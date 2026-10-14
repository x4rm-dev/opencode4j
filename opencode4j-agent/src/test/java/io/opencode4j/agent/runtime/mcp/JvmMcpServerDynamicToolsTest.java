package io.opencode4j.agent.runtime.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.runtime.media.MediaResult;
import io.opencode4j.runtime.spec.DynamicToolSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.tool.DynamicToolProvider;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JvmMcpServerDynamicToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Provider whose tool list can be mutated between requests, recording every call it receives. */
    private static final class MutableProvider implements DynamicToolProvider {
        final List<ToolDefinition> definitions = new CopyOnWriteArrayList<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile String sessionSeen;

        @Override
        public List<ToolDefinition> listTools() {
            return new ArrayList<>(definitions);
        }

        @Override
        public Object call(String toolName, Map<String, Object> arguments, ToolContext context) {
            calls.add(toolName + arguments);
            sessionSeen = context.sessionId();
            return switch (toolName) {
                case "screenshot" -> MediaResult.image(new byte[] {1, 2, 3}, "image/png", "s.png", "frame 7");
                case "click" -> "clicked " + arguments.get("x") + "," + arguments.get("y");
                case "boom" -> throw new IllegalStateException("device offline");
                default -> "ok";
            };
        }
    }

    public static class StaticTools {
        @Tool(description = "static tool")
        public String click() {
            return "static-click";
        }
    }

    private final MutableProvider provider = new MutableProvider();
    private JvmMcpServer server;

    @BeforeEach
    void setUp() throws Exception {
        provider.definitions.add(new DynamicToolProvider.ToolDefinition("screenshot", "Capture screen", null));
        provider.definitions.add(new DynamicToolProvider.ToolDefinition("click", "Click",
                Map.of("type", "object", "properties", Map.of("x", Map.of("type", "integer")))));
        provider.definitions.add(new DynamicToolProvider.ToolDefinition("boom", "Always fails", null));
        server = new JvmMcpServer("dyn-run",
                List.of(JavaMethodToolSpec.of(new StaticTools())),
                List.of(DynamicToolSpec.of("computer", "Computer tools", provider)),
                null, mapper);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void listsDynamicToolsWithTheirSchemasAndLetsStaticToolsWinNameCollisions() throws Exception {
        JsonNode tools = rpc("tools/list", null).path("result").path("tools");

        // static "click" wins; the dynamic "click" is ignored
        assertEquals(3, tools.size());
        JsonNode screenshot = find(tools, "screenshot");
        assertEquals("object", screenshot.path("inputSchema").path("type").asText());
        assertEquals("static tool", find(tools, "click").path("description").asText());

        JsonNode result = rpc("tools/call", "{\"name\":\"click\",\"arguments\":{}}").path("result");
        assertEquals("static-click", result.path("content").get(0).path("text").asText());
        assertTrue(provider.calls.isEmpty());
    }

    @Test
    void routesCallsToTheProviderWithDecodedArgumentsAndSessionContext() throws Exception {
        provider.definitions.removeIf(d -> d.name().equals("click"));
        provider.definitions.add(new DynamicToolProvider.ToolDefinition("tap", "Tap", null));

        JsonNode result = rpc("tools/call", "{\"name\":\"tap\",\"arguments\":{\"x\":3},"
                + "\"_meta\":{\"ai.opencode/sessionID\":\"ses_9\"}}").path("result");

        assertEquals("ok", result.path("content").get(0).path("text").asText());
        assertEquals(List.of("tap{x=3}"), provider.calls);
        assertEquals("ses_9", provider.sessionSeen);
        assertEquals("tap", server.invocationLog().get(0).toolName());
    }

    @Test
    void returnsMediaResultsAsImageContentWithCaption() throws Exception {
        JsonNode content = rpc("tools/call", "{\"name\":\"screenshot\",\"arguments\":{}}")
                .path("result").path("content");

        assertEquals(2, content.size());
        assertEquals("image", content.get(0).path("type").asText());
        assertEquals("image/png", content.get(0).path("mimeType").asText());
        assertEquals("frame 7", content.get(1).path("text").asText());
    }

    @Test
    void reportsProviderExceptionsAsToolErrors() throws Exception {
        JsonNode result = rpc("tools/call", "{\"name\":\"boom\",\"arguments\":{}}").path("result");

        assertTrue(result.path("isError").asBoolean());
        assertTrue(result.path("content").get(0).path("text").asText().contains("device offline"));
        assertTrue(server.invocationLog().get(0).isError());
    }

    @Test
    void picksUpToolsAddedAfterStartupWithoutAnotherListCall() throws Exception {
        provider.definitions.add(new DynamicToolProvider.ToolDefinition("late", "Added later", null));

        JsonNode result = rpc("tools/call", "{\"name\":\"late\",\"arguments\":{}}").path("result");

        assertFalse(result.path("isError").asBoolean());
        assertEquals("ok", result.path("content").get(0).path("text").asText());
    }

    @Test
    void rejectsUnknownTools() throws Exception {
        JsonNode result = rpc("tools/call", "{\"name\":\"nope\",\"arguments\":{}}").path("result");

        assertTrue(result.path("isError").asBoolean());
        assertTrue(result.path("content").get(0).path("text").asText().contains("Unknown tool"));
    }

    private static JsonNode find(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("tool not listed: " + name);
    }

    private JsonNode rpc(String method, String paramsJson) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"req_1\",\"method\":\"" + method + "\""
                + (paramsJson != null ? ",\"params\":" + paramsJson : "") + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.url()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + server.token())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }
}
