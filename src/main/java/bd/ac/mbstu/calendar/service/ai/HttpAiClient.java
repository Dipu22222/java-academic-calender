package bd.ac.mbstu.calendar.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Sends one JSON POST request and returns the response text.
 * Every failure becomes an AiException with a message the user can understand.
 * We never log the API key or the request body.
 */
final class HttpAiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiClient.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpAiClient() {
    }

    static String postJson(String url, Map<String, String> headers, String jsonBody, int timeoutSeconds)
            throws AiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(builder::header);

        try {
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return checkStatus(response);
        } catch (HttpConnectTimeoutException e) {
            throw new AiException(AiException.Kind.NO_INTERNET,
                    "Could not connect to the AI service. Please check your internet connection.", e);
        } catch (HttpTimeoutException e) {
            throw new AiException(AiException.Kind.TIMEOUT,
                    "The AI service took too long to answer.", e);
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NO_INTERNET,
                    "Network problem while contacting the AI service. Please check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice: keep the "interrupted" flag
            throw new AiException(AiException.Kind.CANCELLED, "The AI request was cancelled.", e);
        }
    }

    private static String checkStatus(HttpResponse<String> response) throws AiException {
        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return response.body();
        }
        log.warn("AI service returned HTTP {}", code); // status only, never the body

        if (code == 401 || code == 403) {
            throw new AiException(AiException.Kind.AUTH,
                    "The AI service rejected the API key. Check it in config/app.properties.");
        }
        if (code == 429) {
            throw new AiException(AiException.Kind.RATE_LIMIT,
                    "The AI usage limit or quota was reached. Please try again later.");
        }
        if (code >= 500) {
            throw new AiException(AiException.Kind.SERVER,
                    "The AI service has a problem right now (HTTP " + code + ").");
        }
        throw new AiException(AiException.Kind.BAD_RESPONSE,
                "The AI service refused the request (HTTP " + code + "). Check the model name and API key in the config.");
    }
}
