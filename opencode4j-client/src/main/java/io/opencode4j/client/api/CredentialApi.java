package io.opencode4j.client.api;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.integration.RenameCredentialRequest;
import java.util.Objects;

/**
 * Manages stored integration credentials.
 *
 * <p>Credentials are global to the server, so unlike {@link IntegrationApi} these operations take no
 * location. Credential IDs are listed on {@code CredentialConnection.id()} of an integration.</p>
 */
public final class CredentialApi {

    private static final String CREDENTIAL_PATH = "/api/credential/";

    private final OpenCodeTransport transport;

    public CredentialApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** Changes the display label of a credential. */
    public void rename(String credentialId, RenameCredentialRequest request) {
        transport.send(
                HttpMethod.PATCH,
                credentialPath(credentialId),
                Objects.requireNonNull(request, "request"),
                Void.class);
    }

    /** Deletes a credential. */
    public void remove(String credentialId) {
        transport.send(HttpMethod.DELETE, credentialPath(credentialId), null, Void.class);
    }

    /** Makes a stored credential the active one for its integration. */
    public void activate(String credentialId) {
        transport.send(HttpMethod.POST, credentialPath(credentialId) + "/activate", null, Void.class);
    }

    private String credentialPath(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return CREDENTIAL_PATH + transport.segment(credentialId);
    }
}
