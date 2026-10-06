package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.Delivery;
import io.opencode4j.model.FileAttachment;
import io.opencode4j.model.PromptReceipt;
import io.opencode4j.model.PromptRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptApiTest extends MockServerTest {

    private static final String RECEIPT = "{\"data\":{\"admittedSeq\":1,\"id\":\"msg_1\",\"sessionID\":\"ses_1\","
            + "\"time\":{\"created\":1},\"type\":\"user\",\"payload\":{\"text\":\"hi\"},\"delivery\":\"steer\"}}";

    @Test
    void sendsTheFlatPromptBodyAndReturnsTheReceipt() throws Exception {
        // Given
        server.enqueue(json(RECEIPT));

        // When
        PromptReceipt receipt = client.prompts().send("ses_1", PromptRequest.of("hi"));

        // Then
        assertEquals("msg_1", receipt.id());
        assertEquals("ses_1", receipt.sessionID());
        assertEquals(Delivery.STEER, receipt.delivery());
        assertRequest(HttpMethod.POST, "/api/session/ses_1/prompt", "{\"text\":\"hi\"}");
    }

    @Test
    void sendsAttachmentsAndDeliveryOptions() throws Exception {
        // Given
        server.enqueue(json(RECEIPT));

        // When
        client.prompts().send("ses_1", new PromptRequest(
                "look", List.of(FileAttachment.of("file:///a.txt")), Delivery.QUEUE, true));

        // Then
        assertRequest(
                HttpMethod.POST,
                "/api/session/ses_1/prompt",
                "{\"text\":\"look\",\"files\":[{\"uri\":\"file:///a.txt\"}],\"delivery\":\"queue\",\"resume\":true}");
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.prompts().send(null, PromptRequest.of("x")));
        assertThrows(NullPointerException.class, () -> client.prompts().send("ses_1", null));
        assertEquals(0, server.getRequestCount());
    }
}
