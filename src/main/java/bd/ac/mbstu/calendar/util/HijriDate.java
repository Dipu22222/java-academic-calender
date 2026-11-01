package bd.ac.mbstu.calendar.util;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * A Hijri (Islamic) date.
 *
 * Java has a built-in Hijri calendar (Umm al-Qura, used in Saudi Arabia).
 * Bangladesh follows LOCAL moon sighting, which is usually one day later,
 * so we shift the date before converting. The shift is configurable:
 *     hijri.offset.days=-1      (in config/app.properties)
 * The official printed calendar always wins. This is only an approximation
 * for days that are not listed there.
 */
public record HijriDate(int year, int month, int day) {

    private static final String[] MONTHS_BN = {
        "মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    };

    private static final String[] MONTHS_EN = {
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah"
    };

    private static final int DEFAULT_OFFSET = Config.getInt("hijri.offset.days", -1);

    public static HijriDate from(LocalDate date) {
        return from(date, DEFAULT_OFFSET);
    }

    /** offsetDays is added to the Gregorian date before converting (e.g. -1). */
    public static HijriDate from(LocalDate date, int offsetDays) {
        HijrahDate h = HijrahDate.from(date.plusDays(offsetDays));
        return new HijriDate(
                h.get(ChronoField.YEAR),
                h.get(ChronoField.MONTH_OF_YEAR),
                h.get(ChronoField.DAY_OF_MONTH));
    }

    public String monthName(String language) {
        return "bn".equals(language) ? MONTHS_BN[month - 1] : MONTHS_EN[month - 1];
    }

    /** "15 Rabi al-Thani 1448" or "১৫ রবিউস সানি ১৪৪৮". */
    public String format(String language) {
        if ("bn".equals(language)) {
            return BanglaDate.toBanglaDigits(day) + " " + monthName(language)
                    + " " + BanglaDate.toBanglaDigits(year);
        }
        return day + " " + monthName(language) + " " + year;
    }
}
