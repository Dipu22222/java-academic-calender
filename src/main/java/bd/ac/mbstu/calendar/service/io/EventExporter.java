package bd.ac.mbstu.calendar.service.io;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.io.EventExchange.ExchangeFile;
import bd.ac.mbstu.calendar.util.CsvUtil;
import bd.ac.mbstu.calendar.util.DateUtil;
import bd.ac.mbstu.calendar.util.ValidationException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds export files from a list of events:
 *   CSV  - opens in Excel; can be imported again
 *   JSON - can be imported again
 *   ICS  - Google Calendar / Outlook / phone calendars
 *   PDF  - a printable table (English text only: the PDF library cannot shape Bangla letters)
 */
public class EventExporter {

    public static final List<String> CSV_HEADER = List.of(
            "title", "titleBn", "type", "startDate", "endDate", "startTime", "endTime", "department",
            "year", "semester", "courseCode", "location", "description", "reminderMinutes",
            "classOff", "officeOff");

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter PDF_DATE_SHORT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final Color TEAL = new Color(14, 124, 134);
    private static final Color STRIPE = new Color(240, 246, 248);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, TEAL);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

    private final List<Department> departments;

    public EventExporter(List<Department> departments) {
        this.departments = List.copyOf(departments);
    }

    // ---------------------------------------------------------------
    // CSV and JSON
    // ---------------------------------------------------------------

    public String toCsv(List<Event> events) {
        StringBuilder sb = new StringBuilder(CsvUtil.line(CSV_HEADER)).append("\r\n");
        for (Event e : events) {
            EventExchange x = EventExchangeMapper.toExchange(e, departments);
            sb.append(CsvUtil.line(List.of(
                    CsvUtil.protect(x.title), CsvUtil.protect(x.titleBn), x.type, x.startDate, x.endDate,
                    text(x.startTime), text(x.endTime), text(x.department),
                    number(x.year), number(x.semester),
                    CsvUtil.protect(x.courseCode), CsvUtil.protect(x.location), CsvUtil.protect(x.description),
                    number(x.reminderMinutes), String.valueOf(x.classOff), String.valueOf(x.officeOff))))
              .append("\r\n");
        }
        return sb.toString();
    }

    public String toJson(List<Event> events, Clock clock) {
        ExchangeFile file = new ExchangeFile();
        file.format = "mbstu-smart-calendar";
        file.version = 1;
        file.exportedAt = Instant.now(clock).toString();
        file.events = events.stream().map(e -> EventExchangeMapper.toExchange(e, departments)).toList();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(file);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String number(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ---------------------------------------------------------------
    // ICS (iCalendar, RFC 5545): all-day events use dates, timed events use UTC
    // ---------------------------------------------------------------

    public String toIcs(List<Event> events, Clock clock) {
        String stamp = ICS_UTC.format(Instant.now(clock).atZone(ZoneOffset.UTC));
        StringBuilder out = new StringBuilder();
        foldedLine(out, "BEGIN:VCALENDAR");
        foldedLine(out, "VERSION:2.0");
        foldedLine(out, "PRODID:-//MBSTU//Smart Academic Calendar//EN");
        foldedLine(out, "CALSCALE:GREGORIAN");
        foldedLine(out, "METHOD:PUBLISH");
        foldedLine(out, "X-WR-CALNAME:MBSTU Academic Calendar");

        for (Event e : events) {
            foldedLine(out, "BEGIN:VEVENT");
            foldedLine(out, "UID:event-" + e.getId() + "@smart-calendar.mbstu"); // stable id: re-importing updates, not duplicates
            foldedLine(out, "DTSTAMP:" + stamp);

            if (e.getStartTime() == null) {
                foldedLine(out, "DTSTART;VALUE=DATE:" + ICS_DATE.format(e.getStartDate()));
                foldedLine(out, "DTEND;VALUE=DATE:" + ICS_DATE.format(e.getEndDate().plusDays(1))); // end date is exclusive
            } else {
                LocalDateTime start = LocalDateTime.of(e.getStartDate(), e.getStartTime());
                LocalDateTime end = e.getEndTime() != null
                        ? LocalDateTime.of(e.getEndDate(), e.getEndTime())
                        : LocalDateTime.of(e.getEndDate(), e.getStartTime()).plusHours(1);
                foldedLine(out, "DTSTART:" + utc(start));
                foldedLine(out, "DTEND:" + utc(end));
            }

            foldedLine(out, "SUMMARY:" + escape(e.getTitle()));
            String description = icsDescription(e);
            if (!description.isEmpty()) {
                foldedLine(out, "DESCRIPTION:" + escape(description));
            }
            if (e.getLocation() != null && !e.getLocation().isBlank()) {
                foldedLine(out, "LOCATION:" + escape(e.getLocation()));
            }
            foldedLine(out, "CATEGORIES:" + escape(e.getType().getLabelEn()));

            if (e.getReminderMinutes() != null && e.getReminderMinutes() > 0) {
                foldedLine(out, "BEGIN:VALARM");
                foldedLine(out, "ACTION:DISPLAY");
                foldedLine(out, "DESCRIPTION:Reminder");
                foldedLine(out, "TRIGGER:-PT" + e.getReminderMinutes() + "M");
                foldedLine(out, "END:VALARM");
            }
            foldedLine(out, "END:VEVENT");
        }
        foldedLine(out, "END:VCALENDAR");
        return out.toString();
    }

    private static String utc(LocalDateTime dhakaTime) {
        return ICS_UTC.format(dhakaTime.atZone(DHAKA).withZoneSameInstant(ZoneOffset.UTC));
    }

    private String icsDescription(Event e) {
        List<String> parts = new ArrayList<>();
        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            parts.add(e.getDescription().trim());
        }
        String dept = departmentCode(e);
        if (dept != null) {
            parts.add("Department: " + dept
                    + (e.getYear() != null ? ", year " + e.getYear() : "")
                    + (e.getSemester() != null ? ", semester " + e.getSemester() : ""));
        }
        if (e.isClassOff()) {
            parts.add("Classes are off.");
        }
        if (e.isOfficeOff()) {
            parts.add("The office is closed.");
        }
        return String.join("\n", parts);
    }

    /** Text values need \\ \; \, and \n escaped. */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n");
    }

    /** Lines must be at most 75 BYTES; longer lines continue on the next line after a space. */
    private static void foldedLine(StringBuilder out, String line) {
        int count = 0;
        int i = 0;
        while (i < line.length()) {
            int codePoint = line.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (count + size > 75) {
                out.append("\r\n ");
                count = 1; // the space we just added
            }
            out.appendCodePoint(codePoint);
            count += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }

    // ---------------------------------------------------------------
    // PDF
    // ---------------------------------------------------------------

    public void writePdf(List<Event> events, String title, String subtitle, OutputStream out) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageNumbers());
            document.open();

            document.add(new Paragraph(title, TITLE_FONT));
            Paragraph sub = new Paragraph(subtitle, SMALL_FONT);
            sub.setSpacingAfter(10f);
            document.add(sub);

            if (events.isEmpty()) {
                document.add(new Paragraph("No events in this selection.", BODY_FONT));
            } else {
                document.add(buildTable(events));
            }
            document.close();
        } catch (DocumentException e) {
            throw new ValidationException("Could not create the PDF: " + e.getMessage());
        }
    }

    private PdfPTable buildTable(List<Event> events) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {2.8f, 5f, 2.2f, 1.1f, 1.2f, 1.2f});
        table.setHeaderRows(1);

        for (String header : new String[] {"Date", "Event", "Type", "Dept", "Class off", "Office off"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setBackgroundColor(TEAL);
            cell.setPadding(5);
            table.addCell(cell);
        }

        boolean stripe = false;
        for (Event e : events) {
            Color background = stripe ? STRIPE : Color.WHITE;
            stripe = !stripe;
            addCell(table, dateText(e), background);
            addCell(table, eventText(e), background);
            addCell(table, e.getType().getLabelEn(), background);
            String dept = departmentCode(e);
            addCell(table, dept == null ? "All" : dept, background);
            addCell(table, e.isClassOff() ? "Yes" : "-", background);
            addCell(table, e.isOfficeOff() ? "Yes" : "-", background);
        }
        return table;
    }

    private static void addCell(PdfPTable table, String text, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setBackgroundColor(background);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static String dateText(Event e) {
        if (!e.isMultiDay()) {
            return PDF_DATE.format(e.getStartDate());
        }
        return PDF_DATE_SHORT.format(e.getStartDate()) + " to " + PDF_DATE_SHORT.format(e.getEndDate())
                + " (" + DateUtil.inclusiveDays(e.getStartDate(), e.getEndDate()) + " days)";
    }

    private static String eventText(Event e) {
        StringBuilder sb = new StringBuilder(e.getTitle());
        if (e.getStartTime() != null) {
            sb.append("\n").append(e.getStartTime());
            if (e.getEndTime() != null) {
                sb.append("-").append(e.getEndTime());
            }
        }
        if (e.getLocation() != null && !e.getLocation().isBlank()) {
            sb.append("\n").append(e.getLocation());
        }
        return sb.toString();
    }

    /** Writes "Page N" at the bottom of every page. */
    private static final class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Phrase text = new Phrase("MBSTU Smart Academic Calendar  |  Page " + writer.getPageNumber(), SMALL_FONT);
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, text,
                    (document.right() + document.left()) / 2, document.bottom() - 20, 0);
        }
    }

    // ---------------------------------------------------------------

    private String departmentCode(Event e) {
        if (e.getDepartmentId() == null) {
            return null;
        }
        return departments.stream()
                .filter(d -> d.getId() == e.getDepartmentId())
                .map(Department::getCode)
                .findFirst().orElse(null);
    }
}
