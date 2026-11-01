package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.DateUtil;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Suggests the best free dates for a class test or exam.
 *
 * How it works (no AI needed; the AI layer only explains the result):
 *  1. Try every day in the range.
 *  2. Drop days that have ANY conflict (holiday, weekly off, same-group test).
 *  3. Score the rest: the farther from the group's other tests/exams, the better
 *     (students get breathing space). Maximum score is 14 days.
 */
public class ScheduleSuggestionService {

    /** One suggested day with a score (higher = better) and a plain-English reason. */
    public record DateSuggestion(LocalDate date, int score, String reason) {
    }

    private static final int MAX_SCORE = 14;
    private static final int MAX_RANGE_DAYS = 120;

    private final EventDao eventDao;
    private final WeeklyOffPolicy policy;

    public ScheduleSuggestionService() {
        this(new SqliteEventDao(), WeeklyOffPolicy.load());
    }

    public ScheduleSuggestionService(EventDao eventDao, WeeklyOffPolicy policy) {
        this.eventDao = eventDao;
        this.policy = policy;
    }

    public List<DateSuggestion> suggest(EventType type, Integer departmentId, Integer year,
                                        Integer semester, LocalDate from, LocalDate to, int maxResults) {
        if (type != EventType.CLASS_TEST && type != EventType.EXAM) {
            throw new ValidationException("Suggestions are only available for class tests and exams.");
        }
        Validator.requireNotNull(departmentId, "Department");
        Validator.requireDateRange(from, to);
        if (DateUtil.inclusiveDays(from, to) > MAX_RANGE_DAYS) {
            throw new ValidationException("Please choose a range of at most " + MAX_RANGE_DAYS + " days.");
        }
        if (maxResults < 1) {
            throw new ValidationException("Number of suggestions must be at least 1.");
        }

        // Load official events a little beyond the range, so "distance" checks work at the edges.
        List<Event> existing = eventDao.findByFilter(
                new EventFilter().from(from.minusDays(MAX_SCORE)).to(to.plusDays(MAX_SCORE)));

        List<DateSuggestion> options = new ArrayList<>();
        for (LocalDate day : DateUtil.eachDay(from, to)) {
            Event candidate = EventFactory.create(type, "Candidate", day, day);
            candidate.setDepartmentId(departmentId);
            candidate.setYear(year);
            candidate.setSemester(semester);

            if (!ConflictService.detect(candidate, existing, policy).isEmpty()) {
                continue; // not a free day
            }
            int distance = nearestDistance(day, candidate, existing);
            String reason = distance >= MAX_SCORE
                    ? "Free day. No other test or exam for this group within 2 weeks."
                    : "Free day. The nearest other test/exam for this group is "
                      + distance + (distance == 1 ? " day" : " days") + " away.";
            options.add(new DateSuggestion(day, distance, reason));
        }

        options.sort(Comparator.comparingInt(DateSuggestion::score).reversed()
                .thenComparing(DateSuggestion::date));
        return options.stream().limit(maxResults).toList();
    }

    /** Days between "day" and the closest other test/exam of the same group (capped at 14). */
    private int nearestDistance(LocalDate day, Event candidate, List<Event> existing) {
        int best = MAX_SCORE;
        for (Event other : existing) {
            boolean testOrExam = other.getType() == EventType.CLASS_TEST || other.getType() == EventType.EXAM;
            if (!testOrExam || other.isPersonal() || !ConflictService.sameGroup(candidate, other)) {
                continue;
            }
            int distance;
            if (day.isBefore(other.getStartDate())) {
                distance = (int) java.time.temporal.ChronoUnit.DAYS.between(day, other.getStartDate());
            } else if (day.isAfter(other.getEndDate())) {
                distance = (int) java.time.temporal.ChronoUnit.DAYS.between(other.getEndDate(), day);
            } else {
                distance = 0;
            }
            best = Math.min(best, distance);
        }
        return best;
    }
}
