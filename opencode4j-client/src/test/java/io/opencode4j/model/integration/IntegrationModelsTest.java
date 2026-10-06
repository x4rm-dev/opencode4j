package io.opencode4j.model.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.model.form.BooleanField;
import io.opencode4j.model.form.FormField;
import io.opencode4j.model.form.FormOperator;
import io.opencode4j.model.form.IntegerField;
import io.opencode4j.model.form.MultiselectField;
import io.opencode4j.model.form.NumberField;
import io.opencode4j.model.form.StringField;
import io.opencode4j.model.form.UnknownField;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationModelsTest {

    private static final String INTEGRATION = """
            {
              "id": "github",
              "name": "GitHub",
              "metadata": {"tier": "cloud", "note": null},
              "methods": [
                {
                  "type": "oauth", "id": "oauth", "label": "OAuth",
                  "form": [
                    {"type": "string", "key": "email", "title": "Email", "required": true,
                     "when": [{"key": "enabled", "op": "eq", "value": true}]},
                    {"type": "number", "key": "ratio", "minimum": "-Infinity",
                     "maximum": "Infinity", "default": 1.5},
                    {"type": "integer", "key": "count", "minimum": 0, "maximum": 10, "default": 2},
                    {"type": "boolean", "key": "enabled", "default": true},
                    {"type": "multiselect", "key": "scopes",
                     "options": [{"value": "repo", "label": "Repository"}], "default": ["repo"]}
                  ]
                },
                {"type": "command", "id": "cli", "label": "CLI", "command": ["gh", "auth", "login"]},
                {"type": "key", "label": "Token", "form": []},
                {"type": "env", "names": ["GITHUB_TOKEN"]}
              ],
              "connections": [
                {"type": "credential", "id": "cred_1", "label": "Work", "method": "oauth"},
                {"type": "env", "name": "GITHUB_TOKEN"}
              ]
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesEveryKnownMethodAndConnection() throws Exception {
        // When
        Integration integration = mapper.readValue(INTEGRATION, Integration.class);

        // Then
        assertEquals("github", integration.id());
        assertEquals("cloud", integration.metadata().get("tier"));
        assertInstanceOf(OAuthMethod.class, integration.methods().get(0));
        assertInstanceOf(CommandMethod.class, integration.methods().get(1));
        assertInstanceOf(KeyMethod.class, integration.methods().get(2));
        assertInstanceOf(EnvMethod.class, integration.methods().get(3));

        CredentialConnection credential = assertInstanceOf(
                CredentialConnection.class, integration.connections().get(0));
        assertEquals(CredentialMethod.OAUTH, credential.method());
        assertEquals("GITHUB_TOKEN", assertInstanceOf(EnvConnection.class, integration.connections().get(1)).name());
    }

    @Test
    void keepsNullValuesInFreeFormMetadata() throws Exception {
        // When
        Integration integration = mapper.readValue(INTEGRATION, Integration.class);

        // Then
        assertTrue(integration.metadata().containsKey("note"));
        assertEquals(null, integration.metadata().get("note"));
    }

    @Test
    void deserializesEveryFieldTypeAndSpecialNumbers() throws Exception {
        // When
        Integration integration = mapper.readValue(INTEGRATION, Integration.class);
        List<FormField> form = assertInstanceOf(OAuthMethod.class, integration.methods().getFirst()).form();

        // Then
        StringField email = assertInstanceOf(StringField.class, form.get(0));
        assertEquals(FormOperator.EQ, email.when().getFirst().op());
        NumberField ratio = assertInstanceOf(NumberField.class, form.get(1));
        assertEquals(Double.NEGATIVE_INFINITY, ratio.minimum());
        assertEquals(Double.POSITIVE_INFINITY, ratio.maximum());
        assertInstanceOf(IntegerField.class, form.get(2));
        assertInstanceOf(BooleanField.class, form.get(3));
        assertEquals(List.of("repo"), assertInstanceOf(MultiselectField.class, form.get(4)).defaultValue());
    }

    @Test
    void usesUnknownRecordsForFutureDiscriminators() throws Exception {
        // When
        IntegrationMethod method = mapper.readValue("{\"type\":\"device\"}", IntegrationMethod.class);
        Connection connection = mapper.readValue("{\"type\":\"socket\"}", Connection.class);
        FormField field = mapper.readValue("{\"type\":\"slider\",\"key\":\"size\"}", FormField.class);

        // Then
        assertEquals("device", assertInstanceOf(UnknownMethod.class, method).type());
        assertEquals("socket", assertInstanceOf(UnknownConnection.class, connection).type());
        assertEquals("slider", assertInstanceOf(UnknownField.class, field).type());
    }

    @Test
    void mapsUnknownEnumValuesToUnknown() throws Exception {
        // Then
        assertEquals(AttemptState.UNKNOWN, mapper.readValue("\"future\"", AttemptState.class));
        assertEquals(OAuthMode.UNKNOWN, mapper.readValue("\"future\"", OAuthMode.class));
        assertEquals(CredentialMethod.UNKNOWN, mapper.readValue("\"future\"", CredentialMethod.class));
        assertEquals(FormOperator.UNKNOWN, mapper.readValue("\"future\"", FormOperator.class));
    }

    @Test
    void serializesEnumsAsLowercaseWireValues() throws Exception {
        // Then
        assertEquals("\"pending\"", mapper.writeValueAsString(AttemptState.PENDING));
        assertEquals("\"auto\"", mapper.writeValueAsString(OAuthMode.AUTO));
        assertEquals("\"key\"", mapper.writeValueAsString(CredentialMethod.KEY));
        assertEquals("\"neq\"", mapper.writeValueAsString(FormOperator.NEQ));
    }

    @Test
    void deserializesEveryAttemptStateAndSpecialTimestamps() throws Exception {
        // Given
        String failed = "{\"status\":\"failed\",\"message\":\"Denied\","
                + "\"time\":{\"created\":1,\"expires\":\"Infinity\"}}";

        // When
        AttemptStatus failedStatus = mapper.readValue(failed, AttemptStatus.class);

        // Then
        assertEquals(AttemptState.FAILED, failedStatus.status());
        assertEquals("Denied", failedStatus.message());
        assertEquals(Double.POSITIVE_INFINITY, failedStatus.time().expires());
        for (String state : List.of("pending", "complete", "expired")) {
            String json = "{\"status\":\"" + state + "\",\"time\":{\"created\":1,\"expires\":2}}";
            assertEquals(AttemptState.fromWire(state), mapper.readValue(json, AttemptStatus.class).status());
        }
    }

    @Test
    void deserializesOAuthAndCommandAttempts() throws Exception {
        // Given
        String oauth = "{\"attemptID\":\"att_1\",\"url\":\"https://x.test\",\"instructions\":\"Go\","
                + "\"mode\":\"code\",\"time\":{\"created\":1,\"expires\":2}}";
        String command = "{\"attemptID\":\"att_2\",\"time\":{\"created\":1,\"expires\":2}}";

        // When
        OAuthAttempt oauthAttempt = mapper.readValue(oauth, OAuthAttempt.class);
        CommandAttempt commandAttempt = mapper.readValue(command, CommandAttempt.class);

        // Then
        assertEquals(OAuthMode.CODE, oauthAttempt.mode());
        assertEquals("att_1", oauthAttempt.attemptID());
        assertEquals("att_2", commandAttempt.attemptID());
    }
}
