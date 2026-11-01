package bd.ac.mbstu.calendar.util;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;

/**
 * A date in the Bangladesh revised Bangla calendar (Bangabda).
 *
 * The year starts on 14 April (1 Boishakh). Two month-length rules are supported
 * because I could not confirm which one your printed calendar uses:
 *
 *  SIX_LONG_MONTHS  (default)  Boishakh..Ashwin = 31 days, Kartik..Chaitra = 30,
 *                              Falgun = 29 (30 in a leap year).  Falgun starts on 14 Feb.
 *  FIVE_LONG_MONTHS            Boishakh..Bhadro = 31 days, Ashwin..Chaitra = 30,
 *                              Falgun = 31 in a leap year.       Falgun starts on 13 Feb.
 *
 * Choose with  bangla.calendar.rule=six31  or  five31  in config/app.properties.
 * Bangla year = Gregorian year - 593 (on/after 14 April) or - 594 (before it).
 * Example: 14 April 2026 -> 1 Boishakh 1433.
 */
public record BanglaDate(int year, int month, int day) {

    public enum Rule { SIX_LONG_MONTHS, FIVE_LONG_MONTHS }

    private static final String[] MONTHS_BN = {
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
        "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    };

    private static final String[] MONTHS_EN = {
        "Boishakh", "Joishtho", "Ashar", "Srabon", "Bhadro", "Ashwin",
        "Kartik", "Ogrohayon", "Poush", "Magh", "Falgun", "Choitro"
    };

    private static final String BANGLA_DIGITS = "০১২৩৪৫৬৭৮৯";

    private static final Rule DEFAULT_RULE =
            "five31".equalsIgnoreCase(Config.get("bangla.calendar.rule", "six31"))
                    ? Rule.FIVE_LONG_MONTHS : Rule.SIX_LONG_MONTHS;

    /** Converts a Gregorian date using the configured rule. */
    public static BanglaDate from(LocalDate date) {
        return from(date, DEFAULT_RULE);
    }

    /** Converts a Gregorian date using an explicit rule (the unit tests use this). */
    public static BanglaDate from(LocalDate date, Rule rule) {
        // The Bangla year started on 14 April of this or of last Gregorian year.
        int startYear = date.isBefore(LocalDate.of(date.getYear(), 4, 14))
                ? date.getYear() - 1
                : date.getYear();

        LocalDate yearStart = LocalDate.of(startYear, 4, 14);
        long offset = ChronoUnit.DAYS.between(yearStart, date); // 0 = 1 Boishakh

        int[] lengths = monthLengths(startYear, rule);
        int monthIndex = 0;
        while (offset >= lengths[monthIndex]) {
            offset -= lengths[monthIndex];
            monthIndex++;
        }
        return new BanglaDate(startYear - 593, monthIndex + 1, (int) offset + 1);
    }

    /** Month lengths of the Bangla year that starts on 14 April of startYear. */
    private static int[] monthLengths(int startYear, Rule rule) {
        // Falgun is in Feb-Mar of the NEXT Gregorian year, so that is the year that may be a leap year.
        boolean leap = Year.isLeap(startYear + 1);
        if (rule == Rule.SIX_LONG_MONTHS) {
            return new int[] {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, leap ? 30 : 29, 30};
        }
        return new int[] {31, 31, 31, 31, 31, 30, 30, 30, 30, 30, leap ? 31 : 30, 30};
    }

    public String monthName(String language) {
        return "bn".equals(language) ? MONTHS_BN[month - 1] : MONTHS_EN[month - 1];
    }

    /** "12 কার্তিক ১৪৩৩" (Bangla digits) or "12 Kartik 1433" (English). */
    public String format(String language) {
        if ("bn".equals(language)) {
            return toBanglaDigits(day) + " " + monthName(language) + " " + toBanglaDigits(year);
        }
        return day + " " + monthName(language) + " " + year;
    }

    /** 2026 -> "২০২৬" */
    public static String toBanglaDigits(long number) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) {
            sb.append(Character.isDigit(c) ? BANGLA_DIGITS.charAt(c - '0') : c);
        }
        return sb.toString();
    }
}
