package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.EventFactory;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Converts an AI proposal (ParsedEvent) into a real Event object that can fill the event form.
 * Nothing is saved here. Saving always goes through EventService (roles + validation).
 */
public final class AiEventMapper {

    private AiEventMapper() {
    }

    public static Event toEvent(ParsedEvent p, List<Department> departments) {
        Objects.requireNonNull(p, "parsed event");
        Event e = EventFactory.create(p.type(), p.title(), p.startDate(), p.endDate());

        LocalTime start = p.startTime();
        LocalTime end = p.endTime();
        if (start == null || (end != null && !end.isAfter(start))) {
            end = null;
        }
        e.setStartTime(start);
        e.setEndTime(end);

        if (p.departmentCode() != null) {
            departments.stream()
                    .filter(d -> d.getCode().equalsIgnoreCase(p.departmentCode()))
                    .findFirst()
                    .ifPresent(d -> e.setDepartmentId((int) d.getId()));
        }
        e.setYear(inRange(p.year(), 1, 4));
        e.setSemester(inRange(p.semester(), 1, 2));
        e.setCourseCode(p.courseCode());
        e.setLocation(p.location());
        e.setDescription(p.description());
        return e;
    }

    /** AI numbers outside the valid range become "not set" instead of breaking validation. */
    private static Integer inRange(Integer value, int min, int max) {
        return (value != null && value >= min && value <= max) ? value : null;
    }
}
