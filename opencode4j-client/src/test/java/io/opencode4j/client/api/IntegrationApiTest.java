package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.integration.AddWellKnownRequest;
import io.opencode4j.model.integration.AttemptState;
import io.opencode4j.model.integration.AttemptStatus;
import io.opencode4j.model.integration.CommandAttempt;
import io.opencode4j.model.integration.CommandAttemptStatus;
import io.opencode4j.model.integration.CompleteOAuthRequest;
import io.opencode4j.model.integration.ConnectCommandRequest;
import io.opencode4j.model.integration.ConnectKeyRequest;
import io.opencode4j.model.integration.ConnectOAuthRequest;
import io.opencode4j.model.integration.CredentialConnection;
import io.opencode4j.model.integration.Integration;
import io.opencode4j.model.integration.OAuthAttempt;
import io.opencode4j.model.integration.OAuthMode;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;

class IntegrationApiTest extends MockServerTest {

    private static final String TIME = "\"time\":{\"created\":1,\"expires\":2}";
    private static final String ENVELOPE = "{\"location\":{\"directory\":\"/workspace\"},\"data\":%s}";

    @Test
    void listsIntegrationsUnwrappingTheEnvelopeAndSendingLocation() throws Exception {
        // Given
        server.enqueue(json(envelope("[{\"id\":\"github\",\"name\":\"GitHub\",\"methods\":[],"
                + "\"connections\":[{\"type\":\"credential\",\"id\":\"cred_1\",\"label\":\"Work\","
                + "\"method\":\"key\"}]}]")));

        // When
        List<Integration> integrations = client.integrations().list("/workspace");

        // Then
        assertEquals(1, integrations.size());
        assertEquals("github", integrations.getFirst().id());
        CredentialConnection connection = assertInstanceOf(
                CredentialConnection.class, integrations.getFirst().connections().getFirst());
        assertEquals("cred_1", connection.id());
        assertRequest(HttpMethod.GET, "/api/integration?location%5Bdirectory%5D=%2Fworkspace", "");
    }

    @Test
    void omitsLocationWhenNoDirectoryIsGiven() throws Exception {
        // Given
        server.enqueue(json(envelope("[]")));

        // When
        client.integrations().list(null);

        // Then
        assertRequest(HttpMethod.GET, "/api/integration", "");
    }

    @Test
    void percentEncodesPathSegments() throws Exception {
        // Given
        server.enqueue(json(envelope("{\"id\":\"x\",\"name\":\"X\"}")));

        // When
        client.integrations().get("my integration/2", null);

        // Then
        assertRequest(HttpMethod.GET, "/api/integration/my%20integration%2F2", "");
    }

    @Test
    void connectsWithAKeyAndAcceptsNoContent() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.integrations().connectKey("github", new ConnectKeyRequest("k", null, "Work"), "/workspace");

        // Then
        assertRequest(
                HttpMethod.POST,
                "/api/integration/github/connect/key?location%5Bdirectory%5D=%2Fworkspace",
                "{\"key\":\"k\",\"label\":\"Work\"}");
    }

    @Test
    void runsTheOAuthFlow() throws Exception {
        // Given
        server.enqueue(json(envelope("{\"attemptID\":\"att_1\",\"url\":\"https://login.test\","
                + "\"instructions\":\"Paste the code\",\"mode\":\"code\"," + TIME + "}")));
        server.enqueue(json(envelope("{\"status\":\"pending\"," + TIME + "}")));
        server.enqueue(noContent());
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        OAuthAttempt attempt = client.integrations().connectOAuth("github", ConnectOAuthRequest.of("oauth"), null);
        AttemptStatus status = client.integrations().oauthStatus("github", "att_1", null);
        client.integrations().completeOAuth("github", "att_1", CompleteOAuthRequest.of("abc"), null);
        client.integrations().completeOAuth("github", "att_1", CompleteOAuthRequest.withoutCode(), null);
        client.integrations().cancelOAuth("github", "att_1", null);

        // Then
        assertEquals(OAuthMode.CODE, attempt.mode());
        assertEquals(AttemptState.PENDING, status.status());
        assertRequest(HttpMethod.POST, "/api/integration/github/connect/oauth", "{\"methodID\":\"oauth\"}");
        assertRequest(HttpMethod.GET, "/api/integration/github/connect/oauth/att_1", "");
        assertRequest(
                HttpMethod.POST, "/api/integration/github/connect/oauth/att_1/complete", "{\"code\":\"abc\"}");
        assertRequest(HttpMethod.POST, "/api/integration/github/connect/oauth/att_1/complete", "{}");
        assertRequest(HttpMethod.DELETE, "/api/integration/github/connect/oauth/att_1", "");
    }

    @Test
    void runsTheCommandFlow() throws Exception {
        // Given
        server.enqueue(json(envelope("{\"attemptID\":\"att_2\"," + TIME + "}")));
        server.enqueue(json(envelope("{\"status\":\"failed\",\"message\":\"boom\"," + TIME + "}")));
        server.enqueue(noContent());

        // When
        CommandAttempt attempt = client.integrations()
                .connectCommand("github", ConnectCommandRequest.of("cli"), null);
        CommandAttemptStatus status = client.integrations().commandStatus("github", "att_2", null);
        client.integrations().cancelCommand("github", "att_2", null);

        // Then
        assertEquals("att_2", attempt.attemptID());
        assertEquals(AttemptState.FAILED, status.status());
        assertEquals("boom", status.message());
        assertRequest(HttpMethod.POST, "/api/integration/github/connect/command", "{\"methodID\":\"cli\"}");
        assertRequest(HttpMethod.GET, "/api/integration/github/connect/command/att_2", "");
        assertRequest(HttpMethod.DELETE, "/api/integration/github/connect/command/att_2", "");
    }

    @Test
    void addsAWellKnownSource() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.integrations().addWellKnown(AddWellKnownRequest.of("https://example.test/x.json"), null);

        // Then
        assertRequest(
                HttpMethod.POST,
                "/api/experimental/integration/wellknown",
                "{\"url\":\"https://example.test/x.json\"}");
    }

    @Test
    void surfacesServerErrorsWithTheirStatusCode() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"_tag\":\"IntegrationNotFoundError\"}"));

        // When
        OpenCodeException failure = assertThrows(
                OpenCodeException.class, () -> client.integrations().get("nope", null));

        // Then
        assertEquals(404, failure.statusCode());
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.integrations().get(null, null));
        assertThrows(NullPointerException.class, () -> client.integrations().oauthStatus("github", null, null));
        assertThrows(NullPointerException.class, () -> client.integrations().connectKey("github", null, null));
        assertEquals(0, server.getRequestCount());
    }

    private static String envelope(String data) {
        return ENVELOPE.formatted(data);
    }
}
