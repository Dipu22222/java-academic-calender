package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.ReminderLogDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Checks once a minute (in a background thread) whether any event needs a reminder.
 * It does NOT draw the pop-up itself: it calls the "onReminder" function you give it.
 *
 * Event time = start date + start time (09:00 if the event has no time).
 * A reminder is due when:   now >= eventStart - reminderMinutes   AND   now < eventStart
 * Each reminder is shown once per user (stored in the reminder_log table).
 */
public class ReminderService {

    /** One reminder that is due now. */
    public record Reminder(Event event, LocalDateTime eventStart, int minutesBefore) {

        /** Example: Reminder: "ICT-2207 Class Test" starts in 1 day (2026-11-15 10:00). */
        public String message() {
            return "Reminder: \"" + event.getTitle() + "\" starts in "
                    + describeMinutes(minutesBefore) + " (" + eventStart.toLocalDate()
                    + " " + eventStart.toLocalTime() + ").";
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    private static final int LOOK_AHEAD_DAYS = 8;

    private final EventDao eventDao;
    private final ReminderLogDao logDao;
    private final Clock clock;
    private ScheduledExecutorService scheduler;

    public ReminderService() {
        this(new SqliteEventDao(), new ReminderLogDao(), Clock.systemDefaultZone());
    }

    /** The Clock parameter lets tests pretend it is any date and time. */
    public ReminderService(EventDao eventDao, ReminderLogDao logDao, Clock clock) {
        this.eventDao = eventDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    /** Which reminders are due right now for this user? (Marks them as shown.) */
    public List<Reminder> checkNow(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // Only events relevant to this user: their department/year/semester + their personal events
        EventFilter filter = new EventFilter()
                .from(today).to(today.plusDays(LOOK_AHEAD_DAYS)).viewer(user.getId());
        if (user.getDepartmentId() != null) {
            filter.department(user.getDepartmentId().intValue());
        }
        filter.year(user.getYear()).semester(user.getSemester());

        List<Reminder> due = new ArrayList<>();
        for (Event e : eventDao.findByFilter(filter)) {
            Integer minutes = e.getReminderMinutes();
            if (minutes == null || minutes <= 0) {
                continue;
            }
            LocalTime time = e.getStartTime() != null ? e.getStartTime() : DEFAULT_START_TIME;
            LocalDateTime start = LocalDateTime.of(e.getStartDate(), time);
            LocalDateTime remindAt = start.minusMinutes(minutes);

            boolean isDue = !now.isBefore(remindAt) && now.isBefore(start);
            if (isDue && !logDao.wasShown(e.getId(), user.getId(), minutes)) {
                logDao.markShown(e.getId(), user.getId(), minutes);
                due.add(new Reminder(e, start, minutes));
            }
        }
        return due;
    }

    /** Starts checking every minute. Safe to call again (it restarts). */
    public synchronized void start(User user, Consumer<Reminder> onReminder) {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "reminder-checker");
            t.setDaemon(true); // does not stop the app from closing
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkNow(user).forEach(onReminder);
            } catch (Exception e) {
                // If we let an exception escape, the scheduler silently stops forever.
                log.warn("Reminder check failed: {}", e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);
        log.info("Reminder service started for {}", user.getUsername());
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** 1440 -> "1 day", 60 -> "1 hour", 45 -> "45 minutes". */
    static String describeMinutes(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days + (days == 1 ? " day" : " days");
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + " minutes";
    }
}
