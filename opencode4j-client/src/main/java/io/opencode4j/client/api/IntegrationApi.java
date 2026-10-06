package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.integration.AddWellKnownRequest;
import io.opencode4j.model.integration.AttemptStatus;
import io.opencode4j.model.integration.CommandAttempt;
import io.opencode4j.model.integration.CommandAttemptStatus;
import io.opencode4j.model.integration.CompleteOAuthRequest;
import io.opencode4j.model.integration.ConnectCommandRequest;
import io.opencode4j.model.integration.ConnectKeyRequest;
import io.opencode4j.model.integration.ConnectOAuthRequest;
import io.opencode4j.model.integration.Integration;
import io.opencode4j.model.integration.OAuthAttempt;
import java.util.List;
import java.util.Objects;

/**
 * Lists integrations and connects them to credentials (API key, OAuth or command based).
 *
 * <p>Every operation takes an optional {@code directory}; when it is {@code null} or blank the server
 * uses its default location. Stored credentials themselves are managed through {@link CredentialApi}.</p>
 */
public final class IntegrationApi {

    private static final String INTEGRATION_PATH = "/api/integration";

    private final OpenCodeTransport transport;

    public IntegrationApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** Lists every integration known to the server together with its methods and connections. */
    public List<Integration> list(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(INTEGRATION_PATH, directory),
                null,
                new TypeReference<List<Integration>>() { });
    }

    /** Fetches one integration. */
    public Integration get(String integrationId, String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(integrationPath(integrationId), directory),
                null,
                Integration.class);
    }

    /** Connects an integration with an API key. Completes synchronously. */
    public void connectKey(String integrationId, ConnectKeyRequest request, String directory) {
        transport.send(
                HttpMethod.POST,
                transport.withLocation(integrationPath(integrationId) + "/connect/key", directory),
                Objects.requireNonNull(request, "request"),
                Void.class);
    }

    /**
     * Starts an OAuth connection. Open {@link OAuthAttempt#url()} for the user, then either poll
     * {@link #oauthStatus} (auto mode) or call {@link #completeOAuth} with the pasted code (code mode).
     */
    public OAuthAttempt connectOAuth(String integrationId, ConnectOAuthRequest request, String directory) {
        return transport.send(
                HttpMethod.POST,
                transport.withLocation(integrationPath(integrationId) + "/connect/oauth", directory),
                Objects.requireNonNull(request, "request"),
                OAuthAttempt.class);
    }

    /** Reads the current status of an OAuth attempt. */
    public AttemptStatus oauthStatus(String integrationId, String attemptId, String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(oauthAttemptPath(integrationId, attemptId), directory),
                null,
                AttemptStatus.class);
    }

    /** Completes a code-mode OAuth attempt; use {@link CompleteOAuthRequest#withoutCode()} for auto mode. */
    public void completeOAuth(
            String integrationId,
            String attemptId,
            CompleteOAuthRequest request,
            String directory) {
        transport.send(
                HttpMethod.POST,
                transport.withLocation(oauthAttemptPath(integrationId, attemptId) + "/complete", directory),
                Objects.requireNonNull(request, "request"),
                Void.class);
    }

    /** Cancels a pending OAuth attempt. */
    public void cancelOAuth(String integrationId, String attemptId, String directory) {
        transport.send(
                HttpMethod.DELETE,
                transport.withLocation(oauthAttemptPath(integrationId, attemptId), directory),
                null,
                Void.class);
    }

    /** Starts a command-based connection. Poll {@link #commandStatus} until it leaves the pending state. */
    public CommandAttempt connectCommand(String integrationId, ConnectCommandRequest request, String directory) {
        return transport.send(
                HttpMethod.POST,
                transport.withLocation(integrationPath(integrationId) + "/connect/command", directory),
                Objects.requireNonNull(request, "request"),
                CommandAttempt.class);
    }

    /** Reads the current status of a command attempt. */
    public CommandAttemptStatus commandStatus(String integrationId, String attemptId, String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(commandAttemptPath(integrationId, attemptId), directory),
                null,
                CommandAttemptStatus.class);
    }

    /** Cancels a pending command attempt. */
    public void cancelCommand(String integrationId, String attemptId, String directory) {
        transport.send(
                HttpMethod.DELETE,
                transport.withLocation(commandAttemptPath(integrationId, attemptId), directory),
                null,
                Void.class);
    }

    /**
     * Adds a well-known integration source discovered from a URL.
     *
     * <p>Experimental: the server exposes this under {@code /api/experimental} and may change it.</p>
     */
    public void addWellKnown(AddWellKnownRequest request, String directory) {
        transport.send(
                HttpMethod.POST,
                transport.withLocation("/api/experimental/integration/wellknown", directory),
                Objects.requireNonNull(request, "request"),
                Void.class);
    }

    private String integrationPath(String integrationId) {
        Objects.requireNonNull(integrationId, "integrationId");
        return INTEGRATION_PATH + "/" + transport.segment(integrationId);
    }

    private String oauthAttemptPath(String integrationId, String attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        return integrationPath(integrationId) + "/connect/oauth/" + transport.segment(attemptId);
    }

    private String commandAttemptPath(String integrationId, String attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        return integrationPath(integrationId) + "/connect/command/" + transport.segment(attemptId);
    }
}
