package io.opencode4j.agent.runtime.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper);

    static class TestTools {
        @Tool(description = "Adds numbers")
        public int add(
                @Param(name = "x", description = "First number") int x,
                @Param(name = "y", description = "Second number") int y,
                ToolContext context
        ) {
            return x + y;
        }

        @Tool(description = "Process text")
        public String process(
                @Param(name = "text") String text,
                @Param(name = "tags", required = false) List<String> tags
        ) {
            return text;
        }
    }

    record ReviewResult(String verdict, int score, List<String> points) {}

    @Test
    void generatesSchemaForMethodParametersIgnoringContext() throws Exception {
        Method addMethod = TestTools.class.getMethod("add", int.class, int.class, ToolContext.class);
        ObjectNode schema = generator.schemaForMethod(addMethod);

        assertEquals("object", schema.path("type").asText());
        assertEquals("integer", schema.path("properties").path("x").path("type").asText());
        assertEquals("First number", schema.path("properties").path("x").path("description").asText());
        assertEquals("integer", schema.path("properties").path("y").path("type").asText());
        assertFalse(schema.path("properties").has("context"));

        // Both x and y are required
        assertEquals(2, schema.path("required").size());
    }

    @Test
    void generatesSchemaForRecordType() {
        ObjectNode schema = generator.schemaForType(ReviewResult.class);

        assertEquals("object", schema.path("type").asText());
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals("string", schema.path("properties").path("verdict").path("type").asText());
        assertEquals("integer", schema.path("properties").path("score").path("type").asText());
        assertEquals("array", schema.path("properties").path("points").path("type").asText());
        assertEquals("string", schema.path("properties").path("points").path("items").path("type").asText());
        assertEquals(3, schema.path("required").size());
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}
