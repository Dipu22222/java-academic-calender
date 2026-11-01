package bd.ac.mbstu.calendar.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the text shown beside a date: English | Bangla | Hijri.
 * The English part is always in English so it renders safely on every computer.
 */
public final class DateDisplay {

    private static final DateTimeFormatter ENGLISH =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    private DateDisplay() {
    }

    public static String english(LocalDate date) {
        return ENGLISH.format(date);
    }

    /** Example: "Wed, 28 Oct 2026  |  12 Kartik 1433  |  16 Jumada al-Awwal 1448" */
    public static String full(LocalDate date, String language) {
        return english(date) + "  |  "
                + BanglaDate.from(date).format(language) + "  |  "
                + HijriDate.from(date).format(language);
    }
}
