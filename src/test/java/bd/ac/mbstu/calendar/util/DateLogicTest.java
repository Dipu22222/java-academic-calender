package bd.ac.mbstu.calendar.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for DateUtil, BanglaDate, HijriDate and Validator. No database needed. */
class DateLogicTest {

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    // ---------------- DateUtil ----------------

    @Test
    void eachDayIncludesBothEnds() {
        List<LocalDate> days = DateUtil.eachDay(d(2026, 10, 30), d(2026, 11, 2));
        assertEquals(4, days.size());
        assertEquals(d(2026, 10, 30), days.get(0));
        assertEquals(d(2026, 11, 2), days.get(3));
    }

    @Test
    void eachDayRejectsAReversedRange() {
        assertThrows(ValidationException.class, () -> DateUtil.eachDay(d(2026, 11, 2), d(2026, 11, 1)));
    }

    @Test
    void inclusiveDaysCountsBothEnds() {
        assertEquals(1, DateUtil.inclusiveDays(d(2026, 3, 26), d(2026, 3, 26)));
        assertEquals(5, DateUtil.inclusiveDays(d(2026, 12, 26), d(2026, 12, 30)));
        assertEquals(14, DateUtil.inclusiveDays(d(2026, 3, 12), d(2026, 3, 25)));
    }

    @Test
    void rangesOverlapWhenTheyShareAtLeastOneDay() {
        assertTrue(DateUtil.rangesOverlap(d(2026, 11, 1), d(2026, 11, 5), d(2026, 11, 5), d(2026, 11, 9)),
                "touching on one day counts as overlapping");
        assertTrue(DateUtil.rangesOverlap(d(2026, 11, 1), d(2026, 11, 30), d(2026, 11, 10), d(2026, 11, 12)));
        assertFalse(DateUtil.rangesOverlap(d(2026, 11, 1), d(2026, 11, 4), d(2026, 11, 5), d(2026, 11, 9)));
    }

    @Test
    void weekStartsOnSunday() {
        LocalDate sunday = d(2026, 10, 25);
        assertEquals(sunday, DateUtil.startOfWeek(d(2026, 10, 28)), "Wednesday");
        assertEquals(sunday, DateUtil.startOfWeek(sunday), "Sunday stays Sunday");
        assertEquals(sunday, DateUtil.startOfWeek(d(2026, 10, 31)), "Saturday");
        assertEquals(d(2026, 11, 1), DateUtil.startOfWeek(d(2026, 11, 1)), "the next Sunday starts a new week");
    }

    // ---------------- BanglaDate ----------------
    // Expected values were worked out by hand from the month lengths (see BanglaDate).

    @Test
    void pohelaBoishakhIs14April() {
        for (BanglaDate.Rule rule : BanglaDate.Rule.values()) {
            assertEquals(new BanglaDate(1433, 1, 1), BanglaDate.from(d(2026, 4, 14), rule));
            assertEquals(new BanglaDate(1432, 12, 30), BanglaDate.from(d(2026, 4, 13), rule),
                    "the day before is the last day of Choitro");
        }
    }

    @Test
    void independenceDayIs12Choitro() {
        for (BanglaDate.Rule rule : BanglaDate.Rule.values()) {
            assertEquals(new BanglaDate(1432, 12, 12), BanglaDate.from(d(2026, 3, 26), rule));
        }
    }

    @Test
    void theTwoRulesDisagreeBetweenMidOctoberAndMidMarch() {
        // 21 Feb: Ekushey. The two rules differ here, which is why it is configurable.
        assertEquals(new BanglaDate(1432, 11, 8), BanglaDate.from(d(2026, 2, 21), BanglaDate.Rule.SIX_LONG_MONTHS));
        assertEquals(new BanglaDate(1432, 11, 9), BanglaDate.from(d(2026, 2, 21), BanglaDate.Rule.FIVE_LONG_MONTHS));
        assertEquals(new BanglaDate(1432, 9, 17), BanglaDate.from(d(2026, 1, 1), BanglaDate.Rule.SIX_LONG_MONTHS));
        assertEquals(new BanglaDate(1433, 7, 12), BanglaDate.from(d(2026, 10, 28), BanglaDate.Rule.SIX_LONG_MONTHS));
        assertEquals(new BanglaDate(1433, 7, 13), BanglaDate.from(d(2026, 10, 28), BanglaDate.Rule.FIVE_LONG_MONTHS));
    }

    @Test
    void banglaDateFormatting() {
        BanglaDate date = BanglaDate.from(d(2026, 10, 28), BanglaDate.Rule.SIX_LONG_MONTHS);
        assertEquals("12 Kartik 1433", date.format("en"));
        assertEquals("Kartik", date.monthName("en"));
        assertEquals("২০২৬", BanglaDate.toBanglaDigits(2026));
        assertEquals("০১২৩৪৫৬৭৮৯", BanglaDate.toBanglaDigits(123456789L).length() == 9
                ? "০১২৩৪৫৬৭৮৯" : "wrong length");
    }

    @Test
    void everyDayOf2026ConvertsToAValidBanglaDate() {
        for (LocalDate day : DateUtil.eachDay(d(2026, 1, 1), d(2026, 12, 31))) {
            for (BanglaDate.Rule rule : BanglaDate.Rule.values()) {
                BanglaDate b = BanglaDate.from(day, rule);
                assertTrue(b.month() >= 1 && b.month() <= 12, day + " month " + b.month());
                assertTrue(b.day() >= 1 && b.day() <= 31, day + " day " + b.day());
            }
        }
    }

    // ---------------- HijriDate ----------------

    @Test
    void hijriOffsetMovesTheDateByTheSameNumberOfDays() {
        LocalDate day = d(2026, 10, 28);
        assertEquals(HijriDate.from(day.minusDays(1), 0), HijriDate.from(day, -1));
        assertEquals(HijriDate.from(day.plusDays(1), 0), HijriDate.from(day, 1));
    }

    @Test
    void october2026isInHijriYear1448() {
        assertEquals(1448, HijriDate.from(d(2026, 10, 28), 0).year());
    }

    @Test
    void everyDayOf2026ConvertsToAValidHijriDate() {
        for (LocalDate day : DateUtil.eachDay(d(2026, 1, 1), d(2026, 12, 31))) {
            HijriDate h = HijriDate.from(day, 0);
            assertTrue(h.month() >= 1 && h.month() <= 12, day + " month " + h.month());
            assertTrue(h.day() >= 1 && h.day() <= 30, day + " day " + h.day());
        }
    }

    @Test
    void englishDateTextIsStable() {
        assertEquals("Wed, 28 Oct 2026", DateDisplay.english(d(2026, 10, 28)));
    }

    // ---------------- Validator ----------------

    @Test
    void requireNotBlankTrimsAndRejectsEmptyText() {
        assertEquals("ICT", Validator.requireNotBlank("  ICT  ", "Code"));
        assertThrows(ValidationException.class, () -> Validator.requireNotBlank("   ", "Code"));
        assertThrows(ValidationException.class, () -> Validator.requireNotBlank(null, "Code"));
    }

    @Test
    void timeRangeNeedsEndAfterStart() {
        Validator.requireTimeRange(null, null);                                   // both optional
        Validator.requireTimeRange(java.time.LocalTime.of(9, 0), null);           // end optional
        Validator.requireTimeRange(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0));
        assertThrows(ValidationException.class, () -> Validator.requireTimeRange(
                java.time.LocalTime.of(10, 0), java.time.LocalTime.of(10, 0)));
    }

    @Test
    void numbersMustStayInsideTheirRange() {
        Validator.requireInRangeOrNull(null, 1, 4, "Year");   // null = "not set" is fine
        Validator.requireInRangeOrNull(4, 1, 4, "Year");
        assertThrows(ValidationException.class, () -> Validator.requireInRangeOrNull(5, 1, 4, "Year"));
        assertThrows(ValidationException.class, () -> Validator.requireInRangeOrNull(0, 1, 4, "Year"));
    }
}
