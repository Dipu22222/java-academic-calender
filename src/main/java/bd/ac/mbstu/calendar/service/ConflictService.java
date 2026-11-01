package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.DateUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds problems with a class test or exam BEFORE it is saved.
 * These are warnings: the admin can still choose to save anyway.
 *
 * Checks:
 *  1. TEST_CLASH     - another test/exam of the same type for the same department,
 *                      year and semester on an overlapping day
 *  2. ON_HOLIDAY     - the date falls on a class-off holiday or vacation
 *  3. ON_WEEKLY_OFF  - the date is a weekly off day (default Friday/Saturday)
 */
public class ConflictService {

    public enum Kind { TEST_CLASH, ON_HOLIDAY, ON_WEEKLY_OFF }

    /** One warning. "other" is the clashing event (null for weekly-off warnings). */
    public record Conflict(Kind kind, String message, Event other) {
    }

    private final EventDao eventDao;
    private final WeeklyOffPolicy policy;

    public ConflictService() {
        this(new SqliteEventDao(), WeeklyOffPolicy.load());
    }

    public ConflictService(EventDao eventDao, WeeklyOffPolicy policy) {
        this.eventDao = eventDao;
        this.policy = policy;
    }

    /** Loads the official events around the candidate's dates and checks them. */
    public List<Conflict> findConflicts(Event candidate) {
        if (candidate.isPersonal()) {
            return List.of(); // personal events never clash with university events
        }
        List<Event> existing = eventDao.findByFilter(
                new EventFilter().from(candidate.getStartDate()).to(candidate.getEndDate()));
        return detect(candidate, existing, policy);
    }

    /**
     * The actual rules. It is static and touches no database, so it is easy to unit test.
     */
    public static List<Conflict> detect(Event candidate, List<Event> existing, WeeklyOffPolicy policy) {
        List<Conflict> result = new ArrayList<>();

        if (candidate.getType() == null || candidate.getStartDate() == null
                || candidate.getEndDate() == null
                || candidate.getEndDate().isBefore(candidate.getStartDate())) {
            return result;
        }
        boolean isTestOrExam = candidate.getType() == EventType.CLASS_TEST
                || candidate.getType() == EventType.EXAM;
        if (!isTestOrExam) {
            return result; // only tests and exams are checked
        }

        for (Event other : existing) {
            boolean sameEvent = candidate.getId() != 0 && other.getId() == candidate.getId();
            if (sameEvent || other.isPersonal() || !candidate.overlaps(other)) {
                continue;
            }

            if (other.getType() == candidate.getType() && sameGroup(candidate, other)) {
                result.add(new Conflict(Kind.TEST_CLASH,
                        candidate.getType().getLabelEn() + " clash: \"" + other.getTitle()
                                + "\" is already scheduled on " + other.getStartDate()
                                + " for the same department, year and semester.",
                        other));
            }

            if (other.isClassOff() && matches(candidate.getDepartmentId(), other.getDepartmentId())) {
                result.add(new Conflict(Kind.ON_HOLIDAY,
                        "Classes are off on these dates: \"" + other.getTitle() + "\" ("
                                + other.getStartDate() + " to " + other.getEndDate() + ").",
                        other));
            }
        }

        for (LocalDate day : DateUtil.eachDay(candidate.getStartDate(), candidate.getEndDate())) {
            if (policy.isOffDay(day)) {
                result.add(new Conflict(Kind.ON_WEEKLY_OFF,
                        day + " is a weekly off day (" + day.getDayOfWeek() + ").", null));
                break; // one warning is enough
            }
        }
        return result;
    }

    /** Same department, year and semester? A missing value (null) means "all", so it matches. */
    static boolean sameGroup(Event a, Event b) {
        return matches(a.getDepartmentId(), b.getDepartmentId())
                && matches(a.getYear(), b.getYear())
                && matches(a.getSemester(), b.getSemester());
    }

    private static boolean matches(Integer a, Integer b) {
        return a == null || b == null || a.equals(b);
    }
}
