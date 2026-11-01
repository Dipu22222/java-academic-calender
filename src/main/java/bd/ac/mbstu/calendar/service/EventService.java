package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Add / edit / delete / search events, with role rules and validation.
 *
 * Role rules (in ONE place, canModify):
 *  - ADMIN can add, edit and delete OFFICIAL events.
 *  - Everyone can add, edit and delete their OWN personal events.
 *  - Nobody can change someone else's personal events.
 */
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventDao eventDao;
    private final ConflictService conflictService;

    public EventService() {
        this(new SqliteEventDao());
    }

    public EventService(EventDao eventDao) {
        this(eventDao, new ConflictService(eventDao, WeeklyOffPolicy.load()));
    }

    public EventService(EventDao eventDao, ConflictService conflictService) {
        this.eventDao = eventDao;
        this.conflictService = conflictService;
    }

    // ---------------------------------------------------------------
    // Create / update / delete
    // ---------------------------------------------------------------

    public Event create(User actor, Event event) {
        Validator.requireNotNull(actor, "User");
        Validator.requireNotNull(event, "Event");

        applyOwnershipRules(actor, event);
        validate(event);
        requireCanModify(actor, event);

        event.setCreatedBy(actor.getId());
        eventDao.insert(event);
        log.info("{} created {} event \"{}\"", actor.getUsername(),
                event.isPersonal() ? "personal" : "official", event.getTitle());
        EventBus.getInstance().publish(EventBus.ChangeType.EVENT_ADDED);
        return event;
    }

    public Event update(User actor, Event event) {
        Validator.requireNotNull(actor, "User");
        Validator.requireNotNull(event, "Event");

        Event existing = eventDao.findById(event.getId())
                .orElseThrow(() -> new ValidationException("This event no longer exists."));
        requireCanModify(actor, existing); // check the SAVED version, not what the UI sent

        // These can never be changed by editing.
        event.setOwnerUserId(existing.getOwnerUserId());
        event.setCreatedBy(existing.getCreatedBy());
        if (!actor.isAdmin()) {
            event.setClassOff(false);
            event.setOfficeOff(false);
        }
        validate(event);

        eventDao.update(event);
        log.info("{} updated event {} (\"{}\")", actor.getUsername(), event.getId(), event.getTitle());
        EventBus.getInstance().publish(EventBus.ChangeType.EVENT_UPDATED);
        return event;
    }

    public void delete(User actor, long eventId) {
        Validator.requireNotNull(actor, "User");

        Event existing = eventDao.findById(eventId)
                .orElseThrow(() -> new ValidationException("This event no longer exists."));
        requireCanModify(actor, existing);

        eventDao.delete(eventId);
        log.info("{} deleted event {} (\"{}\")", actor.getUsername(), eventId, existing.getTitle());
        EventBus.getInstance().publish(EventBus.ChangeType.EVENT_DELETED);
    }

    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    /** Official events plus the viewer's own personal events, matching the filter. */
    public List<Event> find(User viewer, EventFilter filter) {
        Validator.requireNotNull(viewer, "User");
        filter.viewer(viewer.getId());
        return eventDao.findByFilter(filter);
    }

    public Optional<Event> findById(long id) {
        return eventDao.findById(id);
    }

    /** Call this BEFORE create/update to show "are you sure?" warnings. */
    public List<ConflictService.Conflict> previewConflicts(Event event) {
        validate(event);
        return conflictService.findConflicts(event);
    }

    // ---------------------------------------------------------------
    // Rules
    // ---------------------------------------------------------------

    /** The single place that decides who may change an event. */
    public static boolean canModify(User actor, Event event) {
        if (event.isPersonal()) {
            return event.getOwnerUserId() == actor.getId();
        }
        return actor.getRole().canEditOfficialEvents();
    }

    private void requireCanModify(User actor, Event event) {
        if (!canModify(actor, event)) {
            throw new PermissionDeniedException(event.isPersonal()
                    ? "You can only change your own personal events."
                    : "Only the university office (admin) can change official events.");
        }
    }

    /**
     * Non-admins can only create personal events (and personal events never switch
     * classes or offices off). Admins create official events unless an owner is set.
     */
    private void applyOwnershipRules(User actor, Event event) {
        if (!actor.isAdmin()) {
            event.setOwnerUserId(actor.getId());
            event.setClassOff(false);
            event.setOfficeOff(false);
        }
    }

    /** Checks every field. Throws ValidationException with a friendly message. */
    public static void validate(Event e) {
        Validator.requireNotNull(e, "Event");

        String title = Validator.requireNotBlank(e.getTitle(), "Title");
        if (title.length() > 200) {
            throw new ValidationException("Title is too long (maximum 200 characters).");
        }
        e.setTitle(title);

        Validator.requireNotNull(e.getType(), "Event type");
        Validator.requireDateRange(e.getStartDate(), e.getEndDate());
        if (e.getStartTime() == null && e.getEndTime() != null) {
            throw new ValidationException("Please set a start time before an end time.");
        }
        Validator.requireTimeRange(e.getStartTime(), e.getEndTime());
        Validator.requireInRangeOrNull(e.getYear(), 1, 4, "Year");
        Validator.requireInRangeOrNull(e.getSemester(), 1, 2, "Semester");

        if (e.getReminderMinutes() != null && e.getReminderMinutes() <= 0) {
            throw new ValidationException("Reminder must be a positive number of minutes.");
        }
        if (e.getCourseCode() != null && e.getCourseCode().length() > 20) {
            throw new ValidationException("Course code is too long (maximum 20 characters).");
        }
        if (e.getDescription() != null && e.getDescription().length() > 2000) {
            throw new ValidationException("Description is too long (maximum 2000 characters).");
        }

        boolean testOrExam = e.getType() == EventType.CLASS_TEST || e.getType() == EventType.EXAM;
        if (!e.isPersonal() && testOrExam && e.getDepartmentId() == null) {
            throw new ValidationException("Department is required for official class tests and exams.");
        }
    }
}
