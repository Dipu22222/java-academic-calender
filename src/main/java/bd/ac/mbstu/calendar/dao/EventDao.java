package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** What we can do with events in the database (the "contract"). */
public interface EventDao {

    /** Saves a new event, sets its id, and returns it. */
    Event insert(Event event);

    void update(Event event);

    void delete(long id);

    Optional<Event> findById(long id);

    /** Every event in the database (official + personal). Used for export and admin. */
    List<Event> findAll();

    /** Events matching the filter. Official events always; personal ones only for the viewer. */
    List<Event> findByFilter(EventFilter filter);

    /** Used by the seed loader so it never inserts the same holiday twice. */
    boolean existsByTitleAndStart(String title, LocalDate startDate);

    long count();
}
