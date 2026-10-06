package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.CreateSessionRequest;
import io.opencode4j.model.LocationRef;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.Session;
import io.opencode4j.model.SessionRevert;
import io.opencode4j.model.SnapshotFileDiff;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;

class SessionApiTest extends MockServerTest {

    private static final String SESSION = "{\"id\":\"ses_1\",\"projectID\":\"p\",\"cost\":0,"
            + "\"tokens\":{\"input\":0,\"output\":0,\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}},"
            + "\"time\":{\"created\":1,\"updated\":2},\"title\":\"T\","
            + "\"location\":{\"directory\":\"/work\"}}";

    @Test
    void createsASessionWithAgentModelAndLocation() throws Exception {
        // Given
        server.enqueue(json("{\"data\":" + SESSION + "}"));

        // When
        Session session = client.sessions().create(
                new CreateSessionRequest("T", "build", ModelRef.parse("github-copilot/gpt-6.1-sol"),
                        new LocationRef("/work")));

        // Then
        assertEquals("ses_1", session.id());
        assertEquals("/work", session.location().directory());
        assertRequest(
                HttpMethod.POST,
                "/api/session",
                "{\"title\":\"T\",\"agent\":\"build\",\"model\":{\"id\":\"gpt-6.1-sol\","
                        + "\"providerID\":\"github-copilot\"},\"location\":{\"directory\":\"/work\"}}");
    }

    @Test
    void getsASessionUsingAnEncodedPathSegment() throws Exception {
        // Given
        server.enqueue(json("{\"data\":" + SESSION + "}"));

        // When
        client.sessions().get("ses 1/x");

        // Then
        assertRequest(HttpMethod.GET, "/api/session/ses%201%2Fx", "");
    }

    @Test
    void listsSessionsFilteredByDirectoryIgnoringThePagingCursor() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[" + SESSION + "],\"cursor\":{\"next\":\"abc\"}}"));

        // When
        List<Session> sessions = client.sessions().list("/work");

        // Then
        assertEquals(1, sessions.size());
        assertRequest(HttpMethod.GET, "/api/session?directory=%2Fwork", "");
    }

    @Test
    void listsChildSessionsByParent() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[],\"cursor\":{}}"));

        // When
        client.sessions().children("ses_parent");

        // Then
        assertRequest(HttpMethod.GET, "/api/session?parentID=ses_parent", "");
    }

    @Test
    void deletesAndInterrupts() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(new MockResponse().setResponseCode(200));

        // When
        client.sessions().delete("ses_1");
        client.sessions().interrupt("ses_1");

        // Then
        assertRequest(HttpMethod.DELETE, "/api/session/ses_1", "");
        assertRequest(HttpMethod.POST, "/api/session/ses_1/interrupt", "");
    }

    @Test
    void switchesAgentAndModel() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        client.sessions().switchAgent("ses_1", "plan");
        client.sessions().switchModel("ses_1", ModelRef.parse("p/m"));

        // Then
        assertRequest(HttpMethod.POST, "/api/session/ses_1/agent", "{\"agent\":\"plan\"}");
        assertRequest(HttpMethod.POST, "/api/session/ses_1/model", "{\"model\":{\"id\":\"m\",\"providerID\":\"p\"}}");
    }

    @Test
    void forksWithAndWithoutABoundary() throws Exception {
        // Given
        server.enqueue(json("{\"data\":" + SESSION + "}"));
        server.enqueue(json("{\"data\":" + SESSION + "}"));

        // When
        client.sessions().fork("ses_1", null);
        client.sessions().fork("ses_1", "msg_9");

        // Then
        assertRequest(HttpMethod.POST, "/api/session/ses_1/fork", "{}");
        assertRequest(HttpMethod.POST, "/api/session/ses_1/fork", "{\"before\":\"msg_9\"}");
    }

    @Test
    void diffsATurnRange() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[{\"file\":\"a.txt\",\"patch\":\"@@\",\"additions\":1,"
                + "\"deletions\":0,\"status\":\"modified\"}]}"));
        server.enqueue(json("{\"data\":[]}"));
        server.enqueue(json("{\"data\":[]}"));

        // When
        List<SnapshotFileDiff> diffs = client.sessions().diff("ses_1", "msg_1", "msg_2");
        client.sessions().diff("ses_1", "msg_1", null);
        client.sessions().diff("ses_1", null, null);

        // Then
        assertEquals("a.txt", diffs.getFirst().file());
        assertRequest(HttpMethod.GET, "/api/session/ses_1/diff?from=msg_1&to=msg_2", "");
        assertRequest(HttpMethod.GET, "/api/session/ses_1/diff?from=msg_1", "");
        assertRequest(HttpMethod.GET, "/api/session/ses_1/diff", "");
    }

    @Test
    void stagesCommitsAndClearsAReverts() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"messageID\":\"msg_1\",\"snapshot\":\"abc\"}}"));
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        SessionRevert revert = client.sessions().revertStage("ses_1", "msg_1");
        client.sessions().revertCommit("ses_1");
        client.sessions().revertClear("ses_1");

        // Then
        assertEquals("msg_1", revert.messageID());
        assertRequest(HttpMethod.POST, "/api/session/ses_1/revert/stage", "{\"messageID\":\"msg_1\"}");
        assertRequest(HttpMethod.POST, "/api/session/ses_1/revert/commit", "");
        assertRequest(HttpMethod.DELETE, "/api/session/ses_1/revert", "");
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.sessions().get(null));
        assertThrows(NullPointerException.class, () -> client.sessions().create(null));
        assertThrows(NullPointerException.class, () -> client.sessions().switchAgent("ses_1", null));
        assertThrows(NullPointerException.class, () -> client.sessions().children(null));
        assertEquals(0, server.getRequestCount());
    }
}
