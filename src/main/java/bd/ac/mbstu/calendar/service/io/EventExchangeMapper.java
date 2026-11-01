package bd.ac.mbstu.calendar.service.io;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.service.EventFactory;
import bd.ac.mbstu.calendar.service.EventService;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts between Event objects and the plain EventExchange shape used in files.
 * Everything that comes FROM a file is untrusted, so toEvent() checks it carefully
 * and throws a ValidationException with a friendly message when something is wrong.
 */
final class EventExchangeMapper {

    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");

    private EventExchangeMapper() {
    }

    // ---------------------------------------------------------------
    // Event -> file
    // ---------------------------------------------------------------

    static EventExchange toExchange(Event e, List<Department> departments) {
        EventExchange x = new EventExchange();
        x.title = e.getTitle();
        x.titleBn = e.getTitleBn();
        x.type = e.getType().name();
        x.startDate = e.getStartDate().toString();
        x.endDate = e.getEndDate().toString();
        x.startTime = e.getStartTime() == null ? null : e.getStartTime().toString();
        x.endTime = e.getEndTime() == null ? null : e.getEndTime().toString();
        x.department = departments.stream()
                .filter(d -> e.getDepartmentId() != null && d.getId() == e.getDepartmentId())
                .map(Department::getCode)
                .findFirst().orElse(null);
        x.year = e.getYear();
        x.semester = e.getSemester();
        x.courseCode = e.getCourseCode();
        x.location = e.getLocation();
        x.description = e.getDescription();
        // 0 = "no reminder", so a round trip does not bring back the default reminder
        x.reminderMinutes = e.getReminderMinutes() == null ? 0 : e.getReminderMinutes();
        x.classOff = e.isClassOff();
        x.officeOff = e.isOfficeOff();
        return x;
    }

    // ---------------------------------------------------------------
    // file -> Event
    // ---------------------------------------------------------------

    static Event toEvent(EventExchange x, List<Department> departments) {
        String title = Validator.requireNotBlank(x.title, "Title");

        EventType type = EventType.fromString(x.type);
        if (type == null) {
            throw new ValidationException("Unknown event type \"" + (x.type == null ? "" : x.type)
                    + "\". Use one of: " + Arrays.stream(EventType.values())
                    .map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }

        LocalDate start = parseDate(x.startDate, "Start date");
        LocalDate end = isBlank(x.endDate) ? start : parseDate(x.endDate, "End date");

        // The factory gives the type's defaults (holiday = classes and office off, tests = 1 day reminder...)
        Event e = EventFactory.create(type, title, start, end);
        e.setTitleBn(blankToNull(x.titleBn));
        e.setStartTime(parseTime(x.startTime, "Start time"));
        e.setEndTime(parseTime(x.endTime, "End time"));

        if (!isBlank(x.department)) {
            String code = x.department.trim();
            Department d = departments.stream()
                    .filter(dep -> dep.getCode().equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Unknown department \"" + code + "\"."));
            e.setDepartmentId((int) d.getId());
        }
        e.setYear(x.year);
        e.setSemester(x.semester);
        e.setCourseCode(blankToNull(x.courseCode));
        e.setLocation(blankToNull(x.location));
        e.setDescription(blankToNull(x.description));

        if (x.reminderMinutes != null) {
            e.setReminderMinutes(x.reminderMinutes <= 0 ? null : x.reminderMinutes);
        }
        if (x.classOff != null) {
            e.setClassOff(x.classOff);
        }
        if (x.officeOff != null) {
            e.setOfficeOff(x.officeOff);
        }

        EventService.validate(e); // same rules as the event form
        return e;
    }

    // ---------------------------------------------------------------
    // Small parsers (also used by EventImporter for CSV and Excel cells)
    // ---------------------------------------------------------------

    static Integer parseInt(String text, String field) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(field + " must be a whole number (found \"" + text.trim() + "\").");
        }
    }

    static Boolean parseBool(String text, String field) {
        if (isBlank(text)) {
            return null;
        }
        switch (text.trim().toLowerCase()) {
            case "true", "yes", "y", "1":
                return Boolean.TRUE;
            case "false", "no", "n", "0":
                return Boolean.FALSE;
            default:
                throw new ValidationException(field + " must be true or false (found \"" + text.trim() + "\").");
        }
    }

    private static LocalDate parseDate(String text, String field) {
        if (isBlank(text)) {
            throw new ValidationException(field + " is required (format 2026-11-15).");
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException(field + " \"" + text.trim() + "\" must look like 2026-11-15.");
        }
    }

    private static LocalTime parseTime(String text, String field) {
        if (isBlank(text)) {
            return null;
        }
        String t = text.trim();
        if (!TIME.matcher(t).matches()) {
            throw new ValidationException(field + " \"" + t + "\" must look like 10:30 (24-hour clock).");
        }
        if (t.indexOf(':') == 1) {
            t = "0" + t; // 9:05 -> 09:05
        }
        try {
            return LocalTime.parse(t);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(field + " \"" + text.trim() + "\" is not a valid time.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
