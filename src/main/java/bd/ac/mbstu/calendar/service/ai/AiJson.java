package bd.ac.mbstu.calendar.service.ai;

import bd.ac.mbstu.calendar.model.EventType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the AI's JSON text into ParsedEvent objects, defensively:
 * AI models sometimes add Markdown fences, "null" strings or wrong formats,
 * so we never assume the JSON is perfect.
 */
final class AiJson {

    private AiJson() {
    }

    /** Finds the first { ... last } in the text (ignores ```json fences and chatter). */
    static JsonObject parseObject(String raw) throws AiException {
        if (raw != null) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return JsonParser.parseString(raw.substring(start, end + 1)).getAsJsonObject();
                } catch (JsonParseException | IllegalStateException e) {
                    throw new AiException(AiException.Kind.BAD_RESPONSE,
                            "The AI answer was not in the expected format.", e);
                }
            }
        }
        throw new AiException(AiException.Kind.BAD_RESPONSE, "The AI answer was not in the expected format.");
    }

    /** {"events": [ ... ]} to a list. Items without a valid date are skipped. */
    static List<ParsedEvent> toParsedEvents(JsonObject root) {
        List<ParsedEvent> result = new ArrayList<>();
        JsonElement events = root.get("events");
        if (events == null || !events.isJsonArray()) {
            return result;
        }
        for (JsonElement item : events.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            try {
                result.add(toParsedEvent(item.getAsJsonObject()));
            } catch (AiException skipThisItem) {
                // one bad item must not lose the good ones
            }
        }
        return result;
    }

    static ParsedEvent toParsedEvent(JsonObject o) throws AiException {
        List<String> warnings = new ArrayList<>(readWarnings(o));

        EventType type = EventType.fromString(str(o, "type"));
        if (type == null) {
            type = EventType.NOTICE;
            warnings.add("The event type was unclear. Please choose it.");
        }

        LocalDate start = parseDate(str(o, "startDate"));
        if (start == null) {
            throw new AiException(AiException.Kind.BAD_RESPONSE,
                    "No valid date was found. Please include a date such as 15 Nov.");
        }
        LocalDate end = parseDate(str(o, "endDate"));
        if (end == null) {
            end = start;
        } else if (end.isBefore(start)) {
            end = start;
            warnings.add("The end date was before the start date, so it was set to the start date.");
        }

        LocalTime startTime = parseTime(str(o, "startTime"));
        LocalTime endTime = parseTime(str(o, "endTime"));
        if (startTime == null && endTime != null) {
            endTime = null;
        }
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            endTime = null;
            warnings.add("The end time was not after the start time and was removed.");
        }

        String title = str(o, "title");
        if (title == null) {
            title = type.getLabelEn();
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        return new ParsedEvent(title, type, start, end, startTime, endTime,
                str(o, "department"), intOrNull(o, "year"), intOrNull(o, "semester"),
                str(o, "courseCode"), str(o, "location"), str(o, "description"), warnings);
    }

    // ---------------------------------------------------------------

    /** Reads a text field. JSON null, "null" and blank all become Java null. */
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        String s = el.getAsString().trim();
        return (s.isEmpty() || s.equalsIgnoreCase("null")) ? null : s;
    }

    private static Integer intOrNull(JsonObject o, String key) {
        String s = str(o, key);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> readWarnings(JsonObject o) {
        List<String> list = new ArrayList<>();
        JsonElement el = o.get("warnings");
        if (el != null && el.isJsonArray()) {
            JsonArray array = el.getAsJsonArray();
            for (JsonElement w : array) {
                if (list.size() >= 5) {
                    break;
                }
                if (w.isJsonPrimitive()) {
                    String text = w.getAsString().trim();
                    if (!text.isEmpty()) {
                        list.add(text.length() > 200 ? text.substring(0, 200) : text);
                    }
                }
            }
        }
        return list;
    }
}
