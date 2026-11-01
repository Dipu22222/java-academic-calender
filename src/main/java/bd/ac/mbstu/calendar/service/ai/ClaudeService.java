package bd.ac.mbstu.calendar.service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** Anthropic Claude "Messages" API. */
public class ClaudeService extends LlmAiService {

    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 2000;

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public ClaudeService(String apiKey, String model, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "Claude (" + model + ")";
    }

    @Override
    protected String complete(String systemPrompt, String userPrompt, boolean expectJson) throws AiException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", userPrompt);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_TOKENS);
        body.addProperty("system", systemPrompt);
        body.add("messages", messages);
        // Claude has no "JSON mode": our prompt asks for JSON and AiJson extracts it safely.

        String response = HttpAiClient.postJson(URL,
                Map.of("x-api-key", apiKey, "anthropic-version", API_VERSION),
                body.toString(), timeoutSeconds);

        try {
            JsonArray blocks = JsonParser.parseString(response).getAsJsonObject().getAsJsonArray("content");
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : blocks) {
                JsonObject block = el.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    sb.append(block.get("text").getAsString());
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, "The AI answer could not be read.", e);
        }
    }
}
