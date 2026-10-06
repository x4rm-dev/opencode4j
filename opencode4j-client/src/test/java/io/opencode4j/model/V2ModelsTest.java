package io.opencode4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Wire-format tests for the v2 session and prompt models, using payloads captured from a 2.0.20 server. */
class V2ModelsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesTheSessionReturnedByTheServer() throws Exception {
        // Given
        String json = "{\"id\":\"ses_f0c408fe8ffebMS4ePmP1sR7mR\",\"projectID\":\"53cd3e96\","
                + "\"cost\":0.012148,\"tokens\":{\"input\":6049,\"output\":5,\"reasoning\":0,"
                + "\"cache\":{\"read\":0,\"write\":0}},\"time\":{\"created\":1790674619125,"
                + "\"updated\":1790674619125},\"title\":\"New session\","
                + "\"location\":{\"directory\":\"/Users/praveen.k/projects/exensy\"},"
                + "\"agent\":\"explore\",\"model\":{\"id\":\"gpt-5.3-codex\",\"providerID\":\"github-copilot\","
                + "\"variant\":\"default\"},\"parentID\":\"ses_parent\"}";

        // When
        Session session = mapper.readValue(json, Session.class);

        // Then
        assertEquals("ses_f0c408fe8ffebMS4ePmP1sR7mR", session.id());
        assertEquals("ses_parent", session.parentId());
        assertEquals("53cd3e96", session.projectId());
        assertEquals("explore", session.agent());
        assertEquals("github-copilot/gpt-5.3-codex", session.model().toString());
        assertEquals(6049, session.tokens().input());
        assertEquals(0.012148, session.cost());
        assertEquals(1790674619125L, session.time().created());
        assertEquals("/Users/praveen.k/projects/exensy", session.location().directory());
    }

    @Test
    void deserializesServerInfo() throws Exception {
        // Given
        String json = "{\"version\":\"2.0.20\",\"pid\":56782,\"urls\":[\"http://127.0.0.1:4096\"],"
                + "\"paths\":{\"tmp\":\"/tmp/opencode\"}}";

        // When
        ServerInfo info = mapper.readValue(json, ServerInfo.class);

        // Then
        assertEquals("2.0.20", info.version());
        assertEquals(56782, info.pid());
        assertEquals(List.of("http://127.0.0.1:4096"), info.urls());
        assertEquals("/tmp/opencode", info.paths().get("tmp"));
    }

    @Test
    void parsesProviderModelStrings() {
        // Then
        ModelRef simple = ModelRef.parse("github-copilot/gpt-6.1-sol");
        assertEquals("github-copilot", simple.providerID());
        assertEquals("gpt-6.1-sol", simple.id());
        ModelRef nested = ModelRef.parse("openrouter/anthropic/claude-sonnet-4");
        assertEquals("openrouter", nested.providerID());
        assertEquals("anthropic/claude-sonnet-4", nested.id());
        assertEquals("openrouter/anthropic/claude-sonnet-4", nested.toString());
    }

    @Test
    void rejectsModelStringsWithoutAProvider() {
        // Then
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("gpt-6.1-sol"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("/model"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("provider/"));
    }

    @Test
    void serializesCreateSessionRequestOmittingUnsetFields() throws Exception {
        // Then
        assertEquals("{}", mapper.writeValueAsString(CreateSessionRequest.empty()));
        assertEquals("{\"title\":\"t\"}", mapper.writeValueAsString(CreateSessionRequest.titled("t")));
        assertEquals(
                "{\"title\":\"t\",\"agent\":\"build\",\"model\":{\"id\":\"m\",\"providerID\":\"p\"},"
                        + "\"location\":{\"directory\":\"/work\"}}",
                mapper.writeValueAsString(new CreateSessionRequest(
                        "t", "build", ModelRef.parse("p/m"), new LocationRef("/work"))));
    }

    @Test
    void serializesPromptsAsTheFlatBodyTheV2ServerExpects() throws Exception {
        // Then
        assertEquals("{\"text\":\"hello\"}", mapper.writeValueAsString(PromptRequest.of("hello")));
        assertEquals(
                "{\"text\":\"hi\",\"files\":[{\"uri\":\"file:///a.txt\",\"name\":\"a.txt\"}],"
                        + "\"delivery\":\"queue\",\"resume\":false}",
                mapper.writeValueAsString(new PromptRequest(
                        "hi",
                        List.of(new FileAttachment("file:///a.txt", "a.txt", null)),
                        Delivery.QUEUE,
                        false)));
        assertFalse(mapper.writeValueAsString(PromptRequest.of("hello")).contains("prompt"));

        PromptRequest full = PromptRequest.builder("Run @worker with @launch-protocol")
                .id("msg_custom_1")
                .agents(List.of(PromptAgentAttachment.of("worker", PromptMention.of(4, 11, "@worker"))))
                .skills(List.of(PromptSkillAttachment.of("launch-protocol")))
                .metadata(Map.of("runId", "run-100"))
                .build();
        String json = mapper.writeValueAsString(full);
        assertTrue(json.contains("\"id\":\"msg_custom_1\""));
        assertTrue(json.contains("\"agents\":[{\"name\":\"worker\",\"mention\":{\"start\":4,\"end\":11,\"text\":\"@worker\"}}]"));
        assertTrue(json.contains("\"skills\":[{\"id\":\"launch-protocol\"}]"));
        assertTrue(json.contains("\"metadata\":{\"runId\":\"run-100\"}"));
    }

    @Test
    void deserializesThePromptReceiptAndToleratesUnknownDeliveryModes() throws Exception {
        // Given
        String receipt = "{\"id\":\"msg_1\",\"sessionID\":\"ses_1\",\"time\":{\"created\":1},\"type\":\"user\","
                + "\"payload\":{\"text\":\"hi\"},\"delivery\":\"steer\"}";

        // Then
        PromptReceipt parsed = mapper.readValue(receipt, PromptReceipt.class);
        assertEquals("msg_1", parsed.id());
        assertEquals(Delivery.STEER, parsed.delivery());
        assertEquals(Delivery.UNKNOWN, mapper.readValue("\"future\"", Delivery.class));
    }

    @Test
    void serializesPermissionAndFormReplies() throws Exception {
        // Then
        assertEquals(
                "{\"decision\":\"once\"}",
                mapper.writeValueAsString(PermissionReplyRequest.of(PermissionDecision.ONCE)));
        assertEquals(
                "{\"decision\":\"reject\",\"message\":\"no\"}",
                mapper.writeValueAsString(new PermissionReplyRequest(PermissionDecision.REJECT, "no")));
        assertEquals(
                "{\"answer\":{\"q0\":\"A\"}}",
                mapper.writeValueAsString(new FormReplyRequest(java.util.Map.of(
                        "q0", io.opencode4j.model.form.FormValue.of("A")))));
    }

    @Test
    void requiresTheFieldsTheServerRequires() {
        // Then
        assertThrows(NullPointerException.class, () -> PromptRequest.of(null));
        assertThrows(NullPointerException.class, () -> FileAttachment.of(null));
        assertThrows(NullPointerException.class, () -> PermissionReplyRequest.of(null));
        assertThrows(NullPointerException.class, () -> new FormReplyRequest(null));
    }
}
