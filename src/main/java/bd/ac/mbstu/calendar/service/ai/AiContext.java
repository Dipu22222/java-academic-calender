package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.WeeklyOffPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Everything the AI is allowed to know for one request:
 * today's date, who is asking, the relevant events, and the departments.
 * (A record is an immutable data holder with automatic getters.)
 */
public record AiContext(LocalDate today, User user, List<Event> events,
                        List<Department> departments, WeeklyOffPolicy policy) {

    public AiContext {
        events = List.copyOf(events);
        departments = List.copyOf(departments);
    }

    /** "ICT" for department id 1, "ALL" when the event is university-wide (null). */
    public String departmentCode(Integer id) {
        if (id == null) {
            return "ALL";
        }
        return departments.stream()
                .filter(d -> d.getId() == id)
                .map(Department::getCode)
                .findFirst()
                .orElse("DEPT" + id);
    }

    public Optional<Department> departmentById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return departments.stream().filter(d -> d.getId() == id).findFirst();
    }
}
