package io.opencode4j.agent.runtime.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ResultSpec;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** One long-lived server shared by many sessions, as Alfred runs its {@code alfred_*} tools. */
class SharedJvmMcpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private JvmMcpServer server;

    public static class Catalog {
        final AtomicReference<ToolContext> lastContext = new AtomicReference<>();

        @Tool(name = "alfred_catalog_lookup", description = "Looks up a service")
        public String lookup(@Param(name = "name") String name, ToolContext context) {
            lastContext.set(context);
            return "cluster of " + name;
        }

        @Tool(description = "Fails with internal details")
        public String broken() {
            throw new IllegalStateException("db password rejected");
        }

        @Tool(description = "Sleeps")
        public String slow(ToolContext context) throws InterruptedException {
            Thread.sleep(5_000);
            return "late";
        }
    }

    record Verdict(String answer) {}

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private JvmMcpServer.JvmMcpServerBuilder builder(Catalog catalog) {
        return JvmMcpServer.builder()
                .serverId("alfred")
                .javaTools(List.of(JavaMethodToolSpec.of(catalog)))
                .mapper(mapper);
    }

    @Test
    void servesAFixedPortPathAndTokenReferencedThroughEnvironmentVariables() throws Exception {
        // Given
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = builder(new Catalog()).port(port).path("/mcp").token("boot-token").build();

        // When
        McpToolSpec spec = server.asMcpToolSpec("alfred", "ALFRED_MCP_PORT", "ALFRED_MCP_TOKEN");

        // Then
        assertEquals(port, server.port());
        assertEquals("http://127.0.0.1:{env:ALFRED_MCP_PORT}/mcp", spec.url());
        assertEquals("Bearer {env:ALFRED_MCP_TOKEN}", spec.headers().get("Authorization"));
        assertEquals(200, post(server.url(), "Bearer boot-token", rpc("tools/list", "{}")).statusCode());
        assertEquals(401, post(server.url(), "Bearer boot-tokeN", rpc("tools/list", "{}")).statusCode());
        assertEquals(401, post(server.url(), null, rpc("tools/list", "{}")).statusCode());
    }

    @Test
    void toolContextNamesTheRunRootSessionAndAgentOfTheCaller() throws Exception {
        // Given: a sub-agent's child session calls the tool
        Catalog catalog = new Catalog();
        server = builder(catalog).resolver(sessionId -> new ToolCaller("run_7", sessionId, "ses_root",
                "logs-analyst", "sre-debugger/logs-analyst")).build();

        // When
        JsonNode result = call("alfred_catalog_lookup", "{\"name\":\"theo\"}", "ses_child");

        // Then
        assertEquals("cluster of theo", result.path("content").get(0).path("text").asText());
        ToolContext context = catalog.lastContext.get();
        assertEquals("run_7", context.runId());
        assertEquals("ses_child", context.sessionId());
        assertEquals("ses_root", context.rootSessionId());
        assertEquals("logs-analyst", context.agentName());
        assertEquals("sre-debugger/logs-analyst", context.agentPath());
    }

    @Test
    void keepsStructuredResultsPerRootSession() throws Exception {
        // Given
        server = JvmMcpServer.builder().mapper(mapper).resultSpec(ResultSpec.of(Verdict.class, "verdict"))
                .resolver(sessionId -> new ToolCaller("r", sessionId, sessionId.replace("-child", ""), "", ""))
                .build();

        // When
        call("submit_result", "{\"answer\":\"a\"}", "ses_a-child");
        call("submit_result", "{\"answer\":\"b\"}", "ses_b");

        // Then
        assertEquals(new Verdict("a"), server.takeSubmittedResult("ses_a"));
        assertNull(server.takeSubmittedResult("ses_a"), "taking a result removes it");
        assertEquals(new Verdict("b"), server.takeSubmittedResult("ses_b"));
    }

    @Test
    void toolFailuresReachTheModelWithoutExceptionClassNames() throws Exception {
        server = builder(new Catalog()).build();

        JsonNode result = call("broken", "{}", "s");

        assertTrue(result.path("isError").asBoolean());
        String text = result.path("content").get(0).path("text").asText();
        assertEquals("db password rejected", text);
        assertFalse(text.contains("IllegalStateException"));
    }

    @Test
    void rejectsMissingRequiredArgumentsBeforeInvoking() throws Exception {
        Catalog catalog = new Catalog();
        server = builder(catalog).build();

        JsonNode result = call("alfred_catalog_lookup", "{}", "s");

        assertTrue(result.path("isError").asBoolean());
        assertEquals("Missing required argument: name", result.path("content").get(0).path("text").asText());
        assertNull(catalog.lastContext.get());
    }

    @Test
    void cancelsToolsThatExceedTheTimeout() throws Exception {
        server = builder(new Catalog()).toolTimeout(Duration.ofMillis(200)).build();

        long start = System.nanoTime();
        JsonNode result = call("slow", "{}", "s");

        assertTrue(result.path("isError").asBoolean());
        assertTrue(result.path("content").get(0).path("text").asText().contains("timed out"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3_000);
    }

    @Test
    void rejectsOversizedRequestBodies() throws Exception {
        server = builder(new Catalog()).maxRequestBytes(64).build();

        HttpResponse<String> response = post(server.url(), "Bearer " + server.token(),
                rpc("tools/call", "{\"name\":\"alfred_catalog_lookup\",\"arguments\":{\"name\":\"" + "x".repeat(200) + "\"}}"));

        assertEquals(413, response.statusCode());
    }

    @Test
    void appliesTheOutputPolicyAndBoundsTheInvocationLog() throws Exception {
        server = builder(new Catalog()).outputPolicy(ToolOutputPolicy.truncateAt(5)).invocationLogSize(2).build();

        JsonNode result = call("alfred_catalog_lookup", "{\"name\":\"theo\"}", "s");
        call("alfred_catalog_lookup", "{\"name\":\"a\"}", "s");
        call("alfred_catalog_lookup", "{\"name\":\"b\"}", "s");

        assertTrue(result.path("content").get(0).path("text").asText().startsWith("clust\n[output truncated"));
        assertEquals(2, server.invocationLog().size());
        assertTrue(server.invocationLog().getLast().argumentsJson().contains("\"b\""));
    }

    private JsonNode call(String tool, String arguments, String sessionId) throws Exception {
        String params = "{\"name\":\"" + tool + "\",\"arguments\":" + arguments
                + ",\"_meta\":{\"ai.opencode/sessionID\":\"" + sessionId + "\"}}";
        HttpResponse<String> response = post(server.url(), "Bearer " + server.token(), rpc("tools/call", params));
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body()).path("result");
    }

    private static String rpc(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    private HttpResponse<String> post(String url, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
