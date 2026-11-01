package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.ScheduleSuggestionService.DateSuggestion;

import java.time.LocalDate;
import java.util.List;

/**
 * STRATEGY pattern: every AI provider implements these six features.
 * The rest of the app only knows this interface, never a concrete provider.
 */
public interface AiService {

    /** Name to show in the UI, e.g. "Claude (claude-haiku-5-5)". */
    String name();

    /** True if this provider uses the internet. */
    boolean isOnline();

    /** 1. Chatbot: answer a question using the events in the context. */
    String chat(String question, AiContext ctx) throws AiException;

    /** 2. Natural-language event creation: "Add ICT-2207 class test on 15 Nov at 10am". */
    ParsedEvent parseEvent(String text, AiContext ctx) throws AiException;

    /** 3. Explain the best dates. The dates were already found and checked by our own code. */
    String suggestSchedule(String request, List<DateSuggestion> options, AiContext ctx) throws AiException;

    /** 4. Week-by-week study plan for the given upcoming tests, exams and deadlines. */
    String studyPlan(List<Event> upcoming, AiContext ctx) throws AiException;

    /** 5. Short weekly digest of the events between from and to. */
    String weeklyDigest(String scopeLabel, List<Event> events, LocalDate from, LocalDate to,
                        AiContext ctx) throws AiException;

    /** 6. Find dated events inside a pasted notice. Returns PROPOSALS only. */
    List<ParsedEvent> extractFromNotice(String notice, AiContext ctx) throws AiException;
}
