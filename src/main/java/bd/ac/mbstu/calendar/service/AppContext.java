package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.ai.AiAssistant;

import java.util.List;
import java.util.Optional;

/**
 * Holds the logged-in user and the services the screens need.
 * Created once after login and passed to every controller,
 * so controllers never create services themselves.
 */
public class AppContext {

    private final User user;
    private final EventService events;
    private final AiAssistant ai;
    private final List<Department> departments;
    private final WeeklyOffPolicy policy;

    public AppContext(User user, EventService events, AiAssistant ai,
                      List<Department> departments, WeeklyOffPolicy policy) {
        this.user = user;
        this.events = events;
        this.ai = ai;
        this.departments = List.copyOf(departments);
        this.policy = policy;
    }

    public User getUser() { return user; }
    public EventService getEvents() { return events; }
    public AiAssistant getAi() { return ai; }
    public List<Department> getDepartments() { return departments; }
    public WeeklyOffPolicy getPolicy() { return policy; }

    public boolean isAdmin() {
        return user.isAdmin();
    }

    /** Can the logged-in user edit or delete this event? (The rule lives in EventService.) */
    public boolean canModify(Event event) {
        return EventService.canModify(user, event);
    }

    /** Accepts Integer or Long ids; null gives an empty result. */
    public Optional<Department> findDepartment(Number id) {
        if (id == null) {
            return Optional.empty();
        }
        return departments.stream().filter(d -> d.getId() == id.longValue()).findFirst();
    }

    /** "ICT" for an id, or null for null or unknown. */
    public String departmentCode(Integer id) {
        return findDepartment(id).map(Department::getCode).orElse(null);
    }
}
