package io.opencode4j.model.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormValueTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsBareJsonFormValues() throws Exception {
        // Given
        String json = "{\"a\":\"x\",\"b\":1.5,\"c\":true,\"d\":[\"p\",\"q\"]}";

        // When
        Map<String, FormValue> values = mapper.readValue(json, new TypeReference<>() { });
        String serialized = mapper.writeValueAsString(values);

        // Then
        assertEquals(json, serialized);
    }

    @Test
    void mapsEachJsonKindToItsVariant() throws Exception {
        // When
        FormValue text = mapper.readValue("\"x\"", FormValue.class);
        FormValue number = mapper.readValue("2", FormValue.class);
        FormValue flag = mapper.readValue("false", FormValue.class);
        FormValue list = mapper.readValue("[\"a\"]", FormValue.class);

        // Then
        assertEquals(new StringValue("x"), text);
        assertEquals(new NumberValue(2.0), number);
        assertEquals(new BooleanValue(false), flag);
        assertEquals(new StringListValue(java.util.List.of("a")), list);
    }

    @Test
    void rejectsListElementsThatAreNotStrings() {
        // When
        JsonMappingException failure = assertThrows(
                JsonMappingException.class,
                () -> mapper.readValue("[\"a\",1]", FormValue.class));

        // Then
        assertTrue(rootMessage(failure).contains("Form list values must be strings"));
    }

    @Test
    void rejectsNestedListsAndNullElements() {
        // Then
        assertThrows(JsonMappingException.class, () -> mapper.readValue("[[\"a\"]]", FormValue.class));
        assertThrows(JsonMappingException.class, () -> mapper.readValue("[null]", FormValue.class));
    }

    @Test
    void rejectsObjects() {
        // When
        JsonMappingException failure = assertThrows(
                JsonMappingException.class,
                () -> mapper.readValue("{\"a\":1}", FormValue.class));

        // Then
        assertTrue(rootMessage(failure).contains("Unsupported form value type"));
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
