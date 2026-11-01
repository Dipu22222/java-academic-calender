package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.Validator;

import java.time.LocalDate;

/**
 * FACTORY pattern: one place that builds an Event with sensible defaults
 * for each type, so the UI and the AI never have to repeat these rules.
 */
public final class EventFactory {

    private EventFactory() {
    }

    public static Event create(EventType type, String title, LocalDate start, LocalDate end) {
        Validator.requireNotNull(type, "Event type");

        Event e = new Event();
        e.setTitle(title);
        e.setType(type);
        e.setStartDate(start);
        e.setEndDate(end);

        switch (type) {
            case HOLIDAY -> {
                e.setClassOff(true);
                e.setOfficeOff(true);
            }
            case SEMESTER_BREAK -> e.setClassOff(true);
            case CLASS_TEST, EXAM, ASSIGNMENT_DEADLINE -> e.setReminderMinutes(1440); // 1 day before
            case DEPARTMENT_EVENT -> e.setReminderMinutes(60);                         // 1 hour before
            case NOTICE -> {
                // no defaults
            }
        }
        return e;
    }
}
