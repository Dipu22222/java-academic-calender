package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for EventFactory, EventType, the Event model, EventService.validate and WeeklyOffPolicy. */
class EventRulesTest {

    private static LocalDate d(int m, int day) {
        return LocalDate.of(2026, m, day);
    }

    private static Event notice() {
        return EventFactory.create(EventType.NOTICE, "Open day", d(11, 20), d(11, 20));
    }

    // ---------------- EventFactory (Factory pattern) ----------------

    @Test
    void holidaysSwitchClassesAndOfficeOff() {
        Event e = EventFactory.create(EventType.HOLIDAY, "Victory Day", d(12, 16), d(12, 16));
        assertTrue(e.isClassOff());
        assertTrue(e.isOfficeOff());
        assertNull(e.getReminderMinutes());
    }

    @Test
    void semesterBreaksSwitchOnlyClassesOff() {
        Event e = EventFactory.create(EventType.SEMESTER_BREAK, "Winter Vacation", d(12, 26), d(12, 30));
        assertTrue(e.isClassOff());
        assertFalse(e.isOfficeOff());
    }

    @Test
    void testsExamsAndDeadlinesGetAOneDayReminder() {
        for (EventType type : new EventType[] {
                EventType.CLASS_TEST, EventType.EXAM, EventType.ASSIGNMENT_DEADLINE}) {
            assertEquals(1440, EventFactory.create(type, "x", d(11, 1), d(11, 1)).getReminderMinutes(), type.name());
        }
    }

    @Test
    void departmentEventsGetAOneHourReminderAndNoticesNone() {
        assertEquals(60, EventFactory.create(EventType.DEPARTMENT_EVENT, "Fest", d(11, 1), d(11, 1)).getReminderMinutes());
        Event notice = notice();
        assertNull(notice.getReminderMinutes());
        assertFalse(notice.isClassOff());
    }

    @Test
    void factoryRequiresAType() {
        assertThrows(ValidationException.class, () -> EventFactory.create(null, "x", d(11, 1), d(11, 1)));
    }

    // ---------------- EventType ----------------

    @Test
    void eventTypeIsParsedFromFriendlyText() {
        assertEquals(EventType.CLASS_TEST, EventType.fromString("class test"));
        assertEquals(EventType.CLASS_TEST, EventType.fromString("  CLASS-TEST "));
        assertEquals(EventType.EXAM, EventType.fromString("exam"));
        assertNull(EventType.fromString("party"));
        assertNull(EventType.fromString(null));
    }

    @Test
    void eventTypeHasLabelsInBothLanguages() {
        assertEquals("Class Test", EventType.CLASS_TEST.getLabel("en"));
        assertEquals("ক্লাস টেস্ট", EventType.CLASS_TEST.getLabel("bn"));
        assertEquals("#D32F2F", EventType.HOLIDAY.getColorHex());
    }

    // ---------------- Event model ----------------

    @Test
    void occursOnIncludesFirstAndLastDay() {
        Event e = EventFactory.create(EventType.SEMESTER_BREAK, "Break", d(12, 26), d(12, 30));
        assertTrue(e.occursOn(d(12, 26)));
        assertTrue(e.occursOn(d(12, 28)));
        assertTrue(e.occursOn(d(12, 30)));
        assertFalse(e.occursOn(d(12, 25)));
        assertFalse(e.occursOn(d(12, 31)));
        assertTrue(e.isMultiDay());
    }

    @Test
    void overlapsAndPersonalFlag() {
        Event a = EventFactory.create(EventType.EXAM, "A", d(11, 1), d(11, 5));
        Event b = EventFactory.create(EventType.EXAM, "B", d(11, 5), d(11, 9));
        Event c = EventFactory.create(EventType.EXAM, "C", d(11, 6), d(11, 9));
        assertTrue(a.overlaps(b));
        assertFalse(a.overlaps(c));
        assertFalse(a.isPersonal());
        a.setOwnerUserId(7L);
        assertTrue(a.isPersonal());
    }

    @Test
    void titleFallsBackToEnglishWhenThereIsNoBanglaTitle() {
        Event e = notice();
        assertEquals("Open day", e.getTitle("bn"));
        e.setTitleBn("খোলা দিবস");
        assertEquals("খোলা দিবস", e.getTitle("bn"));
        assertEquals("Open day", e.getTitle("en"));
    }

    // ---------------- EventService.validate ----------------

    @Test
    void aGoodEventPassesAndItsTitleIsTrimmed() {
        Event e = notice();
        e.setTitle("  Open day  ");
        assertDoesNotThrow(() -> EventService.validate(e));
        assertEquals("Open day", e.getTitle());
    }

    @Test
    void titleIsRequiredAndLimited() {
        Event blank = notice();
        blank.setTitle("   ");
        assertThrows(ValidationException.class, () -> EventService.validate(blank));

        Event tooLong = notice();
        tooLong.setTitle("A".repeat(201));
        assertThrows(ValidationException.class, () -> EventService.validate(tooLong));
    }

    @Test
    void endDateCannotBeBeforeStartDate() {
        Event e = notice();
        e.setEndDate(d(11, 19));
        assertThrows(ValidationException.class, () -> EventService.validate(e));
    }

    @Test
    void timesMustBeConsistent() {
        Event endOnly = notice();
        endOnly.setEndTime(LocalTime.of(11, 0));
        assertThrows(ValidationException.class, () -> EventService.validate(endOnly), "end time without start time");

        Event backwards = notice();
        backwards.setStartTime(LocalTime.of(11, 0));
        backwards.setEndTime(LocalTime.of(10, 0));
        assertThrows(ValidationException.class, () -> EventService.validate(backwards));
    }

    @Test
    void yearSemesterAndReminderMustBeInRange() {
        Event year = notice();
        year.setYear(5);
        assertThrows(ValidationException.class, () -> EventService.validate(year));

        Event semester = notice();
        semester.setSemester(3);
        assertThrows(ValidationException.class, () -> EventService.validate(semester));

        Event reminder = notice();
        reminder.setReminderMinutes(0);
        assertThrows(ValidationException.class, () -> EventService.validate(reminder));
    }

    @Test
    void officialTestsNeedADepartmentButPersonalOnesDoNot() {
        Event official = EventFactory.create(EventType.CLASS_TEST, "CT", d(11, 15), d(11, 15));
        assertThrows(ValidationException.class, () -> EventService.validate(official));

        Event personal = EventFactory.create(EventType.CLASS_TEST, "My CT", d(11, 15), d(11, 15));
        personal.setOwnerUserId(7L);
        assertDoesNotThrow(() -> EventService.validate(personal));
    }

    // ---------------- WeeklyOffPolicy ----------------

    @Test
    void defaultPolicyIsFridayAndSaturday() {
        WeeklyOffPolicy policy = WeeklyOffPolicy.defaultPolicy();
        assertTrue(policy.isOffDay(d(11, 13)), "Friday");
        assertTrue(policy.isOffDay(d(11, 14)), "Saturday");
        assertFalse(policy.isOffDay(d(11, 15)), "Sunday");
    }

    @Test
    void policyCanBeParsedFromTheSetting() {
        WeeklyOffPolicy fridayOnly = WeeklyOffPolicy.parse("friday");
        assertTrue(fridayOnly.isOffDay(d(11, 13)));
        assertFalse(fridayOnly.isOffDay(d(11, 14)));

        WeeklyOffPolicy sundayMonday = WeeklyOffPolicy.parse("sunday, monday");
        assertTrue(sundayMonday.isOffDay(d(11, 15)));
        assertTrue(sundayMonday.isOffDay(d(11, 16)));
        assertFalse(sundayMonday.isOffDay(d(11, 13)));
    }

    @Test
    void garbageOrMissingSettingFallsBackToTheDefault() {
        assertTrue(WeeklyOffPolicy.parse("nonsense").isOffDay(d(11, 14)));
        assertTrue(WeeklyOffPolicy.parse(null).isOffDay(d(11, 13)));
        assertTrue(WeeklyOffPolicy.parse("").isOffDay(d(11, 14)));
    }
}
