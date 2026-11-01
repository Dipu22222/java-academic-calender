package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.DateUtil;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands simple English sentences WITHOUT any AI, using regular expressions (regex).
 * Examples it handles:
 *   dates : "15 Nov", "Nov 15, 2026", "2026-11-15", "10 to 14 Nov", "tomorrow", "next Sunday",
 *           and (for questions only) "next week", "this month", "in November"
 *   times : "10am", "3:30 pm", "14:00"
 *   other : course code (ICT-2207), department, "2nd year", "1st semester", "Room 301"
 */
public final class NaturalLanguageParser {

    /** A first and last day (the same day for single-day dates). */
    public record DateRange(LocalDate start, LocalDate end) {
        public boolean isSingleDay() {
            return start.equals(end);
        }
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final List<String> MONTH_ABBR =
            List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
    private static final String MONTH =
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?"
          + "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b";
    private static final String ORD = "(?:st|nd|rd|th)?";
    private static final String TO = "\\s*(?:to|until|till|-|–)\\s*";

    // (?<!\d) means "not directly after another digit", so "2026" never gives "26 Nov".
    private static final Pattern ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    // groups: 1=day 2=month 3=day 4=month 5=year
    private static final Pattern RANGE_TWO_MONTHS = Pattern.compile(
            "(?<!\\d)(\\d{1,2})" + ORD + "\\s+" + MONTH + TO + "(\\d{1,2})" + ORD + "\\s+" + MONTH
          + "(?:\\s*,?\\s*(\\d{4}))?(?!\\d)", CI);
    // groups: 1=day 2=day 3=month 4=year
    private static final Pattern RANGE_ONE_MONTH = Pattern.compile(
            "(?<!\\d)(\\d{1,2})" + ORD + TO + "(\\d{1,2})" + ORD + "\\s+(?:of\\s+)?" + MONTH
          + "(?:\\s*,?\\s*(\\d{4}))?(?!\\d)", CI);
    // groups: 1=day 2=month 3=year
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(?<!\\d)(\\d{1,2})" + ORD + "\\s+(?:of\\s+)?" + MONTH + "(?:\\s*,?\\s*(\\d{4}))?(?!\\d)", CI);
    // groups: 1=month 2=day 3=year
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b" + MONTH + "\\s+(\\d{1,2})" + ORD + "(?:\\s*,?\\s*(\\d{4}))?(?!\\d)", CI);
    // groups: 1=month 2=year
    private static final Pattern MONTH_ONLY = Pattern.compile(
            "\\b(?:in|during|of|for)\\s+" + MONTH + "(?:\\s*,?\\s*(\\d{4}))?(?!\\d)", CI);
    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(?:(next|this|coming)\\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b", CI);

    // groups: 1=hour 2=minute 3=am/pm   OR   4=hour 5=minute (24-hour clock)
    private static final Pattern TIME = Pattern.compile(
            "(?<![\\d:])(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)\\b|(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)", CI);

    private static final Pattern COURSE = Pattern.compile("\\b([A-Za-z]{3,4})[-\\s]?(\\d{4})\\b");
    private static final Set<String> NOT_COURSE_WORDS = Set.of(
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            "june", "july", "sept", "sun", "mon", "tue", "wed", "thu", "fri", "sat",
            "year", "room", "hall", "date", "time", "from", "till", "will", "held", "lab");

    private static final Pattern YEAR_NUM = Pattern.compile("\\b([1-4])\\s*(?:st|nd|rd|th)?[\\s-]*year\\b", CI);
    private static final Pattern YEAR_WORD = Pattern.compile("\\b(first|second|third|fourth)[\\s-]*year\\b", CI);
    private static final Pattern YEAR_AFTER = Pattern.compile("\\byear\\s*[-:]?\\s*([1-4])\\b", CI);
    private static final Pattern SEM_NUM = Pattern.compile("\\b([1-2])\\s*(?:st|nd)?[\\s-]*(?:semester|sem)\\b", CI);
    private static final Pattern SEM_WORD = Pattern.compile("\\b(first|second)[\\s-]*(?:semester|sem)\\b", CI);
    private static final Pattern SEM_AFTER = Pattern.compile("\\b(?:semester|sem)\\s*[-:]?\\s*([1-2])\\b", CI);
    private static final Pattern LOCATION = Pattern.compile(
            "\\b((?:room|hall|lab|auditorium)\\s*(?:no\\.?\\s*)?(?:[A-Za-z]-?)?\\d+[A-Za-z0-9-]*)", CI);

    /** Event type keywords. The keyword that appears FIRST in the text decides the type. */
    private static final Map<EventType, Pattern> TYPE_PATTERNS = new LinkedHashMap<>();

    static {
        TYPE_PATTERNS.put(EventType.EXAM,
                Pattern.compile("\\b(exams?|examinations?|mid-?terms?|finals?)\\b", CI));
        TYPE_PATTERNS.put(EventType.CLASS_TEST,
                Pattern.compile("\\b(class\\s*tests?|cts?|quiz(?:zes)?|tests?)\\b", CI));
        TYPE_PATTERNS.put(EventType.ASSIGNMENT_DEADLINE,
                Pattern.compile("\\b(assignments?|deadlines?|submissions?|submit(?:ted)?|due)\\b", CI));
        TYPE_PATTERNS.put(EventType.SEMESTER_BREAK,
                Pattern.compile("\\b(vacations?|semester\\s*breaks?|breaks?)\\b", CI));
        TYPE_PATTERNS.put(EventType.HOLIDAY,
                Pattern.compile("\\b(holidays?|off\\s*days?)\\b", CI));
        TYPE_PATTERNS.put(EventType.NOTICE,
                Pattern.compile("\\b(seminars?|workshops?|notices?|webinars?|meetings?|orientation)\\b", CI));
        TYPE_PATTERNS.put(EventType.DEPARTMENT_EVENT,
                Pattern.compile("\\b(fests?|competitions?|contests?|tours?|picnics?|programmes?|programs?|cultural|sports)\\b", CI));
    }

    private NaturalLanguageParser() {
    }

    // ---------------------------------------------------------------
    // Dates
    // ---------------------------------------------------------------

    /**
     * Finds the first date or date range in the text.
     *
     * @param allowVague true for questions ("next week", "in November"); false when creating
     *                   an event, where we need an exact day
     */
    public static Optional<DateRange> findDates(String text, LocalDate today, boolean allowVague) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(search(text, today, allowVague));
        } catch (DateTimeException e) {
            return Optional.empty(); // for example "31 Feb"
        }
    }

    private static DateRange search(String text, LocalDate today, boolean allowVague) {
        Matcher m = ISO.matcher(text);
        if (m.find()) {
            return single(LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        }

        m = RANGE_TWO_MONTHS.matcher(text);
        if (m.find()) {
            Integer year = year(m, 5);
            LocalDate start = resolve(Integer.parseInt(m.group(1)), monthNo(m.group(2)), year, today);
            LocalDate end = resolve(Integer.parseInt(m.group(3)), monthNo(m.group(4)), year, today);
            if (end.isBefore(start) && year == null) {
                end = end.plusYears(1);
            }
            return end.isBefore(start) ? null : new DateRange(start, end);
        }

        m = RANGE_ONE_MONTH.matcher(text);
        if (m.find()) {
            LocalDate start = resolve(Integer.parseInt(m.group(1)), monthNo(m.group(3)), year(m, 4), today);
            LocalDate end = start.withDayOfMonth(Integer.parseInt(m.group(2)));
            return end.isBefore(start) ? null : new DateRange(start, end);
        }

        m = DAY_MONTH.matcher(text);
        if (m.find()) {
            return single(resolve(Integer.parseInt(m.group(1)), monthNo(m.group(2)), year(m, 3), today));
        }

        m = MONTH_DAY.matcher(text);
        if (m.find()) {
            return single(resolve(Integer.parseInt(m.group(2)), monthNo(m.group(1)), year(m, 3), today));
        }

        String lower = text.toLowerCase(Locale.ENGLISH);
        if (lower.contains("day after tomorrow")) {
            return single(today.plusDays(2));
        }
        if (lower.contains("tomorrow")) {
            return single(today.plusDays(1));
        }
        if (lower.contains("today")) {
            return single(today);
        }

        m = WEEKDAY.matcher(text);
        if (m.find()) {
            DayOfWeek day = DayOfWeek.valueOf(m.group(2).toUpperCase(Locale.ENGLISH));
            boolean next = "next".equalsIgnoreCase(m.group(1));
            return single(today.with(next ? TemporalAdjusters.next(day) : TemporalAdjusters.nextOrSame(day)));
        }

        if (!allowVague) {
            return null;
        }
        LocalDate weekStart = DateUtil.startOfWeek(today);
        if (lower.contains("next week")) {
            return new DateRange(weekStart.plusWeeks(1), weekStart.plusWeeks(1).plusDays(6));
        }
        if (lower.contains("this week")) {
            return new DateRange(weekStart, weekStart.plusDays(6));
        }
        if (lower.contains("next month")) {
            return wholeMonth(today.plusMonths(1).withDayOfMonth(1));
        }
        if (lower.contains("this month")) {
            return wholeMonth(today.withDayOfMonth(1));
        }
        m = MONTH_ONLY.matcher(text);
        if (m.find()) {
            int month = monthNo(m.group(1));
            Integer year = year(m, 2);
            int y = year != null ? year : (month < today.getMonthValue() ? today.getYear() + 1 : today.getYear());
            return wholeMonth(LocalDate.of(y, month, 1));
        }
        return null;
    }

    /** Day and month without a year: use this year, or next year if that day has already passed. */
    private static LocalDate resolve(int day, int month, Integer year, LocalDate today) {
        if (year != null) {
            return LocalDate.of(year, month, day);
        }
        LocalDate date = LocalDate.of(today.getYear(), month, day);
        return date.isBefore(today) ? date.plusYears(1) : date;
    }

    private static DateRange single(LocalDate d) {
        return new DateRange(d, d);
    }

    private static DateRange wholeMonth(LocalDate firstDay) {
        return new DateRange(firstDay, firstDay.withDayOfMonth(firstDay.lengthOfMonth()));
    }

    private static int monthNo(String token) {
        return MONTH_ABBR.indexOf(token.toLowerCase(Locale.ENGLISH).substring(0, 3)) + 1;
    }

    private static Integer year(Matcher m, int group) {
        String y = m.group(group);
        return y == null ? null : Integer.valueOf(y);
    }

    // ---------------------------------------------------------------
    // Times
    // ---------------------------------------------------------------

    /** Up to two times found in the text (start, then end). */
    public static List<LocalTime> findTimes(String text) {
        List<LocalTime> times = new ArrayList<>();
        if (text == null) {
            return times;
        }
        Matcher m = TIME.matcher(text);
        while (m.find() && times.size() < 2) {
            if (m.group(3) != null) {                       // 12-hour clock: 10am, 3:30 pm
                int hour = Integer.parseInt(m.group(1));
                int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                if (hour < 1 || hour > 12) {
                    continue;
                }
                boolean pm = m.group(3).equalsIgnoreCase("pm");
                times.add(LocalTime.of(hour % 12 + (pm ? 12 : 0), minute));
            } else {                                        // 24-hour clock: 14:30
                times.add(LocalTime.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))));
            }
        }
        return times;
    }

    // ---------------------------------------------------------------
    // Event details
    // ---------------------------------------------------------------

    public static Optional<EventType> guessType(String text) {
        if (text == null) {
            return Optional.empty();
        }
        EventType best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Map.Entry<EventType, Pattern> entry : TYPE_PATTERNS.entrySet()) {
            Matcher m = entry.getValue().matcher(text);
            if (m.find() && m.start() < bestIndex) {
                bestIndex = m.start();
                best = entry.getKey();
            }
        }
        return Optional.ofNullable(best);
    }

    /** "ict 2207" or "ICT-2207" becomes "ICT-2207". */
    public static Optional<String> findCourseCode(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = COURSE.matcher(text);
        while (m.find()) {
            String letters = m.group(1).toLowerCase(Locale.ENGLISH);
            if (!NOT_COURSE_WORDS.contains(letters)) {
                return Optional.of(m.group(1).toUpperCase(Locale.ENGLISH) + "-" + m.group(2));
            }
        }
        return Optional.empty();
    }

    /** The department whose code (ICT, CSE...) appears first in the text. */
    public static Optional<Department> findDepartment(String text, List<Department> departments) {
        if (text == null) {
            return Optional.empty();
        }
        Department best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Department d : departments) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(d.getCode()) + "\\b", CI).matcher(text);
            if (m.find() && m.start() < bestIndex) {
                bestIndex = m.start();
                best = d;
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<Integer> findYear(String text) {
        return findNumber(text, YEAR_NUM, YEAR_WORD, YEAR_AFTER);
    }

    public static Optional<Integer> findSemester(String text) {
        return findNumber(text, SEM_NUM, SEM_WORD, SEM_AFTER);
    }

    public static Optional<String> findLocation(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = LOCATION.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private static Optional<Integer> findNumber(String text, Pattern numeric, Pattern word, Pattern after) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = numeric.matcher(text);
        if (m.find()) {
            return Optional.of(Integer.parseInt(m.group(1)));
        }
        m = word.matcher(text);
        if (m.find()) {
            return Optional.of(switch (m.group(1).toLowerCase(Locale.ENGLISH)) {
                case "first" -> 1;
                case "second" -> 2;
                case "third" -> 3;
                default -> 4;
            });
        }
        m = after.matcher(text);
        if (m.find()) {
            return Optional.of(Integer.parseInt(m.group(1)));
        }
        return Optional.empty();
    }
}
