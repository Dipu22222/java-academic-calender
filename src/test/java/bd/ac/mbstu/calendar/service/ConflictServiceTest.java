package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.service.ConflictService.Conflict;
import bd.ac.mbstu.calendar.service.ConflictService.Kind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the conflict RULES. ConflictService.detect is static and uses no database,
 * so we simply hand it lists of events.
 *
 * Calendar facts used (2026): Thu 12 Nov, Fri 13 Nov, Sat 14 Nov, Sun 15 Nov, Tue 17 Nov.
 * The default weekly off days are Friday and Saturday.
 */
class ConflictServiceTest {

    private static final int ICT = 1;
    private static final int CSE = 2;
    private static final WeeklyOffPolicy POLICY = WeeklyOffPolicy.defaultPolicy();

    private static LocalDate d(int day) {
        return LocalDate.of(2026, 11, day);
    }

    private static Event event(EventType type, long id, LocalDate start, LocalDate end,
                               Integer dept, Integer year, Integer semester) {
        Event e = EventFactory.create(type, type.name() + id, start, end);
        e.setId(id);
        e.setDepartmentId(dept);
        e.setYear(year);
        e.setSemester(semester);
        return e;
    }

    private static Event classTest(long id, LocalDate day, Integer dept, Integer year, Integer semester) {
        return event(EventType.CLASS_TEST, id, day, day, dept, year, semester);
    }

    private static List<Conflict> detect(Event candidate, Event... existing) {
        return ConflictService.detect(candidate, List.of(existing), POLICY);
    }

    @Test
    void twoTestsForTheSameGroupOnTheSameDayClash() {
        List<Conflict> result = detect(classTest(0, d(15), ICT, 2, 2), classTest(1, d(15), ICT, 2, 2));
        assertEquals(1, result.size());
        assertEquals(Kind.TEST_CLASH, result.get(0).kind());
        assertEquals(1, result.get(0).other().getId());
    }

    @Test
    void anotherDepartmentYearOrSemesterIsNotAClash() {
        Event existing = classTest(1, d(15), ICT, 2, 2);
        assertTrue(detect(classTest(0, d(15), CSE, 2, 2), existing).isEmpty(), "other department");
        assertTrue(detect(classTest(0, d(15), ICT, 3, 2), existing).isEmpty(), "other year");
        assertTrue(detect(classTest(0, d(15), ICT, 2, 1), existing).isEmpty(), "other semester");
    }

    @Test
    void aMissingYearOrSemesterMeansAllSoItStillClashes() {
        List<Conflict> result = detect(classTest(0, d(15), ICT, 2, 2), classTest(1, d(15), ICT, null, null));
        assertEquals(1, result.size());
        assertEquals(Kind.TEST_CLASH, result.get(0).kind());
    }

    @Test
    void differentDaysDoNotClash() {
        assertTrue(detect(classTest(0, d(16), ICT, 2, 2), classTest(1, d(15), ICT, 2, 2)).isEmpty());
    }

    @Test
    void aClassTestAndAnExamOnTheSameDayDoNotClash() {
        Event exam = event(EventType.EXAM, 1, d(15), d(15), ICT, 2, 2);
        assertTrue(detect(classTest(0, d(15), ICT, 2, 2), exam).isEmpty());
    }

    @Test
    void anEventNeverClashesWithItself() {
        Event saved = classTest(5, d(15), ICT, 2, 2);
        Event editedCopy = classTest(5, d(15), ICT, 2, 2);
        assertTrue(detect(editedCopy, saved).isEmpty());
    }

    @Test
    void personalEventsAreIgnored() {
        Event personal = classTest(1, d(15), ICT, 2, 2);
        personal.setOwnerUserId(9L);
        assertTrue(detect(classTest(0, d(15), ICT, 2, 2), personal).isEmpty());
    }

    @Test
    void aTestOnAClassOffHolidayIsReported() {
        Event holiday = event(EventType.HOLIDAY, 3, d(17), d(17), null, null, null);
        List<Conflict> result = detect(classTest(0, d(17), ICT, 2, 2), holiday);
        assertEquals(1, result.size());
        assertEquals(Kind.ON_HOLIDAY, result.get(0).kind());
        assertSame(holiday, result.get(0).other());
    }

    @Test
    void aTestOnAWeeklyOffDayIsReported() {
        List<Conflict> result = detect(classTest(0, d(14), ICT, 2, 2));   // Saturday
        assertEquals(1, result.size());
        assertEquals(Kind.ON_WEEKLY_OFF, result.get(0).kind());
        assertNull(result.get(0).other());
        assertTrue(detect(classTest(0, d(15), ICT, 2, 2)).isEmpty(), "Sunday is a normal day");
    }

    @Test
    void aMultiDayExamAcrossTheWeekendGivesOnlyOneWeeklyOffWarning() {
        Event exam = event(EventType.EXAM, 0, d(12), d(14), ICT, 2, 2);   // Thu, Fri, Sat
        List<Conflict> result = detect(exam);
        assertEquals(1, result.size());
        assertEquals(Kind.ON_WEEKLY_OFF, result.get(0).kind());
    }

    @Test
    void severalProblemsAreAllReported() {
        // Saturday 14 Nov, but also another ICT test that day and a holiday that day.
        List<Conflict> result = detect(classTest(0, d(14), ICT, 2, 2),
                classTest(1, d(14), ICT, 2, 2),
                event(EventType.HOLIDAY, 2, d(14), d(14), null, null, null));
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(c -> c.kind() == Kind.TEST_CLASH));
        assertTrue(result.stream().anyMatch(c -> c.kind() == Kind.ON_HOLIDAY));
        assertTrue(result.stream().anyMatch(c -> c.kind() == Kind.ON_WEEKLY_OFF));
    }

    @Test
    void onlyTestsAndExamsAreChecked() {
        Event notice = event(EventType.NOTICE, 0, d(14), d(14), ICT, null, null);   // on a Saturday
        assertTrue(detect(notice, classTest(1, d(14), ICT, 2, 2)).isEmpty());
        Event holiday = event(EventType.HOLIDAY, 0, d(14), d(14), null, null, null);
        assertTrue(detect(holiday).isEmpty());
    }

    @Test
    void incompleteCandidatesAreHandledWithoutCrashing() {
        Event noDates = new Event();
        noDates.setType(EventType.CLASS_TEST);
        assertTrue(detect(noDates).isEmpty());

        Event reversed = classTest(0, d(15), ICT, 2, 2);
        reversed.setEndDate(d(14));
        assertTrue(detect(reversed, classTest(1, d(15), ICT, 2, 2)).isEmpty());
    }

    @Test
    void personalCandidatesSkipTheDatabaseCheckCompletely() {
        Event personal = classTest(0, d(14), ICT, 2, 2);
        personal.setOwnerUserId(4L);
        // The DAO is null on purpose: findConflicts must return before touching it.
        assertFalse(new ConflictService(null, POLICY).findConflicts(personal).iterator().hasNext());
    }

    @Test
    void sameGroupTreatsNullAsMatchingEverything() {
        assertTrue(ConflictService.sameGroup(classTest(1, d(15), ICT, 2, 2), classTest(2, d(15), ICT, 2, 2)));
        assertTrue(ConflictService.sameGroup(classTest(1, d(15), null, null, null), classTest(2, d(15), CSE, 3, 1)));
        assertFalse(ConflictService.sameGroup(classTest(1, d(15), ICT, 2, 2), classTest(2, d(15), CSE, 2, 2)));
    }
}
