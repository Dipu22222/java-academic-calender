package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.EventService;
import bd.ac.mbstu.calendar.service.WeeklyOffPolicy;

import java.time.LocalDate;
import java.util.List;

/** Collects the events the AI is allowed to see for one user. */
public final class AiContextBuilder {

    /** How far back and forward the AI can see (also used by the offline assistant). */
    public static final int DAYS_BACK = 60;
    public static final int DAYS_AHEAD = 365;
    /** Limit so the prompt stays small (and cheap). */
    public static final int MAX_EVENTS = 200;

    private AiContextBuilder() {
    }

    /** Official events + the user's own personal events in the window. */
    public static AiContext full(EventService events, User user, LocalDate today) {
        EventFilter filter = new EventFilter()
                .from(today.minusDays(DAYS_BACK))
                .to(today.plusDays(DAYS_AHEAD));
        List<Event> list = events.find(user, filter);
        if (list.size() > MAX_EVENTS) {
            list = list.subList(0, MAX_EVENTS);
        }
        return new AiContext(today, user, list, new DepartmentDao().findAll(), WeeklyOffPolicy.load());
    }

    /** For features that bring their own data (parse a sentence, explain given dates). */
    public static AiContext withoutEvents(User user, LocalDate today) {
        return new AiContext(today, user, List.of(), new DepartmentDao().findAll(), WeeklyOffPolicy.load());
    }
}
