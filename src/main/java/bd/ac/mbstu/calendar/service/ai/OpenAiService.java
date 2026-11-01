package bd.ac.mbstu.calendar.service.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** OpenAI "Chat Completions" API. */
public class OpenAiService extends LlmAiService {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public OpenAiService(String apiKey, String model, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "OpenAI (" + model + ")";
    }

    @Override
    protected String complete(String systemPrompt, String userPrompt, boolean expectJson) throws AiException {
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        if (expectJson) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            body.add("response_format", format);
        }

        String response = HttpAiClient.postJson(URL,
                Map.of("Authorization", "Bearer " + apiKey), body.toString(), timeoutSeconds);

        try {
            return JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, "The AI answer could not be read.", e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
