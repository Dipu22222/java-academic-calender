package bd.ac.mbstu.calendar.model;

/**
 * Bangla and Hijri month names printed on the university calendar
 * for one English month. A Java "record" is a short way to write a
 * class that only holds data (it creates the getters for you).
 */
public record CalendarMonth(int year, int month, String banglaMonths, String hijriMonths) {
}
