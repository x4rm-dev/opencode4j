package io.opencode4j.agent.runtime.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.ResultSpec;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JvmMcpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public static class CalculatorService {
        @Tool(description = "Adds two integers")
        public int add(
                @Param(name = "a") int a,
                @Param(name = "b") int b,
                ToolContext context
        ) {
            assertNotNull(context.sessionId());
            return a + b;
        }

        @Tool(description = "Throws an error")
        public String blowUp() {
            throw new IllegalStateException("Simulated tool crash");
        }

        @Tool(description = "Generates a chart")
        public io.opencode4j.runtime.media.MediaResult chart(@Param(name = "title") String title) {
            return io.opencode4j.runtime.media.MediaResult.image(
                    new byte[] { 0x1, 0x2, 0x3 }, "image/png", title + ".png", "Generated chart");
        }
    }

    record MathAnswer(int sum, String note) {}

    private JvmMcpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new JvmMcpServer(
                "test-run",
                List.of(JavaMethodToolSpec.of(new CalculatorService())),
                ResultSpec.of(MathAnswer.class, "Final math submission"),
                mapper
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.url()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    void listsRegisteredJavaToolsAndResultTool() throws Exception {
        JsonNode resp = sendRpc("tools/list", null);
        JsonNode tools = resp.path("result").path("tools");
        assertTrue(tools.isArray());
        assertEquals(4, tools.size()); // add, blowUp, chart, submit_result

        boolean hasAdd = false;
        boolean hasSubmit = false;
        boolean hasChart = false;
        for (JsonNode t : tools) {
            if ("add".equals(t.path("name").asText())) hasAdd = true;
            if ("submit_result".equals(t.path("name").asText())) hasSubmit = true;
            if ("chart".equals(t.path("name").asText())) hasChart = true;
        }
        assertTrue(hasAdd);
        assertTrue(hasSubmit);
        assertTrue(hasChart);
    }

    @Test
    void executesJavaMethodToolWithTypedArgumentsAndSessionMeta() throws Exception {
        String args = "{\"a\": 25, \"b\": 17}";
        String params = "{\"name\":\"add\",\"arguments\":" + args + ",\"_meta\":{\"ai.opencode/sessionID\":\"ses_123\"}}";

        JsonNode resp = sendRpc("tools/call", params);
        JsonNode content = resp.path("result").path("content");
        assertEquals("42", content.get(0).path("text").asText());

        List<JvmMcpServer.ToolInvocationRecord> log = server.invocationLog();
        assertEquals(1, log.size());
        assertEquals("add", log.get(0).toolName());
        assertEquals("ses_123", log.get(0).sessionId());
        assertEquals("42", log.get(0).resultText());
        assertFalse(log.get(0).isError());
    }

    @Test
    void handlesJavaMethodExceptionsReturningErrorContent() throws Exception {
        String params = "{\"name\":\"blowUp\",\"arguments\":{}}";
        JsonNode resp = sendRpc("tools/call", params);
        assertTrue(resp.path("result").path("isError").asBoolean());
        assertTrue(resp.path("result").path("content").get(0).path("text").asText().contains("Simulated tool crash"));
    }

    @Test
    void handlesResultSubmissionToolCapturingTypedResult() throws Exception {
        String args = "{\"sum\": 42, \"note\": \"Calculated correctly\"}";
        String params = "{\"name\":\"submit_result\",\"arguments\":" + args + "}";

        JsonNode resp = sendRpc("tools/call", params);
        assertEquals("accepted", resp.path("result").path("content").get(0).path("text").asText());

        Object result = server.submittedResult();
        assertNotNull(result);
        assertTrue(result instanceof MathAnswer);
        MathAnswer mathAnswer = (MathAnswer) result;
        assertEquals(42, mathAnswer.sum());
        assertEquals("Calculated correctly", mathAnswer.note());
    }

    @Test
    void handlesToolsReturningMediaResultAsMcpImageContent() throws Exception {
        String params = "{\"name\":\"chart\",\"arguments\":{\"title\":\"cpu\"}}";
        JsonNode resp = sendRpc("tools/call", params);
        JsonNode content = resp.path("result").path("content");
        assertTrue(content.isArray());
        assertEquals(2, content.size()); // image part + text caption part

        JsonNode imagePart = content.get(0);
        assertEquals("image", imagePart.path("type").asText());
        assertEquals("image/png", imagePart.path("mimeType").asText());
        assertNotNull(imagePart.path("data").asText());

        JsonNode textPart = content.get(1);
        assertEquals("text", textPart.path("type").asText());
        assertEquals("Generated chart", textPart.path("text").asText());
    }

    private JsonNode sendRpc(String method, String paramsJson) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"req_1\",\"method\":\"" + method + "\""
                + (paramsJson != null ? ",\"params\":" + paramsJson : "") + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.url()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + server.token())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body());
    }
}
