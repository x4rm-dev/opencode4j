package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.integration.RenameCredentialRequest;
import org.junit.jupiter.api.Test;

class CredentialApiTest extends MockServerTest {

    @Test
    void renamesACredential() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.credentials().rename("cred_1", RenameCredentialRequest.of("Personal"));

        // Then
        assertRequest(HttpMethod.PATCH, "/api/credential/cred_1", "{\"label\":\"Personal\"}");
    }

    @Test
    void removesACredential() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.credentials().remove("cred_1");

        // Then
        assertRequest(HttpMethod.DELETE, "/api/credential/cred_1", "");
    }

    @Test
    void activatesACredentialWithoutALocation() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.credentials().activate("cred 1");

        // Then
        assertRequest(HttpMethod.POST, "/api/credential/cred%201/activate", "");
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.credentials().remove(null));
        assertThrows(NullPointerException.class, () -> client.credentials().rename("cred_1", null));
        assertEquals(0, server.getRequestCount());
    }
}
