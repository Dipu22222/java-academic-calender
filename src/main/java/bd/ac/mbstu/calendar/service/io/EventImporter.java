package bd.ac.mbstu.calendar.service.io;

import bd.ac.mbstu.calendar.model.CalendarMonth;
import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.util.CsvUtil;
import bd.ac.mbstu.calendar.util.ValidationException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a calendar file and turns it into a PREVIEW: the valid events, the problems
 * (with row numbers) and, for seed-style JSON, the Bangla/Hijri month names.
 * Nothing is saved here. ImportExportService.commit() saves after the admin approves.
 *
 * Safety limits: files up to 5 MB and 2000 rows. Row numbers ignore empty lines.
 */
public class EventImporter {

    /** One valid event and the row it came from. */
    public record Candidate(int row, Event event) {
    }

    public record ImportPreview(List<Candidate> candidates, List<String> errors, List<CalendarMonth> months) {
        public ImportPreview {
            candidates = List.copyOf(candidates);
            errors = List.copyOf(errors);
            months = List.copyOf(months);
        }
    }

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private static final List<String> REQUIRED_COLUMNS = List.of("title", "type", "startdate");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final List<Department> departments;
    private final Gson gson = new Gson();

    public EventImporter(List<Department> departments) {
        this.departments = List.copyOf(departments);
    }

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------

    /** Chooses the reader from the file extension (.csv, .json or .xlsx). */
    public ImportPreview parse(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new ValidationException("The file is larger than 5 MB.");
        }
        try {
            if (name.endsWith(".csv")) {
                return parseCsv(Files.readString(file, StandardCharsets.UTF_8));
            }
            if (name.endsWith(".json")) {
                return parseJson(Files.readString(file, StandardCharsets.UTF_8));
            }
            if (name.endsWith(".xlsx")) {
                try (InputStream in = Files.newInputStream(file)) {
                    return parseExcel(in);
                }
            }
        } catch (MalformedInputException e) {
            throw new ValidationException("The file must be saved as UTF-8 text.");
        }
        throw new ValidationException("Unsupported file type. Please choose a .csv, .json or .xlsx file.");
    }

    // ---------------------------------------------------------------
    // CSV and Excel (both become a table of text, then share one reader)
    // ---------------------------------------------------------------

    public ImportPreview parseCsv(String text) {
        return fromTable(CsvUtil.parse(text));
    }

    public ImportPreview parseExcel(InputStream in) {
        return fromTable(readFirstSheet(in));
    }

    private ImportPreview fromTable(List<List<String>> table) {
        if (table.isEmpty()) {
            throw new ValidationException("The file is empty.");
        }
        Map<String, Integer> index = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            index.putIfAbsent(normalize(header.get(i)), i);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!index.containsKey(required)) {
                throw new ValidationException(
                        "The first row must be a header with at least the columns: title, type, startDate.");
            }
        }
        if (table.size() - 1 > MAX_ROWS) {
            throw new ValidationException("The file has more than " + MAX_ROWS + " rows. Please split it.");
        }

        List<Candidate> ok = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            int rowNumber = r + 1;
            try {
                EventExchange x = new EventExchange();
                x.title = cell(cells, index, "title");
                x.titleBn = cell(cells, index, "titlebn");
                x.type = cell(cells, index, "type");
                x.startDate = cell(cells, index, "startdate");
                x.endDate = cell(cells, index, "enddate");
                x.startTime = cell(cells, index, "starttime");
                x.endTime = cell(cells, index, "endtime");
                x.department = cell(cells, index, "department");
                x.year = EventExchangeMapper.parseInt(cell(cells, index, "year"), "Year");
                x.semester = EventExchangeMapper.parseInt(cell(cells, index, "semester"), "Semester");
                x.courseCode = cell(cells, index, "coursecode");
                x.location = cell(cells, index, "location");
                x.description = cell(cells, index, "description");
                x.reminderMinutes = EventExchangeMapper.parseInt(
                        cell(cells, index, "reminderminutes"), "Reminder minutes");
                x.classOff = EventExchangeMapper.parseBool(cell(cells, index, "classoff"), "Class off");
                x.officeOff = EventExchangeMapper.parseBool(cell(cells, index, "officeoff"), "Office off");
                ok.add(new Candidate(rowNumber, EventExchangeMapper.toEvent(x, departments)));
            } catch (ValidationException ex) {
                errors.add("Row " + rowNumber + ": " + ex.getMessage());
            }
        }
        return new ImportPreview(ok, errors, List.of());
    }

    /** "Start Date", "start_date" and "startDate" all become "startdate". */
    private static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String cell(List<String> cells, Map<String, Integer> index, String column) {
        Integer i = index.get(column);
        if (i == null || i >= cells.size()) {
            return null;
        }
        return CsvUtil.unprotect(cells.get(i)).trim();
    }

    // ---------------------------------------------------------------
    // Excel (.xlsx): first sheet, first row = header
    // ---------------------------------------------------------------

    private List<List<String>> readFirstSheet(InputStream in) {
        DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
        try (Workbook workbook = new XSSFWorkbook(in)) {
            List<List<String>> table = new ArrayList<>();
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                return table;
            }
            int columns = headerRow.getLastCellNum();
            List<String> headerNames = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                headerNames.add(normalize(formatter.formatCellValue(headerRow.getCell(c))));
            }

            int last = Math.min(sheet.getLastRowNum(), MAX_ROWS + 1); // reading stops here; fromTable reports "too many rows"
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < columns; c++) {
                    String text = cellText(row == null ? null : row.getCell(c), headerNames.get(c), formatter);
                    if (!text.isBlank()) {
                        any = true;
                    }
                    cells.add(text);
                }
                if (any) {
                    table.add(cells);
                }
            }
            return table;
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("This is not a readable .xlsx file.");
        }
    }

    /** Dates typed as real Excel dates become 2026-11-15, times become 10:00. */
    private static String cellText(Cell cell, String header, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getDateCellValue().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            boolean isTimeColumn = "starttime".equals(header) || "endtime".equals(header);
            return isTimeColumn ? dt.format(HH_MM) : dt.toLocalDate().toString();
        }
        return formatter.formatCellValue(cell);
    }

    // ---------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------

    /**
     * Accepts three shapes:
     *   [ {event}, {event} ]               a plain list
     *   { "events": [ {event}, ... ] }     what this app exports
     *   { "holidays": [ ... ] }            the seed format of holidays_2026.json
     */
    public ImportPreview parseJson(String text) {
        String clean = text == null ? "" : text;
        if (clean.startsWith("\uFEFF")) {
            clean = clean.substring(1);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(clean);
        } catch (JsonParseException ex) {
            throw new ValidationException("The file is not valid JSON. Check the commas, quotes and brackets.");
        }
        if (root.isJsonArray()) {
            return fromEventList(root.getAsJsonArray());
        }
        if (!root.isJsonObject()) {
            throw new ValidationException("The JSON file must contain a list of events.");
        }
        JsonObject object = root.getAsJsonObject();
        if (object.get("events") != null && object.get("events").isJsonArray()) {
            return fromEventList(object.getAsJsonArray("events"));
        }
        if (object.get("holidays") != null && object.get("holidays").isJsonArray()) {
            return fromSeedFormat(object);
        }
        throw new ValidationException("Unrecognised JSON. Expected an \"events\" list or a \"holidays\" list.");
    }

    private ImportPreview fromEventList(JsonArray items) {
        if (items.size() > MAX_ROWS) {
            throw new ValidationException("The file has more than " + MAX_ROWS + " events. Please split it.");
        }
        List<Candidate> ok = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int row = i + 1;
            JsonElement item = items.get(i);
            if (!item.isJsonObject()) {
                errors.add("Item " + row + ": must be an object like { \"title\": ..., \"type\": ... }.");
                continue;
            }
            try {
                EventExchange x = gson.fromJson(item, EventExchange.class);
                ok.add(new Candidate(row, EventExchangeMapper.toEvent(x, departments)));
            } catch (JsonParseException ex) {
                errors.add("Item " + row + ": a value has the wrong type "
                        + "(year, semester and reminderMinutes must be numbers).");
            } catch (ValidationException ex) {
                errors.add("Item " + row + ": " + ex.getMessage());
            }
        }
        return new ImportPreview(ok, errors, List.of());
    }

    private ImportPreview fromSeedFormat(JsonObject root) {
        JsonArray holidays = root.getAsJsonArray("holidays");
        if (holidays.size() > MAX_ROWS) {
            throw new ValidationException("The file has more than " + MAX_ROWS + " holidays. Please split it.");
        }
        List<Candidate> ok = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < holidays.size(); i++) {
            int row = i + 1;
            JsonElement item = holidays.get(i);
            if (!item.isJsonObject()) {
                errors.add("Holiday " + row + ": must be an object.");
                continue;
            }
            try {
                ok.add(new Candidate(row, seedHoliday(item.getAsJsonObject())));
            } catch (ValidationException ex) {
                errors.add("Holiday " + row + ": " + ex.getMessage());
            }
        }
        return new ImportPreview(ok, errors, readMonths(root));
    }

    private Event seedHoliday(JsonObject h) {
        JsonObject classOff = object(h, "classOff");
        if (classOff == null) {
            throw new ValidationException("\"classOff\" with start and end dates is required.");
        }
        JsonObject officeOff = object(h, "officeOff");
        String key = text(h, "key");

        EventExchange x = new EventExchange();
        x.title = text(h, "titleEn");
        x.titleBn = text(h, "titleBn");
        x.type = (key != null && key.endsWith("vacation")) ? "SEMESTER_BREAK" : "HOLIDAY";
        x.startDate = text(classOff, "start");
        x.endDate = text(classOff, "end");
        x.classOff = true;
        x.officeOff = officeOff != null;
        x.description = "Class off: " + describe(classOff)
                + (officeOff != null ? ". Office off: " + describe(officeOff) + "." : ". Office remains open.");
        return EventExchangeMapper.toEvent(x, departments);
    }

    private static String describe(JsonObject period) {
        String start = text(period, "start");
        String end = text(period, "end");
        String days = text(period, "days");
        String range = (start == null || start.equals(end)) ? String.valueOf(start) : start + " to " + end;
        return days == null ? range : range + " (" + days + ("1".equals(days) ? " day)" : " days)");
    }

    private List<CalendarMonth> readMonths(JsonObject root) {
        List<CalendarMonth> list = new ArrayList<>();
        String yearText = text(root, "year");
        JsonElement months = root.get("months");
        if (yearText == null || months == null || !months.isJsonArray()) {
            return list;
        }
        int year;
        try {
            year = Integer.parseInt(yearText.trim());
        } catch (NumberFormatException e) {
            return list;
        }
        for (JsonElement el : months.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject m = el.getAsJsonObject();
            String monthText = text(m, "month");
            String bangla = text(m, "bangla");
            String hijri = text(m, "hijri");
            if (monthText == null || bangla == null || hijri == null) {
                continue;
            }
            try {
                int month = Integer.parseInt(monthText.trim());
                if (month >= 1 && month <= 12) {
                    list.add(new CalendarMonth(year, month, bangla, hijri));
                }
            } catch (NumberFormatException ignored) {
                // skip this month entry
            }
        }
        return list;
    }

    private static String text(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }
}
