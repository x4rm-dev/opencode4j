package io.opencode4j.model.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationRequestsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesConnectKeyRequests() throws Exception {
        // Given
        ConnectKeyRequest minimal = ConnectKeyRequest.of("k");
        ConnectKeyRequest detailed = new ConnectKeyRequest("k", Map.of("scope", FormValue.of("repo")), "Work");

        // Then
        assertEquals("{\"key\":\"k\"}", mapper.writeValueAsString(minimal));
        assertEquals(
                "{\"key\":\"k\",\"answer\":{\"scope\":\"repo\"},\"label\":\"Work\"}",
                mapper.writeValueAsString(detailed));
    }

    @Test
    void serializesConnectOAuthAndCommandRequests() throws Exception {
        // Then
        assertEquals("{\"methodID\":\"oauth-main\"}", mapper.writeValueAsString(ConnectOAuthRequest.of("oauth-main")));
        assertEquals(
                "{\"methodID\":\"cli\"}", mapper.writeValueAsString(ConnectCommandRequest.of("cli")));
        assertEquals(
                "{\"methodID\":\"cli\",\"label\":\"Laptop\"}",
                mapper.writeValueAsString(new ConnectCommandRequest("cli", "Laptop")));
    }

    @Test
    void serializesCompleteOAuthRequestWithAndWithoutCode() throws Exception {
        // Then
        assertEquals("{\"code\":\"abc\"}", mapper.writeValueAsString(CompleteOAuthRequest.of("abc")));
        assertEquals("{}", mapper.writeValueAsString(CompleteOAuthRequest.withoutCode()));
    }

    @Test
    void serializesCredentialAndWellKnownRequests() throws Exception {
        // Then
        assertEquals("{\"label\":\"Renamed\"}", mapper.writeValueAsString(RenameCredentialRequest.of("Renamed")));
        assertEquals(
                "{\"url\":\"https://example.test/integration.json\"}",
                mapper.writeValueAsString(AddWellKnownRequest.of("https://example.test/integration.json")));
    }

    @Test
    void rejectsMissingRequiredFields() {
        // Then
        assertThrows(NullPointerException.class, () -> ConnectKeyRequest.of(null));
        assertThrows(NullPointerException.class, () -> ConnectOAuthRequest.of(null));
        assertThrows(NullPointerException.class, () -> ConnectCommandRequest.of(null));
        assertThrows(NullPointerException.class, () -> RenameCredentialRequest.of(null));
        assertThrows(NullPointerException.class, () -> AddWellKnownRequest.of(null));
    }
}
