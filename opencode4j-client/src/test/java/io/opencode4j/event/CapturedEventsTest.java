package io.opencode4j.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Decodes events captured from a live OpenCode 2.2.0 server ({@code src/test/resources/fixtures/opencode-2.2.0}).
 *
 * <p>The capture drove a real server with a stub OpenAI-compatible model: a text turn, a rename, a shell
 * tool call answered with a rejected permission, a manual compaction, a session delete and a location
 * eviction. Workspace paths were replaced with {@code /work}. Events the capture could not trigger are built
 * from the server's event schema in {@code packages/schema/src} and marked as such.</p>
 */
class CapturedEventsTest {

    private static final String SESSION = "ses_e6d020593ffe4q46YQ96agDiQ0";

    /** Captured types that this library deliberately leaves as {@link UnknownEvent}. */
    private static final Set<String> UNMODELED = Set.of(
            "session.compaction.delta", "session.inbox.enqueued", "session.text.started");

    private static final List<String> CAPTURED = List.of(
            "location.shutdown", "permission.asked", "permission.replied", "server.connected",
            "session.compaction.delta", "session.compaction.failed", "session.compaction.started",
            "session.created", "session.deleted", "session.execution.interrupted", "session.execution.started",
            "session.execution.succeeded", "session.inbox.enqueued", "session.metadata.updated",
            "session.renamed", "session.step.ended", "session.step.failed", "session.step.started",
            "session.text.delta", "session.text.ended", "session.text.started", "session.tool.called",
            "session.tool.failed", "session.tool.input.started", "session.usage.updated");

    @Test
    void everyCapturedEventDecodesToItsTypedRecord() throws IOException {
        for (String type : CAPTURED) {
            // When
            EventEnvelope envelope = SseEventParser.parseEnvelopeJson(fixture(type));

            // Then
            assertNotNull(envelope, type);
            assertEquals(type, envelope.type(), type);
            assertEquals(UNMODELED.contains(type), envelope.event() instanceof UnknownEvent, type);
            assertNotNull(envelope.id(), type);
        }
    }

    @Test
    void envelopeCarriesDurableSequenceAndLocation() throws IOException {
        // When
        EventEnvelope ended = SseEventParser.parseEnvelopeJson(fixture("session.step.ended"));
        EventEnvelope delta = SseEventParser.parseEnvelopeJson(fixture("session.text.delta"));

        // Then
        assertTrue(ended.durable());
        assertEquals(SESSION, ended.aggregateId());
        assertNotNull(ended.seq());
        assertEquals("/work", ended.directory());
        assertNotNull(ended.created());
        assertFalse(delta.durable(), "text deltas are transient");
    }

    @Test
    void decodesSessionLifecycleEvents() throws IOException {
        SessionCreated created = assertInstanceOf(SessionCreated.class, event("session.created"));
        SessionRenamed renamed = assertInstanceOf(SessionRenamed.class, event("session.renamed"));
        SessionMetadataUpdated metadata = assertInstanceOf(SessionMetadataUpdated.class,
                event("session.metadata.updated"));
        SessionDeleted deleted = assertInstanceOf(SessionDeleted.class, event("session.deleted"));
        LocationShutdown shutdown = assertInstanceOf(LocationShutdown.class, event("location.shutdown"));

        assertEquals(SESSION, created.sessionId());
        assertNull(created.parentId());
        assertEquals("capture", created.title());
        assertEquals("renamed", renamed.title());
        assertEquals(Map.of("releaseId", "r1"), metadata.metadata());
        assertEquals(SESSION, deleted.sessionId());
        assertEquals("/work", shutdown.directory());
        assertInstanceOf(ServerConnected.class, event("server.connected"));
    }

    @Test
    void decodesStepAndTextEvents() throws IOException {
        StepStarted started = assertInstanceOf(StepStarted.class, event("session.step.started"));
        TextEnded text = assertInstanceOf(TextEnded.class, event("session.text.ended"));
        SessionUsageUpdated usage = assertInstanceOf(SessionUsageUpdated.class, event("session.usage.updated"));
        StepFailed failed = assertInstanceOf(StepFailed.class, event("session.step.failed"));

        assertEquals("build", started.agent());
        assertEquals("fake", started.model().providerID());
        assertEquals("fake-1", started.model().id());
        assertEquals("pong", text.text());
        assertEquals(0, text.ordinal());
        assertEquals(11, usage.usage().input());
        assertEquals("aborted", failed.errorType());
    }

    @Test
    void decodesPermissionRoundTrip() throws IOException {
        PermissionAsked asked = assertInstanceOf(PermissionAsked.class, event("permission.asked"));
        PermissionReplied replied = assertInstanceOf(PermissionReplied.class, event("permission.replied"));
        ToolFailed declined = assertInstanceOf(ToolFailed.class, event("session.tool.failed"));
        ExecutionEnded interrupted = assertInstanceOf(ExecutionEnded.class, event("session.execution.interrupted"));

        assertEquals("shell", asked.action());
        assertEquals(List.of("echo hi"), asked.resources());
        assertEquals(asked.id(), replied.requestId());
        assertEquals("reject", replied.reply());
        assertEquals("The user declined this tool call", declined.error());
        // A rejected permission ends the whole execution, not just the tool call.
        assertEquals(ExecutionOutcome.INTERRUPTED, interrupted.outcome());
        assertEquals("shutdown", interrupted.detail());
    }

    @Test
    void decodesCompactionEvents() throws IOException {
        CompactionStarted started = assertInstanceOf(CompactionStarted.class, event("session.compaction.started"));
        CompactionFailed failed = assertInstanceOf(CompactionFailed.class, event("session.compaction.failed"));

        assertEquals("manual", started.reason());
        assertEquals(started.inputId(), failed.inputId());
        assertEquals("compaction.failed", failed.error().type());
        assertEquals(22, failed.usage().input());
    }

    @Test
    void decodesEventsBuiltFromTheServerSchema() {
        // Built from packages/schema/src/session-event.ts, session-status-event.ts and mcp-event.ts.
        CompactionEnded ended = assertInstanceOf(CompactionEnded.class, SseEventParser.parseJson(
                "{\"type\":\"session.compaction.ended\",\"data\":{\"sessionID\":\"s\",\"reason\":\"auto\","
                        + "\"text\":\"summary\",\"cost\":0.5,\"tokens\":{\"input\":10,\"output\":2,"
                        + "\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}}}}"));
        SessionStatusChanged retry = assertInstanceOf(SessionStatusChanged.class, SseEventParser.parseJson(
                "{\"type\":\"session.status\",\"data\":{\"sessionID\":\"s\",\"status\":{\"type\":\"retry\","
                        + "\"attempt\":2,\"message\":\"rate limited\",\"next\":1700000000000}}}"));
        SessionStatusChanged busy = assertInstanceOf(SessionStatusChanged.class, SseEventParser.parseJson(
                "{\"type\":\"session.status\",\"data\":{\"sessionID\":\"s\",\"status\":{\"type\":\"busy\"}}}"));
        RetryScheduled scheduled = assertInstanceOf(RetryScheduled.class, SseEventParser.parseJson(
                "{\"type\":\"session.retry.scheduled\",\"data\":{\"sessionID\":\"s\",\"assistantMessageID\":\"m\","
                        + "\"attempt\":1,\"at\":1700000000000,\"error\":{\"type\":\"provider.rate_limit\","
                        + "\"message\":\"slow down\",\"status\":429,\"retryAfterMs\":2000}}}"));
        McpStatusChanged mcp = assertInstanceOf(McpStatusChanged.class, SseEventParser.parseJson(
                "{\"type\":\"mcp.status.changed\",\"data\":{\"server\":\"victorialogs\"}}"));

        assertEquals("summary", ended.summary());
        assertEquals(10, ended.usage().input());
        assertEquals("retry", retry.status());
        assertEquals(2, retry.attempt());
        assertEquals(1_700_000_000_000L, retry.nextAttemptAt());
        assertEquals("busy", busy.status());
        assertNull(busy.attempt());
        assertEquals(429, scheduled.error().status());
        assertEquals(2_000L, scheduled.error().retryAfterMs());
        assertEquals("victorialogs", mcp.server());
    }

    @Test
    void malformedPayloadsBecomeUnknownEvents() {
        assertEquals("parse_error", SseEventParser.parseJson("{not json").type());
        assertEquals("parse_error", SseEventParser.parseJson("[1,2]").type());
        assertEquals("missing_type", SseEventParser.parseJson("{\"data\":{}}").type());
        assertNull(SseEventParser.parseJson("  "));
    }

    private static OpenCodeEvent event(String type) throws IOException {
        return SseEventParser.parseJson(fixture(type));
    }

    private static String fixture(String type) throws IOException {
        String path = "/fixtures/opencode-2.2.0/events/" + type + ".json";
        try (InputStream stream = CapturedEventsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing fixture " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
