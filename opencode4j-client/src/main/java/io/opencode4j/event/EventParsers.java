package io.opencode4j.event;

import io.opencode4j.model.ErrorInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.model.ModelRef;
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
    static OpenCodeEvent parse(String type, JsonNode root, String rawJson) {
        JsonNode data = root.path("data");
        return switch (type) {
            case "server.connected" -> new ServerConnected();
            // OpenCode 2.0.x sent heartbeats as events; 2.2 sends SSE comments. Neither is for consumers.
            case "server.heartbeat" -> null;
            case "session.deleted" -> new SessionDeleted(text(data, "sessionID"));
            case "session.renamed" -> new SessionRenamed(text(data, "sessionID"), text(data, "title"));
            case "session.metadata.updated" -> new SessionMetadataUpdated(
                    text(data, "sessionID"), object(data.get("metadata")));
            case "session.usage.updated" -> new SessionUsageUpdated(text(data, "sessionID"), usage(data));
            case "session.idle" -> new SessionIdle(text(data, "sessionID"));
            case "session.status" -> sessionStatus(data);
            case "session.retry.scheduled" -> new RetryScheduled(
                    text(data, "sessionID"), text(data, "assistantMessageID"), integer(data, "attempt"),
                    data.path("at").asLong(0), errorInfo(data.get("error")));
            case "mcp.status.changed" -> new McpStatusChanged(text(data, "server"));
            case "session.step.started" -> new StepStarted(
                    text(data, "sessionID"), text(data, "assistantMessageID"), text(data, "agent"),
                    modelRef(data.get("model")));
            case "session.text.ended" -> new TextEnded(
                    text(data, "sessionID"), text(data, "assistantMessageID"), integer(data, "ordinal"),
                    text(data, "text"));
            case "session.compaction.started" -> new CompactionStarted(
                    text(data, "sessionID"), text(data, "reason"), text(data, "inputID"));
            case "session.compaction.ended" -> new CompactionEnded(
                    text(data, "sessionID"), text(data, "reason"), text(data, "text"), usage(data));
            case "session.compaction.failed" -> new CompactionFailed(
                    text(data, "sessionID"), text(data, "reason"), text(data, "inputID"),
                    errorInfo(data.get("error")), usage(data));
            case "permission.replied" -> new PermissionReplied(
                    text(data, "sessionID"), text(data, "requestID"), text(data, "reply"));
            case "location.shutdown" -> new LocationShutdown(text(root.path("location"), "directory"));
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
        if (message == null) {
            return text(data, "message");
        }
        // Provider failures carry an HTTP status; keep it (and the error type) so callers can tell an
        // expired credential ("provider.auth", HTTP 401) from other failures. Statusless errors such as
        // "aborted" keep their plain message.
        JsonNode status = error.get("status");
        if (status == null || status.isNull()) {
            return message;
        }
        String type = text(error, "type");
        String detail = (type != null ? type + ", " : "") + "HTTP " + status.asText();
        return message + " [" + detail + "]";
    }

    private static OpenCodeEvent sessionStatus(JsonNode data) {
        JsonNode status = data.path("status");
        boolean retry = "retry".equals(text(status, "type"));
        return new SessionStatusChanged(
                text(data, "sessionID"),
                text(status, "type"),
                retry && status.has("attempt") ? status.get("attempt").asInt() : null,
                retry ? text(status, "message") : null,
                retry && status.has("next") ? status.get("next").asLong() : null);
    }

    /** Reads a {@code Session.StructuredError}, or {@code null} if absent. */
    static ErrorInfo errorInfo(JsonNode error) {
        if (error == null || error.isNull() || error.isMissingNode()) {
            return null;
        }
        if (error.isTextual()) {
            return new ErrorInfo(null, error.asText(), null, null);
        }
        JsonNode status = error.get("status");
        JsonNode retryAfter = error.get("retryAfterMs");
        return new ErrorInfo(
                text(error, "type"),
                text(error, "message"),
                status != null && status.canConvertToInt() ? status.asInt() : null,
                retryAfter != null && retryAfter.canConvertToLong() ? retryAfter.asLong() : null);
    }

    private static ModelRef modelRef(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String id = text(node, "id");
        String provider = text(node, "providerID");
        return id == null || provider == null ? null : new ModelRef(id, provider, text(node, "variant"));
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

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
