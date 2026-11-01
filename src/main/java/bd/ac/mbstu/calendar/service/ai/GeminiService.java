package bd.ac.mbstu.calendar.service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** Google Gemini "generateContent" API. */
public class GeminiService extends LlmAiService {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public GeminiService(String apiKey, String model, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "Gemini (" + model + ")";
    }

    @Override
    protected String complete(String systemPrompt, String userPrompt, boolean expectJson) throws AiException {
        JsonObject body = new JsonObject();
        body.add("systemInstruction", content(null, systemPrompt));
        JsonArray contents = new JsonArray();
        contents.add(content("user", userPrompt));
        body.add("contents", contents);
        if (expectJson) {
            JsonObject config = new JsonObject();
            config.addProperty("responseMimeType", "application/json");
            body.add("generationConfig", config);
        }

        String response = HttpAiClient.postJson(BASE_URL + model + ":generateContent",
                Map.of("x-goog-api-key", apiKey), body.toString(), timeoutSeconds);

        try {
            JsonArray parts = JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : parts) {
                JsonElement text = part.getAsJsonObject().get("text");
                if (text != null) {
                    sb.append(text.getAsString());
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            // Typical cause: the answer was blocked by a safety filter, so there is no "candidates".
            throw new AiException(AiException.Kind.BAD_RESPONSE,
                    "The AI did not return an answer (it may have been blocked).", e);
        }
    }

    private static JsonObject content(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        if (role != null) {
            content.addProperty("role", role);
        }
        content.add("parts", parts);
        return content;
    }
}
