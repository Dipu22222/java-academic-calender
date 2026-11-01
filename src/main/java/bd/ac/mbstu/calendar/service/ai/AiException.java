package bd.ac.mbstu.calendar.service.ai;

/**
 * Anything that can go wrong when talking to an AI provider.
 * getMessage() is a friendly sentence that can be shown to the user directly.
 * It is a CHECKED exception, so the compiler forces us to handle it.
 */
public class AiException extends Exception {

    public enum Kind {
        NOT_CONFIGURED,  // no API key
        NO_INTERNET,     // cannot reach the provider
        TIMEOUT,         // provider too slow
        AUTH,            // API key rejected
        RATE_LIMIT,      // quota or rate limit reached
        SERVER,          // provider has a problem (5xx)
        BAD_RESPONSE,    // answer missing or not in the expected format
        UNDERSTAND,      // offline mode could not understand the text
        CANCELLED        // the thread was interrupted
    }

    private final Kind kind;

    public AiException(Kind kind, String userMessage) {
        super(userMessage);
        this.kind = kind;
    }

    public AiException(Kind kind, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
