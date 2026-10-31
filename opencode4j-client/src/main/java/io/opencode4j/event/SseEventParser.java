package io.opencode4j.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Decodes server-sent events from {@code GET /api/event} into typed {@link OpenCodeEvent}s.
 *
 * <p>Each event is a JSON object {@code {"id", "created", "type", "location", "data", "durable"}} carried in
 * the {@code data} of one {@link SseFrame}. The parser is tolerant by design: an unrecognised type or
 * malformed payload becomes an {@link UnknownEvent} and never an exception, so one bad frame cannot kill a
 * long-running stream.</p>
 */
public final class SseEventParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATA_PREFIX = "data:";

    private SseEventParser() {
    }

    /**
     * Decodes one dispatched frame.
     *
     * @return the event, or {@code null} if the frame carries no consumer-visible event
     */
    public static OpenCodeEvent parse(SseFrame frame) {
        return frame == null ? null : parseJson(frame.data());
    }

    /**
     * Decodes one dispatched frame with its stream metadata.
     *
     * @return the envelope, or {@code null} if the frame carries no consumer-visible event
     */
    public static EventEnvelope parseEnvelope(SseFrame frame) {
        return frame == null ? null : parseEnvelopeJson(frame.data());
    }

    /**
     * Decodes one event payload.
     *
     * @return the event, or {@code null} if {@code json} is blank or a housekeeping event
     */
    public static OpenCodeEvent parseJson(String json) {
        EventEnvelope envelope = parseEnvelopeJson(json);
        return envelope == null ? null : envelope.event();
    }

    /**
     * Decodes one event payload with its stream metadata.
     *
     * @return the envelope, or {@code null} if {@code json} is blank or a housekeeping event
     */
    public static EventEnvelope parseEnvelopeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String payload = json.trim();
        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (JsonProcessingException exception) {
            return unknown("parse_error", payload);
        }
        if (root == null || !root.isObject()) {
            return unknown("parse_error", payload);
        }

        JsonNode type = root.get("type");
        if (type == null || !type.isTextual()) {
            return unknown("missing_type", payload);
        }
        OpenCodeEvent event = EventParsers.parse(type.asText(), root, payload);
        if (event == null) {
            return null;
        }
        JsonNode durable = root.path("durable");
        return new EventEnvelope(
                EventParsers.text(root, "id"),
                longOrNull(root.get("created")),
                EventParsers.text(root.path("location"), "directory"),
                EventParsers.text(durable, "aggregateID"),
                longOrNull(durable.get("seq")),
                event);
    }

    /**
     * Parses one raw SSE line, treating each {@code data:} line as a complete event.
     *
     * @return the event, or {@code null} for lines that carry none (blank lines, comments, {@code id:} /
     *         {@code event:} fields)
     * @deprecated a payload split across several {@code data:} lines is not reassembled; read frames with
     *             {@link io.opencode4j.client.SseStream#nextFrame()} and decode them with {@link #parse(SseFrame)}
     */
    @Deprecated(since = "0.2.0", forRemoval = true)
    public static OpenCodeEvent parseLine(String line) {
        if (line == null || !line.startsWith(DATA_PREFIX)) {
            return null;
        }
        return parseJson(line.substring(DATA_PREFIX.length()));
    }

    private static EventEnvelope unknown(String type, String payload) {
        return new EventEnvelope(null, null, null, null, null, new UnknownEvent(type, payload));
    }

    private static Long longOrNull(JsonNode node) {
        return node != null && node.canConvertToLong() ? node.asLong() : null;
    }
}
