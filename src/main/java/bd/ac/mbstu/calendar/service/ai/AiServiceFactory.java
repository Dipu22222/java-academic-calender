package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FACTORY: reads config/app.properties (or environment variables) and creates
 * the provider you chose. Returns null for "offline".
 */
public final class AiServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AiServiceFactory.class);

    /** primary = the online provider (null = offline only). note = a message to show the user (or null). */
    public record Selection(AiService primary, String note) {
    }

    private AiServiceFactory() {
    }

    public static Selection select() {
        String provider = Config.get("ai.provider", "offline").toLowerCase();
        int timeout = Config.getInt("ai.timeout.seconds", 20);

        switch (provider) {
            case "openai": {
                String key = Config.get("openai.api.key", "");
                if (key.isBlank()) {
                    return missingKey("OpenAI");
                }
                return new Selection(new OpenAiService(key, Config.get("openai.model", "gpt-4o-mini"), timeout), null);
            }
            case "gemini": {
                String key = Config.get("gemini.api.key", "");
                if (key.isBlank()) {
                    return missingKey("Gemini");
                }
                return new Selection(new GeminiService(key, Config.get("gemini.model", "gemini-2.0-flash"), timeout), null);
            }
            case "claude": {
                String key = Config.get("claude.api.key", "");
                if (key.isBlank()) {
                    key = firstNonBlank(System.getenv("ANTHROPIC_API_KEY"));
                }
                if (key.isBlank()) {
                    return missingKey("Claude");
                }
                return new Selection(new ClaudeService(key, Config.get("claude.model", "claude-haiku-5-5"), timeout), null);
            }
            case "offline":
                return new Selection(null, null);
            default:
                log.warn("Unknown ai.provider '{}'. Using offline mode.", provider);
                return new Selection(null, "Unknown AI provider \"" + provider + "\". Using offline mode.");
        }
    }

    private static Selection missingKey(String provider) {
        log.warn("{} was selected but no API key was found. Using offline mode.", provider);
        return new Selection(null, provider + " is selected but no API key was found. Using offline mode.");
    }

    private static String firstNonBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
