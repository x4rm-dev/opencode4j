package io.opencode4j.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opencode4j.model.form.FormOption;
import io.opencode4j.model.form.StringField;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parser tests. The payloads are real events captured from an OpenCode 2.0.20 server, shortened only by
 * removing bulky provider state, except {@code permission.asked}, which is built from the server schema.
 */
class SseEventParserTest {

    private static final String SESSION = "ses_f0c408fe8ffebMS4ePmP1sR7mR";
    private static final String MESSAGE = "msg_0f3bf7256001eZDCLC05FQ4M51";

    @Test
    void ignoresLinesThatCarryNoEvent() {
        assertNull(SseEventParser.parseLine(null));
        assertNull(SseEventParser.parseLine(""));
        assertNull(SseEventParser.parseLine(":heartbeat"));
        assertNull(SseEventParser.parseLine("id: 7"));
        assertNull(SseEventParser.parseLine("event: message"));
        assertNull(SseEventParser.parseLine("data:"));
    }

    @Test
    void dropsHousekeepingEvents() {
        assertNull(SseEventParser.parseLine(line("server.connected", "{}")));
        assertNull(SseEventParser.parseLine(line("server.heartbeat", "{}")));
    }

    @Test
    void parsesTextDelta() {
        // Given
        String data = "{\"sessionID\":\"" + SESSION + "\",\"assistantMessageID\":\"" + MESSAGE
                + "\",\"ordinal\":0,\"delta\":\"pong\"}";

        // When
        TextDelta delta = assertInstanceOf(TextDelta.class, SseEventParser.parseLine(line("session.text.delta", data)));

        // Then
        assertEquals(SESSION, delta.sessionId());
        assertEquals(MESSAGE, delta.messageId());
        assertEquals(0, delta.ordinal());
        assertEquals("pong", delta.delta());
        assertEquals("session.text.delta", delta.type());
    }

    @Test
    void parsesReasoningDelta() {
        // Given
        String data = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"ordinal\":1,\"delta\":\"hmm\"}";

        // When
        ReasoningDelta delta = assertInstanceOf(
                ReasoningDelta.class, SseEventParser.parseLine(line("session.reasoning.delta", data)));

        // Then
        assertEquals("hmm", delta.delta());
        assertEquals(1, delta.ordinal());
    }

    @Test
    void parsesAToolCallLifecycle() {
        // Given
        String started = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"id\":\"call_1\",\"name\":\"shell\"}";
        String called = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"id\":\"call_1\","
                + "\"input\":{\"command\":\"echo hi\",\"timeout\":null},\"executed\":false}";
        String succeeded = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"id\":\"call_1\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi\\n\"},{\"type\":\"text\",\"text\":\"done\"}],"
                + "\"metadata\":{\"exit\":0},\"executed\":false}";
        String failed = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"id\":\"call_1\","
                + "\"error\":{\"type\":\"aborted\",\"message\":\"Tool execution interrupted\"},\"executed\":false}";

        // When
        ToolStarted toolStarted = assertInstanceOf(
                ToolStarted.class, SseEventParser.parseLine(line("session.tool.input.started", started)));
        ToolCalled toolCalled = assertInstanceOf(
                ToolCalled.class, SseEventParser.parseLine(line("session.tool.called", called)));
        ToolSucceeded toolSucceeded = assertInstanceOf(
                ToolSucceeded.class, SseEventParser.parseLine(line("session.tool.success", succeeded)));
        ToolFailed toolFailed = assertInstanceOf(
                ToolFailed.class, SseEventParser.parseLine(line("session.tool.failed", failed)));

        // Then
        assertEquals("call_1", toolStarted.callId());
        assertEquals("shell", toolStarted.name());
        assertEquals("echo hi", toolCalled.input().get("command"));
        assertEquals(true, toolCalled.input().containsKey("timeout"));
        assertEquals("hi\n\ndone", toolSucceeded.output());
        assertEquals("Tool execution interrupted", toolFailed.error());
    }

    @Test
    void parsesStepEndedWithTokensAndCost() {
        // Given
        String data = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\",\"finish\":\"tool-calls\","
                + "\"cost\":0.0008874,\"tokens\":{\"input\":27,\"output\":23,\"reasoning\":4,"
                + "\"cache\":{\"read\":6034,\"write\":7}},\"snapshot\":\"abc\",\"files\":[]}";

        // When
        StepEnded step = assertInstanceOf(StepEnded.class, SseEventParser.parseLine(line("session.step.ended", data)));

        // Then
        assertEquals("tool-calls", step.finish());
        assertEquals(27, step.usage().input());
        assertEquals(23, step.usage().output());
        assertEquals(4, step.usage().reasoning());
        assertEquals(6034, step.usage().cacheRead());
        assertEquals(7, step.usage().cacheWrite());
        assertEquals(0.0008874, step.usage().cost());
    }

    @Test
    void parsesStepFailedWithItsError() {
        // Given
        String data = "{\"sessionID\":\"s\",\"assistantMessageID\":\"m\","
                + "\"error\":{\"type\":\"aborted\",\"message\":\"Step interrupted\"},\"cost\":0.001,"
                + "\"tokens\":{\"input\":28,\"output\":52,\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}}}";

        // When
        StepFailed step = assertInstanceOf(StepFailed.class, SseEventParser.parseLine(line("session.step.failed", data)));

        // Then
        assertEquals("aborted", step.errorType());
        assertEquals("Step interrupted", step.message());
        assertEquals(52, step.usage().output());
    }

    @Test
    void parsesEveryExecutionOutcome() {
        // When
        ExecutionStarted started = assertInstanceOf(ExecutionStarted.class,
                SseEventParser.parseLine(line("session.execution.started", "{\"sessionID\":\"s\"}")));
        ExecutionEnded succeeded = assertInstanceOf(ExecutionEnded.class,
                SseEventParser.parseLine(line("session.execution.succeeded", "{\"sessionID\":\"s\"}")));
        ExecutionEnded failed = assertInstanceOf(ExecutionEnded.class, SseEventParser.parseLine(line(
                "session.execution.failed", "{\"sessionID\":\"s\",\"error\":{\"message\":\"HTTP 403\"}}")));
        ExecutionEnded interrupted = assertInstanceOf(ExecutionEnded.class, SseEventParser.parseLine(line(
                "session.execution.interrupted", "{\"sessionID\":\"s\",\"reason\":\"user\"}")));

        // Then
        assertEquals("s", started.sessionId());
        assertEquals(ExecutionOutcome.SUCCEEDED, succeeded.outcome());
        assertEquals("session.execution.succeeded", succeeded.type());
        assertEquals(ExecutionOutcome.FAILED, failed.outcome());
        assertEquals("HTTP 403", failed.detail());
        assertEquals(ExecutionOutcome.INTERRUPTED, interrupted.outcome());
        assertEquals("user", interrupted.detail());
    }

    @Test
    void parsesAQuestionFormWithItsFields() {
        // Given
        String data = "{\"form\":{\"id\":\"frm_1\",\"sessionID\":\"s\",\"title\":\"Questions\","
                + "\"metadata\":{\"kind\":\"question\",\"tool\":{\"messageID\":\"m\",\"id\":\"call_1\"}},"
                + "\"fields\":[{\"key\":\"q0\",\"title\":\"Choose\",\"description\":\"Choose A or B.\","
                + "\"type\":\"string\",\"options\":[{\"value\":\"A\",\"label\":\"A\"},"
                + "{\"value\":\"B\",\"label\":\"B\"}],\"custom\":true}]}}";

        // When
        FormCreated form = assertInstanceOf(FormCreated.class, SseEventParser.parseLine(line("form.created", data)));

        // Then
        assertEquals("frm_1", form.id());
        assertEquals("s", form.sessionId());
        assertEquals("question", form.kind());
        StringField field = assertInstanceOf(StringField.class, form.fields().getFirst());
        assertEquals("q0", field.key());
        assertEquals(List.of("A", "B"), field.options().stream().map(FormOption::value).toList());
    }

    @Test
    void parsesPermissionRequestsFromEitherLayout() {
        // Given
        String flat = "{\"id\":\"per_1\",\"sessionID\":\"s\",\"action\":\"bash\","
                + "\"resources\":[\"ls -la\"],\"message\":\"Run ls\"}";
        String wrapped = "{\"request\":" + flat + "}";

        // When
        PermissionAsked fromFlat = assertInstanceOf(
                PermissionAsked.class, SseEventParser.parseLine(line("permission.asked", flat)));
        PermissionAsked fromWrapped = assertInstanceOf(
                PermissionAsked.class, SseEventParser.parseLine(line("permission.asked", wrapped)));

        // Then
        assertEquals(fromFlat, fromWrapped);
        assertEquals("per_1", fromFlat.id());
        assertEquals("s", fromFlat.sessionId());
        assertEquals(List.of("ls -la"), fromFlat.resources());
        assertEquals("bash", fromFlat.action());
    }

    @Test
    void parsesSessionCreatedIncludingParent() {
        // Given
        String data = "{\"sessionID\":\"" + SESSION + "\",\"parentID\":\"ses_parent\",\"slug\":\"misty-falcon\","
                + "\"version\":\"2.0.20\",\"title\":\"opencode4j-probe\"}";

        // When
        SessionCreated created = assertInstanceOf(
                SessionCreated.class, SseEventParser.parseLine(line("session.created", data)));

        // Then
        assertEquals(SESSION, created.sessionId());
        assertEquals("ses_parent", created.parentId());
        assertEquals("opencode4j-probe", created.title());
    }

    @Test
    void unmodelledTypesBecomeUnknownEventsCarryingTheRawJson() {
        // Given
        String raw = line("mcp.status.changed", "{\"server\":\"codebase-memory-mcp\"}");

        // When
        UnknownEvent event = assertInstanceOf(UnknownEvent.class, SseEventParser.parseLine(raw));

        // Then
        assertEquals("mcp.status.changed", event.type());
        assertEquals(true, event.rawJson().contains("codebase-memory-mcp"));
    }

    @Test
    void malformedJsonBecomesAnUnknownEventNotAnException() {
        UnknownEvent event = assertInstanceOf(UnknownEvent.class, SseEventParser.parseLine("data: {not json"));
        assertEquals("parse_error", event.type());
    }

    @Test
    void objectsWithoutATypeBecomeUnknownEvents() {
        UnknownEvent event = assertInstanceOf(UnknownEvent.class, SseEventParser.parseLine("data: {\"data\":{}}"));
        assertEquals("missing_type", event.type());
    }

    @Test
    void toleratesMissingFieldsInsteadOfThrowing() {
        // A newer server may omit fields; the parser must still produce an event.
        TextDelta delta = assertInstanceOf(TextDelta.class,
                SseEventParser.parseLine(line("session.text.delta", "{}")));
        assertNull(delta.sessionId());
        assertEquals(Map.of(), assertInstanceOf(ToolCalled.class,
                SseEventParser.parseLine(line("session.tool.called", "{}"))).input());
    }

    /** Builds an SSE data line in the v2 envelope shape. */
    private static String line(String type, String data) {
        return "data: {\"id\":\"evt_1\",\"created\":1790795805234,\"type\":\"" + type + "\",\"data\":" + data + "}";
    }
}
