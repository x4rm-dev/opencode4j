package io.opencode4j.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses server-sent-event lines from {@code GET /api/event} into typed {@link OpenCodeEvent}s.
 *
 * <p>Each event is a JSON object {@code {"id", "created", "type", "data", ...}} on a {@code data:} line.
 * The parser is tolerant by design: an unrecognised type or malformed line becomes an
 * {@link UnknownEvent} and never an exception, so one bad line cannot kill a long-running stream.</p>
 */
public final class SseEventParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATA_PREFIX = "data:";

    private SseEventParser() {
    }

    /**
     * Parses one raw SSE line.
     *
     * @return the event, or {@code null} for lines that carry none (blank lines, comments, {@code id:} /
     *         {@code event:} fields, and housekeeping events such as {@code server.connected})
     */
    public static OpenCodeEvent parseLine(String line) {
        if (line == null || !line.startsWith(DATA_PREFIX)) {
            return null;
        }
        String json = line.substring(DATA_PREFIX.length()).trim();
        if (json.isEmpty()) {
            return null;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            return new UnknownEvent("parse_error", json);
        }

        JsonNode type = root.get("type");
        if (type == null || !type.isTextual()) {
            return new UnknownEvent("missing_type", json);
        }
        return EventParsers.parse(type.asText(), root.path("data"), json);
    }
}
