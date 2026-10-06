package io.opencode4j.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesDataEnvelopeIgnoringSiblingProperties() throws Exception {
        // Given
        String response = "{\"data\": {\"id\": \"ses_123\"}, \"location\": {\"directory\": \"/work\"}}";
        JavaType payload = mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class);
        JavaType envelopeType = mapper.getTypeFactory().constructParametricType(DataEnvelope.class, payload);

        // When
        DataEnvelope<Map<String, String>> envelope = mapper.readValue(response, envelopeType);

        // Then
        assertEquals("ses_123", envelope.data().get("id"));
    }
}
