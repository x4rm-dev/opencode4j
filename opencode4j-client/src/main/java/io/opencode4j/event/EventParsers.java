package io.opencode4j.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.model.TokenUsage;
import io.opencode4j.model.form.FormField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps the {@code data} payload of each supported event type to its typed record. */
final class EventParsers {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final TypeReference<List<FormField>> FIELDS = new TypeReference<>() { };

    private EventParsers() {
    }

    /** Returns {@code null} for housekeeping events the caller should not see. */
    static OpenCodeEvent parse(String type, JsonNode data, String rawJson) {
        return switch (type) {
            case "server.connected", "server.heartbeat" -> null;
            case "session.created" -> new SessionCreated(
                    text(data, "sessionID"), text(data, "parentID"), text(data, "title"));
            case "session.text.delta" -> new TextDelta(
                    text(data, "sessionID"), text(data, "assistantMessageID"), integer(data, "ordinal"),
                    text(data, "delta"));
            case "session.reasoning.delta" -> new ReasoningDelta(
                    text(data, "sessionID"), text(data, "assistantMessageID"), integer(data, "ordinal"),
                    text(data, "delta"));
            case "session.tool.input.started" -> new ToolStarted(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "id"),
                    text(data, "name"));
            case "session.tool.called" -> new ToolCalled(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "id"),
                    object(data.get("input")));
            case "session.tool.success" -> new ToolSucceeded(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "id"),
                    contentText(data));
            case "session.tool.failed" -> new ToolFailed(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "id"),
                    errorMessage(data));
            case "session.step.ended" -> new StepEnded(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "finish"),
                    usage(data));
            case "session.step.failed" -> new StepFailed(
                    text(data, "sessionID"), text(data, "assistantMessageID"),
                    text(data.path("error"), "type"), errorMessage(data), usage(data));
            case "session.execution.started" -> new ExecutionStarted(text(data, "sessionID"));
            case "session.execution.succeeded" -> new ExecutionEnded(
                    text(data, "sessionID"), ExecutionOutcome.SUCCEEDED, null);
            case "session.execution.failed" -> new ExecutionEnded(
                    text(data, "sessionID"), ExecutionOutcome.FAILED, errorMessage(data));
            case "session.execution.interrupted" -> new ExecutionEnded(
                    text(data, "sessionID"), ExecutionOutcome.INTERRUPTED, text(data, "reason"));
            case "permission.asked" -> permissionAsked(data);
            case "form.created" -> formCreated(data, rawJson);
            default -> new UnknownEvent(type, rawJson);
        };
    }

    private static OpenCodeEvent permissionAsked(JsonNode data) {
        JsonNode request = data.path("request").isObject() ? data.get("request") : data;
        List<String> resources = new ArrayList<>();
        request.path("resources").forEach(resource -> resources.add(resource.asText()));
        String sessionId = text(request, "sessionID");
        return new PermissionAsked(
                text(request, "id"),
                sessionId != null ? sessionId : text(data, "sessionID"),
                text(request, "action"),
                resources,
                text(request, "message"));
    }

    private static OpenCodeEvent formCreated(JsonNode data, String rawJson) {
        JsonNode form = data.path("form").isObject() ? data.get("form") : data;
        List<FormField> fields;
        try {
            fields = MAPPER.convertValue(form.path("fields"), FIELDS);
        } catch (IllegalArgumentException exception) {
            return new UnknownEvent("parse_error_form.created", rawJson);
        }
        return new FormCreated(
                text(form, "id"),
                text(form, "sessionID"),
                text(form, "title"),
                text(form.path("metadata"), "kind"),
                fields);
    }

    private static TokenUsage usage(JsonNode data) {
        JsonNode tokens = data.path("tokens");
        if (!tokens.isObject()) {
            return TokenUsage.ZERO;
        }
        JsonNode cache = tokens.path("cache");
        return new TokenUsage(
                tokens.path("input").asLong(0),
                tokens.path("output").asLong(0),
                tokens.path("reasoning").asLong(0),
                cache.path("read").asLong(0),
                cache.path("write").asLong(0),
                data.path("cost").asDouble(0.0));
    }

    /** Joins the text parts of a tool result's {@code content} array. */
    private static String contentText(JsonNode data) {
        StringBuilder output = new StringBuilder();
        for (JsonNode part : data.path("content")) {
            String text = text(part, "text");
            if (text != null) {
                if (!output.isEmpty()) {
                    output.append('\n');
                }
                output.append(text);
            }
        }
        return output.toString();
    }

    /** Reads {@code error.message}, falling back to a top-level {@code message} or a bare {@code error} string. */
    private static String errorMessage(JsonNode data) {
        JsonNode error = data.path("error");
        if (error.isTextual()) {
            return error.asText();
        }
        String message = text(error, "message");
        return message != null ? message : text(data, "message");
    }

    private static Map<String, Object> object(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return MAPPER.convertValue(node, OBJECT);
    }

    private static int integer(JsonNode node, String field) {
        return node.path(field).asInt(0);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
