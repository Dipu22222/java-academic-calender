package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Simple date questions about the calendar:
 * "Is this day off?", "When is the next holiday?", "How many days until Eid?"
 */
public class CalendarService {

    private final EventDao eventDao;
    private final WeeklyOffPolicy policy;

    public CalendarService() {
        this(new SqliteEventDao(), WeeklyOffPolicy.load());
    }

    public CalendarService(EventDao eventDao, WeeklyOffPolicy policy) {
        this.eventDao = eventDao;
        this.policy = policy;
    }

    /** True if classes are off: weekly off day OR a class-off holiday/vacation. */
    public boolean isClassOff(LocalDate date) {
        if (policy.isOffDay(date)) {
            return true;
        }
        return eventDao.findByFilter(new EventFilter().from(date).to(date)).stream()
                .anyMatch(e -> e.isClassOff() && e.occursOn(date));
    }

    /** The first holiday that starts on or after the given day. */
    public Optional<Event> nextHoliday(LocalDate from) {
        return eventDao.findByFilter(new EventFilter().from(from).type(EventType.HOLIDAY)).stream()
                .filter(e -> !e.getStartDate().isBefore(from))
                .findFirst();
    }

    /** The next (or current) official event whose title/description contains the keyword. */
    public Optional<Event> nextByKeyword(String keyword, LocalDate from) {
        return eventDao.findByFilter(new EventFilter().from(from).keyword(keyword)).stream()
                .findFirst();
    }

    /** Days from "from" until the event starts (0 if it already started). */
    public long daysUntil(Event event, LocalDate from) {
        return Math.max(0, ChronoUnit.DAYS.between(from, event.getStartDate()));
    }
}
