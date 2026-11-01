package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.ScheduleSuggestionService.DateSuggestion;
import bd.ac.mbstu.calendar.service.ai.NaturalLanguageParser.DateRange;
import bd.ac.mbstu.calendar.util.DateUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The offline assistant. It needs no internet and no API key.
 * It understands a fixed set of English question styles using NaturalLanguageParser.
 * It is also the safety net when an online provider fails.
 */
public class RuleBasedAiService implements AiService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);
    private static final int MAX_LINES = 15;

    private static final Pattern HELLO =
            Pattern.compile("^(hi|hello|hey|help|salam|assalamu alaikum|what can you do)\\b");
    private static final Pattern DAYS_UNTIL = Pattern.compile(
            "\\b(?:how many days|days left|days)\\b.*?\\b(?:until|till|to|before)\\s+(.+?)\\s*\\??$");
    private static final Pattern IS_OFF = Pattern.compile(
            "\\b(?:is|are|was|were)\\b.*\\b(?:off|closed|holiday|vacation)\\b");
    private static final Pattern NEXT_ONE = Pattern.compile("\\bnext\\s+(?!week\\b|month\\b)");
    private static final Pattern WHEN_IS = Pattern.compile(
            "\\bwhen(?:'s|\\s+is|\\s+are|\\s+does|\\s+will)?\\s+(?:the\\s+)?(.+?)\\s*\\??$");
    private static final Pattern BANGLA = Pattern.compile("[\\u0980-\\u09FF]");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "is", "are", "what", "when", "a", "an", "of", "on", "in", "for", "to", "me", "show", "tell",
            "about", "any", "there", "my", "and", "or", "do", "does", "date", "day", "days", "many", "how",
            "much", "which", "who", "can", "you", "please", "have", "will", "be", "at", "from", "next");

    @Override
    public String name() {
        return "Offline assistant (rule-based)";
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    // ===============================================================
    // 1. Chat
    // ===============================================================

    @Override
    public String chat(String question, AiContext ctx) {
        String q = question == null ? "" : question.trim();
        String lower = q.toLowerCase(Locale.ENGLISH);

        if (lower.isEmpty() || HELLO.matcher(lower).find()) {
            return helpText(q);
        }

        Matcher until = DAYS_UNTIL.matcher(lower);
        if (until.find()) {
            return daysUntil(until.group(1), ctx);
        }

        if (IS_OFF.matcher(lower).find()) {
            Optional<DateRange> date = NaturalLanguageParser.findDates(q, ctx.today(), false);
            if (date.isPresent() && date.get().isSingleDay()) {
                return isOff(date.get().start(), ctx);
            }
        }

        Optional<EventType> type = NaturalLanguageParser.guessType(q);
        if (type.isPresent() || lower.contains("event") || lower.contains("schedule")) {
            return listEvents(q, lower, type.orElse(null), ctx);
        }

        Matcher when = WHEN_IS.matcher(lower);
        if (when.find()) {
            return whenIs(when.group(1), ctx);
        }
        return keywordSearchOrHelp(q, lower, ctx);
    }

    private String daysUntil(String target, AiContext ctx) {
        String cleaned = target.replaceAll("[?.!]", "").trim();
        Optional<Event> found = resolveTarget(cleaned, ctx);
        if (found.isEmpty()) {
            return "I could not find an upcoming event matching \"" + cleaned + "\" in the calendar.";
        }
        Event e = found.get();
        long days = ChronoUnit.DAYS.between(ctx.today(), e.getStartDate());
        if (days > 0) {
            return e.getTitle() + " starts on " + DAY.format(e.getStartDate()) + ", which is " + days
                    + (days == 1 ? " day" : " days") + " from today (" + DAY.format(ctx.today()) + ").";
        }
        if (days == 0) {
            return e.getTitle() + " starts today (" + DAY.format(ctx.today()) + ").";
        }
        return e.getTitle() + " is happening now and runs until " + DAY.format(e.getEndDate()) + ".";
    }

    private String whenIs(String target, AiContext ctx) {
        String cleaned = target.replaceAll("[?.!]", "").trim();
        Optional<Event> found = resolveTarget(cleaned, ctx);
        if (found.isEmpty()) {
            return "I could not find an upcoming event matching \"" + cleaned + "\" in the calendar.";
        }
        long days = ChronoUnit.DAYS.between(ctx.today(), found.get().getStartDate());
        return AiPrompts.humanLine(found.get())
                + (days > 0 ? "  (in " + days + (days == 1 ? " day)" : " days)") : "");
    }

    private String isOff(LocalDate date, AiContext ctx) {
        LocalDate oldest = ctx.today().minusDays(AiContextBuilder.DAYS_BACK);
        LocalDate newest = ctx.today().plusDays(AiContextBuilder.DAYS_AHEAD);
        if (date.isBefore(oldest) || date.isAfter(newest)) {
            return "I only have calendar data from " + DAY.format(oldest) + " to " + DAY.format(newest) + ".";
        }
        List<Event> off = ctx.events().stream()
                .filter(e -> e.occursOn(date) && (e.isClassOff() || e.isOfficeOff()))
                .toList();
        if (!off.isEmpty()) {
            Event e = off.get(0);
            return "Yes. " + DAY.format(date) + " is covered by \"" + e.getTitle() + "\". "
                    + (e.isClassOff() ? "Classes are off" : "Classes are on")
                    + (e.isOfficeOff() ? " and the office is closed." : "; the office stays open.");
        }
        if (ctx.policy().isOffDay(date)) {
            return "Yes. " + DAY.format(date) + " is a weekly off day (" + date.getDayOfWeek() + ").";
        }
        return "No. " + DAY.format(date) + " is a normal working day (no holiday or vacation is listed).";
    }

    private String listEvents(String q, String lower, EventType type, AiContext ctx) {
        LocalDate today = ctx.today();
        Optional<DateRange> range = NaturalLanguageParser.findDates(q, today, true);
        boolean onlyNext = NEXT_ONE.matcher(lower).find() && range.isEmpty();

        LocalDate from = range.map(DateRange::start).orElse(today);
        LocalDate to = range.map(DateRange::end).orElse(today.plusDays(onlyNext ? 365 : 60));

        Integer deptId = NaturalLanguageParser.findDepartment(q, ctx.departments())
                .map(d -> (int) d.getId()).orElse(null);
        Integer year = NaturalLanguageParser.findYear(q).orElse(null);
        Integer semester = NaturalLanguageParser.findSemester(q).orElse(null);
        String code = NaturalLanguageParser.findCourseCode(q).orElse(null);

        // Nothing mentioned? Assume the user means their own group, and say so in the answer.
        User user = ctx.user();
        if (deptId == null && year == null && semester == null && code == null && user != null) {
            if (user.getDepartmentId() != null) {
                deptId = user.getDepartmentId().intValue();
            }
            if (user.getRole() == Role.STUDENT) {
                year = user.getYear();
                semester = user.getSemester();
            }
        }

        final Integer dept = deptId;
        final Integer yr = year;
        final Integer sem = semester;
        List<Event> matches = ctx.events().stream()
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> !e.getEndDate().isBefore(from) && !e.getStartDate().isAfter(to))
                .filter(e -> groupMatches(e, dept, yr, sem))
                .filter(e -> code == null || mentions(e, code))
                .sorted(Comparator.comparing(Event::getStartDate))
                .toList();

        String scope = describeScope(ctx, dept, yr, sem);
        String label = type == null ? "Events" : type.getLabelEn() + " events";
        if (matches.isEmpty()) {
            return "I found no " + label.toLowerCase(Locale.ENGLISH) + " for " + scope
                    + " between " + from + " and " + to + ".";
        }
        if (onlyNext) {
            return "Next " + (type == null ? "event" : type.getLabelEn().toLowerCase(Locale.ENGLISH))
                    + " (" + scope + "):\n- " + AiPrompts.humanLine(matches.get(0));
        }

        StringBuilder sb = new StringBuilder(label).append(" for ").append(scope)
                .append(" (").append(from).append(" to ").append(to).append("):");
        int shown = 0;
        for (Event e : matches) {
            if (shown++ == MAX_LINES) {
                sb.append("\n...and ").append(matches.size() - MAX_LINES).append(" more.");
                break;
            }
            sb.append("\n- ").append(AiPrompts.humanLine(e));
        }
        return sb.toString();
    }

    private String keywordSearchOrHelp(String q, String lower, AiContext ctx) {
        List<String> words = new ArrayList<>();
        for (String w : lower.split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                words.add(w);
            }
        }
        List<Event> matches = ctx.events().stream()
                .filter(e -> !e.getEndDate().isBefore(ctx.today()))
                .filter(e -> words.stream().anyMatch(w -> norm(e.getTitle()).contains(w)))
                .sorted(Comparator.comparing(Event::getStartDate))
                .limit(5)
                .toList();
        if (matches.isEmpty()) {
            return "Sorry, I did not understand that.\n\n" + helpText(q);
        }
        return "These upcoming events match your words:\n"
                + matches.stream().map(e -> "- " + AiPrompts.humanLine(e)).collect(Collectors.joining("\n"));
    }

    private String helpText(String original) {
        String text = "I am in offline mode (no internet AI), so I understand a fixed set of English questions:\n"
                + "- When is the next holiday?\n"
                + "- How many days until Victory Day?\n"
                + "- Show class tests in November\n"
                + "- Show CSE exams next week\n"
                + "- Is tomorrow a holiday?\n"
                + "- When is the ICT-2207 class test?\n"
                + "For free-form questions, set an AI provider in config/app.properties.";
        if (BANGLA.matcher(original).find()) {
            text += "\n(অফলাইন মোডে শুধু ইংরেজি প্রশ্ন বোঝা যায়।)";
        }
        return text;
    }

    // ---------------------------------------------------------------
    // Helpers for chat
    // ---------------------------------------------------------------

    /** Finds the event a phrase like "eid", "victory day" or "next holiday" refers to. */
    private Optional<Event> resolveTarget(String phrase, AiContext ctx) {
        LocalDate today = ctx.today();
        String cleaned = phrase.toLowerCase(Locale.ENGLISH)
                .replaceAll("\\b(the|next|upcoming|a|an|of|my)\\b", " ")
                .replaceAll("\\s+", " ").trim();

        List<Event> upcoming = ctx.events().stream()
                .filter(e -> !e.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Event::getStartDate))
                .toList();

        // "next holiday", "next exam": first upcoming event of that type for the user's group
        Optional<EventType> type = NaturalLanguageParser.guessType(cleaned);
        if (type.isPresent() && NaturalLanguageParser.findCourseCode(cleaned).isEmpty()) {
            User u = ctx.user();
            Integer dept = (u != null && u.getDepartmentId() != null) ? u.getDepartmentId().intValue() : null;
            Integer year = (u != null && u.getRole() == Role.STUDENT) ? u.getYear() : null;
            Integer sem = (u != null && u.getRole() == Role.STUDENT) ? u.getSemester() : null;
            return upcoming.stream()
                    .filter(e -> e.getType() == type.get() && groupMatches(e, dept, year, sem))
                    .findFirst();
        }
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        String n = norm(cleaned);
        Optional<Event> byTitle = upcoming.stream().filter(e -> titleMatches(e, cleaned, n)).findFirst();
        if (byTitle.isPresent()) {
            return byTitle;
        }
        return upcoming.stream()
                .filter(e -> e.getDescription() != null && norm(e.getDescription()).contains(n))
                .findFirst();
    }

    private boolean titleMatches(Event e, String phrase, String normalizedPhrase) {
        if (!normalizedPhrase.isEmpty() && norm(e.getTitle()).contains(normalizedPhrase)) {
            return true;
        }
        return e.getTitleBn() != null && e.getTitleBn().contains(phrase.trim());
    }

    /** A missing value (null) on either side counts as "matches". */
    private boolean groupMatches(Event e, Integer dept, Integer year, Integer sem) {
        return nullOrEqual(e.getDepartmentId(), dept)
                && nullOrEqual(e.getYear(), year)
                && nullOrEqual(e.getSemester(), sem);
    }

    private boolean nullOrEqual(Integer a, Integer b) {
        return a == null || b == null || a.equals(b);
    }

    private boolean mentions(Event e, String code) {
        String c = norm(code);
        return norm(e.getTitle()).contains(c)
                || (e.getCourseCode() != null && norm(e.getCourseCode()).contains(c));
    }

    private String describeScope(AiContext ctx, Integer dept, Integer year, Integer sem) {
        List<String> parts = new ArrayList<>();
        if (dept != null) {
            parts.add(ctx.departmentCode(dept));
        }
        if (year != null) {
            parts.add("year " + year);
        }
        if (sem != null) {
            parts.add("semester " + sem);
        }
        return parts.isEmpty() ? "all departments" : String.join(", ", parts);
    }

    /** Lower-case letters and digits only: "Eid-ul-Fitr" becomes "eidulfitr". */
    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
    }

    // ===============================================================
    // 2. Natural-language event creation
    // ===============================================================

    @Override
    public ParsedEvent parseEvent(String text, AiContext ctx) throws AiException {
        return parseSegment(text, ctx, false).orElseThrow(() -> new AiException(AiException.Kind.UNDERSTAND,
                "I could not find a date in that text. Try: Add ICT-2207 class test on 15 Nov at 10am for 2nd year."));
    }

    /** Builds a proposed event from one sentence. Empty if the sentence has no exact date. */
    private Optional<ParsedEvent> parseSegment(String text, AiContext ctx, boolean keepSourceText) {
        Optional<DateRange> dates = NaturalLanguageParser.findDates(text, ctx.today(), false);
        if (dates.isEmpty()) {
            return Optional.empty();
        }
        List<String> warnings = new ArrayList<>();

        EventType type = NaturalLanguageParser.guessType(text).orElse(null);
        if (type == null) {
            type = EventType.NOTICE;
            warnings.add("I could not tell the event type. Please choose it.");
        }
        Optional<String> code = NaturalLanguageParser.findCourseCode(text);
        Optional<Department> dept = NaturalLanguageParser.findDepartment(text, ctx.departments());
        if (dept.isEmpty() && (type == EventType.CLASS_TEST || type == EventType.EXAM)) {
            warnings.add("No department was found in the text. Please choose one.");
        }

        List<LocalTime> times = NaturalLanguageParser.findTimes(text);
        LocalTime start = times.isEmpty() ? null : times.get(0);
        LocalTime end = (times.size() > 1 && times.get(1).isAfter(times.get(0))) ? times.get(1) : null;

        String source = AiPrompts.oneLine(text);
        String description = keepSourceText
                ? "From notice: " + (source.length() > 300 ? source.substring(0, 300) : source)
                : null;

        return Optional.of(new ParsedEvent(
                buildTitle(code.orElse(null), type, text),
                type, dates.get().start(), dates.get().end(), start, end,
                dept.map(Department::getCode).orElse(null),
                NaturalLanguageParser.findYear(text).orElse(null),
                NaturalLanguageParser.findSemester(text).orElse(null),
                code.orElse(null),
                NaturalLanguageParser.findLocation(text).orElse(null),
                description, warnings));
    }

    /** "ICT-2207 Class Test", or the words after "add" and before "on/at/for...". */
    private String buildTitle(String courseCode, EventType type, String text) {
        if (courseCode != null) {
            return courseCode + " " + type.getLabelEn();
        }
        String t = text.trim().replaceFirst(
                "(?i)^(please\\s+)?(add|create|schedule|set\\s+up|new)\\s+(an?\\s+|the\\s+)?", "");
        Matcher cut = Pattern.compile("(?i)\\b(on|at|for|from|until|till|by)\\b").matcher(t);
        if (cut.find() && cut.start() > 0) {
            t = t.substring(0, cut.start());
        }
        t = t.trim().replaceAll("[,.;:]+$", "").trim();
        if (t.isEmpty()) {
            return type.getLabelEn();
        }
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    // ===============================================================
    // 3. Schedule suggestion text
    // ===============================================================

    @Override
    public String suggestSchedule(String request, List<DateSuggestion> options, AiContext ctx) {
        StringBuilder sb = new StringBuilder("Best dates for ").append(AiPrompts.oneLine(request)).append(":\n");
        int rank = 1;
        for (DateSuggestion s : options) {
            if (rank > 3) {
                break;
            }
            sb.append(rank++).append(". ").append(DAY.format(s.date())).append(" - ").append(s.reason()).append("\n");
        }
        sb.append("These dates already avoid holidays, weekly off days and clashes with other tests or exams ")
          .append("of the same department, year and semester.");
        return sb.toString();
    }

    // ===============================================================
    // 4. Study plan
    // ===============================================================

    @Override
    public String studyPlan(List<Event> upcoming, AiContext ctx) {
        LocalDate today = ctx.today();
        List<Event> items = upcoming.stream()
                .filter(e -> !e.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Event::getStartDate))
                .toList();
        if (items.isEmpty()) {
            return "You have no upcoming tests, exams or deadlines, so there is nothing to plan yet.";
        }

        LocalDate last = items.get(items.size() - 1).getStartDate();
        StringBuilder sb = new StringBuilder(
                "Study plan (about 2 to 3 hours of focused study per day, plus one rest day each week):\n");

        LocalDate weekStart = DateUtil.startOfWeek(today);
        int week = 1;
        for (LocalDate ws = weekStart; !ws.isAfter(last) && week <= 12; ws = ws.plusWeeks(1), week++) {
            LocalDate we = ws.plusDays(6);
            sb.append("\nWeek ").append(week).append(" (").append(SHORT.format(ws)).append(" - ")
              .append(SHORT.format(we)).append(")\n");

            List<Event> thisWeek = startingBetween(items, ws, we);
            List<Event> nextWeek = startingBetween(items, ws.plusWeeks(1), we.plusWeeks(1));
            List<Event> inTwoWeeks = startingBetween(items, ws.plusWeeks(2), we.plusWeeks(2));

            if (thisWeek.size() >= 2) {
                sb.append("- Busy week: study for the earliest item first and give each item a fixed time block.\n");
            }
            for (Event e : thisWeek) {
                sb.append("- ").append(AiPrompts.humanLine(e)).append(": ").append(advice(e.getType())).append("\n");
            }
            for (Event e : nextWeek) {
                sb.append("- Prepare for \"").append(e.getTitle())
                  .append("\" (next week): finish all topics and start practice problems.\n");
            }
            for (Event e : inTwoWeeks) {
                sb.append("- Start early for \"").append(e.getTitle())
                  .append("\" (in 2 weeks): list the chapters and mark your weak topics.\n");
            }
            if (thisWeek.isEmpty() && nextWeek.isEmpty() && inTwoWeeks.isEmpty()) {
                sb.append("- Light week: revise earlier lectures and catch up on your notes.\n");
            }
        }
        return sb.toString().trim();
    }

    private List<Event> startingBetween(List<Event> events, LocalDate from, LocalDate to) {
        return events.stream()
                .filter(e -> !e.getStartDate().isBefore(from) && !e.getStartDate().isAfter(to))
                .toList();
    }

    private String advice(EventType type) {
        return switch (type) {
            case ASSIGNMENT_DEADLINE -> "finish and proofread, then submit before the deadline.";
            case EXAM, CLASS_TEST -> "revise key topics, solve past questions, and rest well the night before.";
            default -> "be ready.";
        };
    }

    // ===============================================================
    // 5. Weekly digest
    // ===============================================================

    @Override
    public String weeklyDigest(String scopeLabel, List<Event> events, LocalDate from, LocalDate to,
                               AiContext ctx) {
        StringBuilder sb = new StringBuilder("Weekly digest for ").append(AiPrompts.oneLine(scopeLabel))
                .append(": ").append(DAY.format(from)).append(" - ").append(DAY.format(to)).append("\n");

        List<Event> week = events.stream()
                .filter(e -> !e.getEndDate().isBefore(from) && !e.getStartDate().isAfter(to))
                .sorted(Comparator.comparing(Event::getStartDate))
                .toList();
        if (week.isEmpty()) {
            sb.append("Nothing is scheduled this week.");
            return sb.toString();
        }

        EnumMap<EventType, Long> counts = new EnumMap<>(EventType.class);
        for (Event e : week) {
            counts.merge(e.getType(), 1L, Long::sum);
        }
        sb.append(week.size()).append(week.size() == 1 ? " item: " : " items: ")
          .append(counts.entrySet().stream()
                  .map(en -> en.getValue() + " " + en.getKey().getLabelEn())
                  .collect(Collectors.joining(", ")))
          .append(".\n\n");

        LocalDate busiest = null;
        int busiestCount = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            final LocalDate day = d;
            List<Event> onDay = week.stream().filter(e -> e.occursOn(day)).toList();
            if (onDay.isEmpty()) {
                continue;
            }
            sb.append(DAY.format(d)).append(ctx.policy().isOffDay(d) ? " (weekly off)" : "").append("\n");
            for (Event e : onDay) {
                sb.append("- ").append(e.getTitle()).append(" [").append(e.getType().getLabelEn()).append("]")
                  .append(e.isClassOff() ? " - classes off" : "").append("\n");
            }
            if (onDay.size() > busiestCount) {
                busiestCount = onDay.size();
                busiest = d;
            }
        }
        if (busiestCount >= 2) {
            sb.append("\nBusiest day: ").append(DAY.format(busiest)).append(" (").append(busiestCount).append(" items).");
        }
        return sb.toString().trim();
    }

    // ===============================================================
    // 6. Notice extraction
    // ===============================================================

    @Override
    public List<ParsedEvent> extractFromNotice(String notice, AiContext ctx) {
        List<ParsedEvent> result = new ArrayList<>();
        for (String segment : segments(notice)) {
            parseSegment(segment, ctx, true).ifPresent(result::add);
        }
        return result;
    }

    /** One segment per line. Long lines are split into sentences. */
    private List<String> segments(String notice) {
        List<String> list = new ArrayList<>();
        for (String line : notice.split("\\R+")) {
            String trimmed = line.trim();
            if (trimmed.length() < 8) {
                continue;
            }
            if (trimmed.length() > 160) {
                for (String sentence : trimmed.split("(?<=[.!?])\\s+(?=[A-Z0-9])")) {
                    if (sentence.trim().length() >= 8) {
                        list.add(sentence.trim());
                    }
                }
            } else {
                list.add(trimmed);
            }
        }
        return list;
    }
}
