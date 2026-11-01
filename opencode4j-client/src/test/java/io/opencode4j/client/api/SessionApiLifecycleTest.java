package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.OpenCodeConflictException;
import io.opencode4j.model.Page;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.PermissionEffect;
import io.opencode4j.model.PermissionRule;
import io.opencode4j.model.PromptReceipt;
import io.opencode4j.model.Session;
import io.opencode4j.model.SessionQuery;
import io.opencode4j.model.SessionUpdate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;

class SessionApiLifecycleTest extends MockServerTest {

    @Test
    void decodesSessionMetadataAndOutcome() {
        // Given
        server.enqueue(fixture("session"));

        // When
        Session session = client.sessions().get("ses_1");

        // Then
        assertEquals(Map.of("conversationId", "c2"), session.metadata());
        assertEquals("succeeded", session.outcome());
        assertEquals("/work", session.location().directory());
    }

    @Test
    void pagesThroughRootSessionsOfALocation() throws Exception {
        // Given
        server.enqueue(fixture("sessions"));

        // When
        Page<Session> page = client.sessions().list(SessionQuery.roots("/work dir"), PageRequest.first(1));

        // Then
        assertEquals("/api/session?directory=%2Fwork+dir&parentID=null&limit=1", takeRequest().getPath());
        assertEquals("renamed", page.items().getFirst().title());
        assertNotNull(page.next());
        assertNotNull(page.previous());
    }

    @Test
    void listsChildrenAndArchivedSessions() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[],\"cursor\":{}}"));

        // When
        client.sessions().list(new SessionQuery(null, "ses_p", true, true, "deploy"), null);

        // Then: an explicit parent wins over rootsOnly
        assertEquals("/api/session?parentID=ses_p&archived=true&search=deploy", takeRequest().getPath());
    }

    @Test
    void updatesOnlyTheGivenFields() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        client.sessions().update("ses_1", SessionUpdate.title("Theo timeouts"));
        client.sessions().update("ses_1", new SessionUpdate(null, Map.of("releaseId", "r1"),
                List.of(new PermissionRule("shell", "*", PermissionEffect.DENY)), null));

        // Then
        assertRequest("PATCH", "/api/session/ses_1", "{\"title\":\"Theo timeouts\"}");
        assertRequest("PATCH", "/api/session/ses_1",
                "{\"metadata\":{\"releaseId\":\"r1\"},\"permissions\":[{\"action\":\"shell\",\"resource\":\"*\","
                        + "\"effect\":\"deny\"}]}");
    }

    @Test
    void queuesACompaction() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"id\":\"msg_c\",\"sessionID\":\"ses_1\",\"time\":{\"created\":1},"
                + "\"type\":\"compaction\",\"payload\":{},\"delivery\":\"steer\"}}"));

        // When
        PromptReceipt receipt = client.sessions().compact("ses_1");

        // Then
        assertRequest("POST", "/api/session/ses_1/compact", "{}");
        assertEquals("msg_c", receipt.id());
    }

    @Test
    void compactionConflictsAreTyped() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(409)
                .setBody("{\"_tag\":\"ConflictError\",\"message\":\"busy\"}"));

        // Then
        assertThrows(OpenCodeConflictException.class, () -> client.sessions().compact("ses_1"));
    }

    @Test
    void readsActiveSessionIds() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"ses_a\":{\"type\":\"running\"},\"ses_b\":{\"type\":\"running\"}}}"));
        server.enqueue(json("{\"data\":{}}"));

        // Then
        assertEquals(Set.of("ses_a", "ses_b"), client.sessions().activeSessionIds());
        assertEquals(Set.of(), client.sessions().activeSessionIds());
        assertEquals("/api/session/active", takeRequest().getPath());
    }
}
