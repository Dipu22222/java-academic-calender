package bd.ac.mbstu.calendar.service.ai;

/**
 * What the UI receives from AiAssistant.
 *
 * @param value            the answer (text, parsed event, list...)
 * @param provider         who produced it, e.g. "OpenAI (gpt-4o-mini)" or the offline assistant
 * @param offlineFallback  true if the online provider failed and offline mode answered instead
 * @param warning          a message to show the user (or null), e.g. "No internet. Showing the offline answer."
 */
public record AiResult<T>(T value, String provider, boolean offlineFallback, String warning) {
}
