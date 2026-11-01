package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.EventService;
import bd.ac.mbstu.calendar.service.PermissionDeniedException;
import bd.ac.mbstu.calendar.service.ScheduleSuggestionService;
import bd.ac.mbstu.calendar.service.ScheduleSuggestionService.DateSuggestion;
import bd.ac.mbstu.calendar.util.DateUtil;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * FACADE: one simple class for the whole AI layer. The UI never builds prompts or
 * talks to providers. It calls these methods and gets an AiResult back.
 *
 * Every method that uses an AI provider goes through run(), which:
 *   1. tries the online provider (if one is configured),
 *   2. on ANY failure (no internet, timeout, bad key, bad answer...) logs it,
 *      then answers with the offline RuleBasedAiService and sets a warning.
 *
 * IMPORTANT: online calls can take seconds, so call these methods from a
 * background thread (JavaFX Task), never from the UI thread.
 */
public class AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(AiAssistant.class);

    /** One AI action, applied to whichever provider run() chooses. */
    @FunctionalInterface
    private interface AiCall<T> {
        T run(AiService service) throws AiException;
    }

    private final AiService primary;                     // online provider, or null
    private final RuleBasedAiService offline = new RuleBasedAiService();
    private final String startupNote;
    private final Clock clock;
    private final EventService eventService = new EventService();
    private final ScheduleSuggestionService suggester = new ScheduleSuggestionService();

    /** The Clock lets the demo and tests pretend it is any date. */
    public AiAssistant(AiService primary, String startupNote, Clock clock) {
        this.primary = primary;
        this.startupNote = startupNote;
        this.clock = clock;
    }

    /** Uses config/app.properties (or environment variables) to pick the provider. */
    public static AiAssistant createDefault() {
        AiServiceFactory.Selection selection = AiServiceFactory.select();
        return new AiAssistant(selection.primary(), selection.note(), Clock.systemDefaultZone());
    }

    public String describeProvider() {
        return primary == null ? offline.name() : primary.name() + " (offline fallback available)";
    }

    // ===============================================================
    // The six features
    // ===============================================================

    /** 1. Chatbot. */
    public AiResult<String> ask(User user, String question) throws AiException {
        String q = requireText(question, 500, "Question");
        AiContext ctx = AiContextBuilder.full(eventService, user, today());
        return run(service -> service.chat(q, ctx));
    }

    /** 2. Natural-language event creation: returns a PROPOSAL for the event form. */
    public AiResult<ParsedEvent> parseEvent(User user, String text) throws AiException {
        String t = requireText(text, 1000, "Text");
        AiContext ctx = AiContextBuilder.withoutEvents(user, today());
        return run(service -> service.parseEvent(t, ctx));
    }

    /** 3. Smart schedule suggestion. Our own code finds the free dates, the AI explains them. */
    public AiResult<String> suggestSchedule(User user, EventType type, Integer departmentId, Integer year,
                                            Integer semester, LocalDate from, LocalDate to) throws AiException {
        List<DateSuggestion> options = suggester.suggest(type, departmentId, year, semester, from, to, 5);
        if (options.isEmpty()) {
            return new AiResult<>("No free date was found between " + from + " and " + to
                    + ". Every day has a holiday, a weekly off day or a clash. Try a longer date range.",
                    offline.name(), false, null);
        }
        AiContext ctx = AiContextBuilder.withoutEvents(user, today());
        String request = type.getLabelEn() + " for " + ctx.departmentCode(departmentId)
                + (year != null ? ", year " + year : "") + (semester != null ? ", semester " + semester : "")
                + ", between " + from + " and " + to;
        return run(service -> service.suggestSchedule(request, options, ctx));
    }

    /** 4. Study planner for the user's own department, year and semester. */
    public AiResult<String> studyPlan(User user) throws AiException {
        LocalDate today = today();
        EventFilter filter = new EventFilter().from(today).to(today.plusDays(120));
        if (user.getDepartmentId() != null) {
            filter.department(user.getDepartmentId().intValue());
        }
        filter.year(user.getYear()).semester(user.getSemester());

        List<Event> upcoming = eventService.find(user, filter).stream()
                .filter(e -> e.getType() == EventType.CLASS_TEST || e.getType() == EventType.EXAM
                        || e.getType() == EventType.ASSIGNMENT_DEADLINE)
                .toList();
        if (upcoming.isEmpty()) {
            return new AiResult<>("There are no upcoming tests, exams or assignment deadlines in the next 120 days, "
                    + "so there is nothing to plan yet.", offline.name(), false, null);
        }
        AiContext ctx = AiContextBuilder.withoutEvents(user, today);
        return run(service -> service.studyPlan(upcoming, ctx));
    }

    /** 5. Weekly digest. departmentId == null means the whole university. */
    public AiResult<String> weeklyDigest(User user, Integer departmentId, LocalDate anyDayInWeek) throws AiException {
        LocalDate from = DateUtil.startOfWeek(Validator.requireNotNull(anyDayInWeek, "Date"));
        LocalDate to = from.plusDays(6);

        EventFilter filter = new EventFilter().from(from).to(to).department(departmentId);
        List<Event> events = eventService.find(user, filter);

        AiContext ctx = AiContextBuilder.withoutEvents(user, today());
        String scope = departmentId == null ? "the whole university" : ctx.departmentCode(departmentId) + " department";
        return run(service -> service.weeklyDigest(scope, events, from, to, ctx));
    }

    /** 6. Notice extractor. ADMIN only. Returns proposals; the admin approves each one in the UI. */
    public AiResult<List<ParsedEvent>> extractFromNotice(User user, String notice) throws AiException {
        if (user == null || !user.isAdmin()) {
            throw new PermissionDeniedException("Only an admin can import events from a notice.");
        }
        String text = requireText(notice, 8000, "Notice");
        AiContext ctx = AiContextBuilder.withoutEvents(user, today());
        return run(service -> service.extractFromNotice(text, ctx));
    }

    // ===============================================================
    // Internals
    // ===============================================================

    private <T> AiResult<T> run(AiCall<T> call) throws AiException {
        String warning = startupNote;

        if (primary != null) {
            long startedAt = System.currentTimeMillis();
            try {
                T value = call.run(primary);
                log.info("AI call with {} finished in {} ms", primary.name(), System.currentTimeMillis() - startedAt);
                return new AiResult<>(value, primary.name(), false, null);
            } catch (AiException e) {
                log.warn("AI provider {} failed ({}): {}", primary.name(), e.getKind(), e.getMessage());
                warning = e.getMessage() + " Showing the offline answer instead.";
            }
        }

        T value = call.run(offline); // may throw AiException (offline could not understand)
        return new AiResult<>(value, offline.name(), primary != null, warning);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String requireText(String text, int maxLength, String field) {
        String t = Validator.requireNotBlank(text, field);
        if (t.length() > maxLength) {
            throw new ValidationException(field + " is too long (maximum " + maxLength + " characters).");
        }
        return t;
    }
}
