package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.ScheduleSuggestionService.DateSuggestion;

import java.time.LocalDate;
import java.util.List;

/**
 * Base class for online AI providers (OpenAI, Gemini, Claude).
 * INHERITANCE + ABSTRACTION: this class contains everything the three providers
 * have in common (prompts, JSON parsing). Each provider only implements complete(),
 * which knows that provider's web API.
 */
public abstract class LlmAiService implements AiService {

    /**
     * Sends one request to the provider and returns the model's text.
     *
     * @param expectJson true when we need JSON back (event extraction)
     */
    protected abstract String complete(String systemPrompt, String userPrompt, boolean expectJson)
            throws AiException;

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public String chat(String question, AiContext ctx) throws AiException {
        return text(AiPrompts.system(ctx, true), AiPrompts.chatPrompt(question, ctx));
    }

    @Override
    public ParsedEvent parseEvent(String text, AiContext ctx) throws AiException {
        String system = AiPrompts.system(ctx, false) + AiPrompts.eventJsonRules(ctx, false);
        String raw = complete(system, AiPrompts.parseEventPrompt(text), true);
        return AiJson.toParsedEvent(AiJson.parseObject(raw));
    }

    @Override
    public String suggestSchedule(String request, List<DateSuggestion> options, AiContext ctx) throws AiException {
        return text(AiPrompts.system(ctx, true), AiPrompts.suggestionPrompt(request, options));
    }

    @Override
    public String studyPlan(List<Event> upcoming, AiContext ctx) throws AiException {
        return text(AiPrompts.system(ctx, true), AiPrompts.studyPlanPrompt(upcoming, ctx));
    }

    @Override
    public String weeklyDigest(String scopeLabel, List<Event> events, LocalDate from, LocalDate to,
                               AiContext ctx) throws AiException {
        return text(AiPrompts.system(ctx, true), AiPrompts.digestPrompt(scopeLabel, events, from, to, ctx));
    }

    @Override
    public List<ParsedEvent> extractFromNotice(String notice, AiContext ctx) throws AiException {
        String system = AiPrompts.system(ctx, false) + AiPrompts.eventJsonRules(ctx, true);
        String raw = complete(system, AiPrompts.noticePrompt(notice), true);
        return AiJson.toParsedEvents(AiJson.parseObject(raw));
    }

    /** complete() for plain-text answers, with an "empty answer" check. */
    private String text(String system, String user) throws AiException {
        String answer = complete(system, user, false);
        if (answer == null || answer.isBlank()) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, "The AI service returned an empty answer.");
        }
        return answer.trim();
    }
}
