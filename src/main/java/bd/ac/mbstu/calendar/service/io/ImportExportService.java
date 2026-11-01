package bd.ac.mbstu.calendar.service.io;

import bd.ac.mbstu.calendar.dao.CalendarMonthDao;
import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.CalendarMonth;
import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.EventBus;
import bd.ac.mbstu.calendar.service.EventService;
import bd.ac.mbstu.calendar.service.PermissionDeniedException;
import bd.ac.mbstu.calendar.service.io.EventImporter.Candidate;
import bd.ac.mbstu.calendar.service.io.EventImporter.ImportPreview;
import bd.ac.mbstu.calendar.util.CsvUtil;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Import and export for the ADMIN.
 *
 *  Import = two steps so the admin can check first:
 *     preview(file)   reads the file and lists valid events and problems (saves nothing)
 *     commit(preview) saves the valid events in ONE transaction (all or nothing)
 *  Events already in the calendar (same title and start date) are skipped, so importing
 *  the same file twice does not create duplicates.
 */
public class ImportExportService {

    public enum ExportFormat {
        CSV("csv", "CSV (Excel)"),
        JSON("json", "JSON"),
        ICS("ics", "ICS (Google Calendar)"),
        PDF("pdf", "PDF");

        private final String extension;
        private final String label;

        ExportFormat(String extension, String label) {
            this.extension = extension;
            this.label = label;
        }

        public String extension() {
            return extension;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record ImportSummary(int added, int duplicates, List<String> failures) {
    }

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    private final EventDao eventDao;
    private final DepartmentDao departmentDao;
    private final CalendarMonthDao monthDao;
    private final Clock clock;

    public ImportExportService() {
        this(new SqliteEventDao(), new DepartmentDao(), new CalendarMonthDao(), Clock.systemDefaultZone());
    }

    public ImportExportService(EventDao eventDao, DepartmentDao departmentDao,
                               CalendarMonthDao monthDao, Clock clock) {
        this.eventDao = eventDao;
        this.departmentDao = departmentDao;
        this.monthDao = monthDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------

    public ImportPreview preview(User actor, Path file) {
        requireAdmin(actor, "import");
        Validator.requireNotNull(file, "File");
        try {
            return new EventImporter(departmentDao.findAll()).parse(file);
        } catch (IOException e) {
            throw new ValidationException("Could not read the file: " + e.getMessage());
        }
    }

    public ImportSummary commit(User actor, ImportPreview preview) {
        requireAdmin(actor, "import");
        Validator.requireNotNull(preview, "Preview");

        Connection connection = DatabaseManager.getInstance().getConnection();
        int added = 0;
        int duplicates = 0;
        List<String> failures = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();

        try {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);   // start a transaction: nothing is final until commit()
            try {
                for (Candidate candidate : preview.candidates()) {
                    Event e = candidate.event();
                    String key = e.getTitle() + "|" + e.getStartDate();
                    if (!seenInFile.add(key) || eventDao.existsByTitleAndStart(e.getTitle(), e.getStartDate())) {
                        duplicates++;
                        continue;
                    }
                    try {
                        EventService.validate(e);
                        e.setOwnerUserId(null);            // imported events are official
                        e.setCreatedBy(actor.getId());
                        eventDao.insert(e);
                        added++;
                    } catch (ValidationException ex) {
                        failures.add("Row " + candidate.row() + ": " + ex.getMessage());
                    }
                }
                for (CalendarMonth month : preview.months()) {
                    monthDao.save(month);
                }
                connection.commit();
            } catch (RuntimeException ex) {
                connection.rollback();                      // something broke: undo everything
                throw ex;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("The import could not be saved", ex);
        }

        log.info("{} imported: {} added, {} duplicates, {} failed",
                actor.getUsername(), added, duplicates, failures.size());
        EventBus.getInstance().publish(EventBus.ChangeType.DATA_IMPORTED);
        return new ImportSummary(added, duplicates, failures);
    }

    /** A ready-made CSV file the admin can fill in (shows every column with two example rows). */
    public static String templateCsv() {
        return CsvUtil.line(EventExporter.CSV_HEADER) + "\r\n"
                + CsvUtil.line(List.of("ICT-2207 Class Test", "", "CLASS_TEST", "2027-02-15", "2027-02-15",
                        "10:00", "11:00", "ICT", "2", "2", "ICT-2207", "Room 301", "Chapters 1-3",
                        "1440", "false", "false")) + "\r\n"
                + CsvUtil.line(List.of("Independence Day", "মহান স্বাধীনতা ও জাতীয় দিবস", "HOLIDAY",
                        "2027-03-26", "2027-03-26", "", "", "", "", "", "", "", "",
                        "0", "true", "true")) + "\r\n";
    }

    // ---------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------

    /**
     * Writes the OFFICIAL events (not personal ones) between from and to.
     * departmentId == null means every department. University-wide events (holidays) are always included.
     *
     * @return how many events were written
     */
    public int export(User actor, ExportFormat format, Path file, Integer departmentId,
                      LocalDate from, LocalDate to, String title) {
        requireAdmin(actor, "export");
        Validator.requireNotNull(format, "Format");
        Validator.requireNotNull(file, "File");
        Validator.requireDateRange(from, to);
        String heading = Validator.requireNotBlank(title, "Title");

        List<Event> events = eventDao.findByFilter(
                new EventFilter().from(from).to(to).department(departmentId));
        List<Department> departments = departmentDao.findAll();
        EventExporter exporter = new EventExporter(departments);

        String scope = departmentId == null ? "all departments"
                : departments.stream().filter(d -> d.getId() == departmentId)
                        .map(Department::getCode).findFirst().orElse("selected department");
        String subtitle = "Official events " + from + " to " + to + " | " + scope
                + " | generated " + LocalDate.now(clock);

        try {
            switch (format) {
                case CSV -> Files.writeString(file, "\uFEFF" + exporter.toCsv(events), StandardCharsets.UTF_8);
                case JSON -> Files.writeString(file, exporter.toJson(events, clock), StandardCharsets.UTF_8);
                case ICS -> Files.writeString(file, exporter.toIcs(events, clock), StandardCharsets.UTF_8);
                case PDF -> {
                    try (OutputStream out = Files.newOutputStream(file)) {
                        exporter.writePdf(events, heading, subtitle, out);
                    }
                }
            }
        } catch (IOException e) {
            throw new ValidationException("Could not write the file: " + e.getMessage());
        }
        log.info("{} exported {} events as {} to {}", actor.getUsername(), events.size(), format, file);
        return events.size();
    }

    private static void requireAdmin(User actor, String action) {
        if (actor == null || !actor.isAdmin()) {
            throw new PermissionDeniedException("Only an admin can " + action + " the calendar.");
        }
    }
}
