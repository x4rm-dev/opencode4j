package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeNotFoundException;
import io.opencode4j.model.Page;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.SortOrder;
import io.opencode4j.model.message.AssistantContent;
import io.opencode4j.model.message.AssistantMessage;
import io.opencode4j.model.message.CompactionMessage;
import io.opencode4j.model.message.IdleMessage;
import io.opencode4j.model.message.Message;
import io.opencode4j.model.message.OtherMessage;
import io.opencode4j.model.message.UserMessage;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class MessageApiTest extends MockServerTest {

    @Test
    void decodesAFullTurnOldestFirst() throws Exception {
        // Given
        server.enqueue(fixture("messages-asc"));

        // When
        Page<Message> page = client.messages().list("ses_1", PageRequest.first(50).ordered(SortOrder.ASC));

        // Then
        RecordedRequest request = takeRequest();
        assertEquals("/api/session/ses_1/message?limit=50&order=asc", request.getPath());
        UserMessage user = assertInstanceOf(UserMessage.class, page.items().get(0));
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, page.items().get(1));
        IdleMessage idle = assertInstanceOf(IdleMessage.class, page.items().get(2));
        assertEquals("ping", user.text());
        assertEquals(Map.of("turnId", "t1"), user.metadata());
        assertEquals("pong", assistant.text());
        assertEquals("build", assistant.agent());
        assertEquals("fake", assistant.model().providerID());
        assertEquals("stop", assistant.finish());
        assertEquals(11, assistant.tokens().input());
        assertNotNull(assistant.time().completed());
        assertTrue(idle.succeeded());
    }

    @Test
    void decodesDeclinedToolCallsAndFailedCompactions() {
        // Given: newest first, after a rejected shell call and a failed manual compaction
        server.enqueue(fixture("session-messages"));

        // When
        List<Message> messages = client.messages().list("ses_1", PageRequest.first(3)).items();

        // Then
        assertInstanceOf(IdleMessage.class, messages.get(0));
        CompactionMessage compaction = assertInstanceOf(CompactionMessage.class, messages.get(1));
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("failed", compaction.status());
        assertEquals("manual", compaction.reason());
        assertEquals("compaction.failed", compaction.error().type());
        AssistantContent.ToolCall call = assistant.toolCalls().getFirst();
        assertEquals("shell", call.name());
        assertEquals(Boolean.FALSE, call.executed());
        assertEquals("error", call.state().status());
        assertEquals("echo hi", call.state().input().path("command").asText());
        assertEquals("aborted", call.state().error().type());
        assertEquals("", assistant.text());
    }

    @Test
    void followsCursorsUntilAnEmptyPage() throws Exception {
        // Given
        server.enqueue(fixture("messages-page-1"));
        server.enqueue(fixture("messages-page-2"));

        // When
        PageRequest request = PageRequest.first(2);
        Page<Message> first = client.messages().list("ses_1", request);
        Page<Message> second = client.messages().list("ses_1", request.at(first.next()));

        // Then
        assertEquals(List.of("idle", "assistant"), first.items().stream().map(Message::type).toList());
        assertTrue(first.hasMore(request));
        assertEquals(List.of("user"), second.items().stream().map(Message::type).toList());
        assertFalse(second.hasMore(request), "a short page is the last one even though it has a next cursor");
        assertNotNull(second.next());
        takeRequest();
        assertEquals("/api/session/ses_1/message?limit=2&cursor=" + first.next().value(), takeRequest().getPath());
    }

    @Test
    void unknownMessageAndPartTypesDoNotBreakDecoding() {
        // Given
        server.enqueue(json("{\"data\":[{\"id\":\"m1\",\"type\":\"agent-switched\",\"time\":{\"created\":1},"
                + "\"agent\":\"plan\"},{\"id\":\"m2\",\"type\":\"assistant\",\"time\":{\"created\":2},"
                + "\"agent\":\"a\",\"model\":{\"id\":\"x\",\"providerID\":\"p\"},"
                + "\"content\":[{\"type\":\"hologram\"},{\"type\":\"text\",\"text\":\"hi\"}]}],\"cursor\":{}}"));

        // When
        Page<Message> page = client.messages().list("ses_1", null);

        // Then
        OtherMessage other = assertInstanceOf(OtherMessage.class, page.items().get(0));
        assertEquals("agent-switched", other.type());
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, page.items().get(1));
        assertInstanceOf(AssistantContent.Other.class, assistant.content().getFirst());
        assertEquals("hi", assistant.text());
        assertEquals(null, page.next());
    }

    @Test
    void readsOneMessageAndTheModelContext() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"id\":\"m1\",\"type\":\"user\",\"time\":{\"created\":1},\"text\":\"q\"}}"));
        server.enqueue(fixture("session-context"));

        // When
        Message one = client.messages().get("ses_1", "m1");
        List<Message> context = client.messages().context("ses_1");

        // Then
        assertEquals("q", assertInstanceOf(UserMessage.class, one).text());
        assertEquals("/api/session/ses_1/message/m1", takeRequest().getPath());
        assertEquals("/api/session/ses_1/context", takeRequest().getPath());
        assertEquals(7, context.size());
    }

    @Test
    void missingSessionsAreTyped() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(404).setBody(
                "{\"_tag\":\"SessionNotFoundError\",\"sessionID\":\"nope\",\"message\":\"Session not found: nope\"}"));

        // When
        OpenCodeNotFoundException failure = assertThrows(OpenCodeNotFoundException.class,
                () -> client.messages().list("nope", PageRequest.first()));

        // Then
        assertEquals("SessionNotFoundError", failure.errorCode());
    }
}
