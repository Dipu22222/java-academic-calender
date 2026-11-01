package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.EventType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * An event "proposed" by the AI or the offline parser from plain text.
 * It is NOT saved anywhere. The UI shows it in the form and the user approves it.
 * "warnings" lists things the user must check (unclear type, missing department...).
 */
public record ParsedEvent(String title, EventType type, LocalDate startDate, LocalDate endDate,
                          LocalTime startTime, LocalTime endTime, String departmentCode,
                          Integer year, Integer semester, String courseCode,
                          String location, String description, List<String> warnings) {

    public ParsedEvent {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
