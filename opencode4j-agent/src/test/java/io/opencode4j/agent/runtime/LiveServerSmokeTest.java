package io.opencode4j.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.client.event.ConnectionState;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.message.AssistantMessage;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.ProviderSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end turn against a real {@code opencode serve} and a stub OpenAI-compatible model that answers
 * {@code pong}. Skipped unless {@code OPENCODE4J_LIVE_URL} (server) and {@code OPENCODE4J_LIVE_LLM_URL}
 * (stub, including {@code /v1}) are set; {@code OPENCODE4J_LIVE_PASSWORD} is the server password.
 */
@EnabledIfEnvironmentVariable(named = "OPENCODE4J_LIVE_URL", matches = ".+")
class LiveServerSmokeTest {

    @Test
    void runsTwoTurnsOverTheSharedEventBus(@TempDir Path workspaces) throws Exception {
        OpenCodeConfig config = OpenCodeConfig.builder(System.getenv("OPENCODE4J_LIVE_URL"))
                .servePassword(System.getenv("OPENCODE4J_LIVE_PASSWORD"))
                .sseReadTimeoutMs(30_000)
                .build();
        ProviderSpec stub = ProviderSpec.openAiCompatible("stub", System.getenv("OPENCODE4J_LIVE_LLM_URL"),
                () -> Map.of("Authorization", "Bearer stub"),
                List.of(new ProviderSpec.ModelSpec("fake-1", "fake", true, false, 100_000, 4_096)));
        try (OpenCodeClient client = OpenCodeClient.create(config);
             OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                     .baseWorkspacesDir(workspaces)
                     .warmupTimeout(Duration.ofSeconds(20))
                     .providers(List.of(stub))
                     .deleteWorkspaceOnClose(true)
                     .build();
             AgentSession session = runtime.open(AgentSpec.builder("smoke").model("stub/fake-1").build())) {

            AgentResult first = session.send("ping").result().get(60, TimeUnit.SECONDS);
            AgentResult second = session.send("ping again").result().get(60, TimeUnit.SECONDS);

            assertTrue(first.isSuccess(), first.errorMessage());
            assertEquals("pong", first.text());
            assertTrue(second.isSuccess(), second.errorMessage());
            assertEquals("pong", second.text());
            assertEquals(ConnectionState.IDLE, client.eventBus().state(), "the last run released the stream");
            var history = client.messages().list(session.sessionId(), PageRequest.first(10)).items();
            assertTrue(history.stream().anyMatch(m -> m instanceof AssistantMessage a && "pong".equals(a.text())));
        }
    }

    /** Records what the in-JVM tool sees. */
    public static final class Catalog {
        final java.util.concurrent.atomic.AtomicReference<io.opencode4j.runtime.tool.ToolContext> seen =
                new java.util.concurrent.atomic.AtomicReference<>();

        @io.opencode4j.runtime.tool.Tool(name = "catalog_lookup", description = "Looks up a service")
        public String lookup(@io.opencode4j.runtime.tool.Param(name = "name") String name,
                             io.opencode4j.runtime.tool.ToolContext context) {
            seen.set(context);
            return "cluster of " + name;
        }
    }

    /**
     * A release workspace with deny-all permissions and a shared tool server referenced through environment
     * variables. Needs {@code OPENCODE4J_LIVE_MCP_PORT} / {@code OPENCODE4J_LIVE_MCP_TOKEN}, set as
     * {@code ALFRED_MCP_PORT} / {@code ALFRED_MCP_TOKEN} on the OpenCode process too.
     */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OPENCODE4J_LIVE_MCP_PORT", matches = ".+")
    void sharedReleaseWorkspaceWithDenyAllAndASharedToolServer(@TempDir Path workspaces) throws Exception {
        OpenCodeConfig config = OpenCodeConfig.builder(System.getenv("OPENCODE4J_LIVE_URL"))
                .servePassword(System.getenv("OPENCODE4J_LIVE_PASSWORD"))
                .sseReadTimeoutMs(30_000)
                .build();
        ProviderSpec stub = ProviderSpec.openAiCompatible("stub", System.getenv("OPENCODE4J_LIVE_LLM_URL"),
                () -> Map.of("Authorization", "Bearer stub"),
                List.of(new ProviderSpec.ModelSpec("fake-1", "fake", true, false, 100_000, 4_096)));
        Catalog catalog = new Catalog();
        try (OpenCodeClient client = OpenCodeClient.create(config);
             OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                     .baseWorkspacesDir(workspaces).warmupTimeout(Duration.ofSeconds(20)).build();
             io.opencode4j.agent.runtime.mcp.JvmMcpServer tools = io.opencode4j.agent.runtime.mcp.JvmMcpServer.builder()
                     .serverId("alfred").path("/mcp")
                     .port(Integer.parseInt(System.getenv("OPENCODE4J_LIVE_MCP_PORT")))
                     .token(System.getenv("OPENCODE4J_LIVE_MCP_TOKEN"))
                     .javaTools(List.of(io.opencode4j.runtime.spec.JavaMethodToolSpec.of(catalog)))
                     .resolver(runtime.toolCallResolver())
                     .build()) {

            var spec = io.opencode4j.runtime.spec.WorkspaceSpec.builder("debugging/rel_live",
                            AgentSpec.builder("sre").model("stub/fake-1")
                                    .permission(io.opencode4j.runtime.spec.PermissionRule.allow("alfred_*", "*"))
                                    .build())
                    .provider(stub)
                    .mcpServer(tools.asMcpToolSpec("alfred", "ALFRED_MCP_PORT", "ALFRED_MCP_TOKEN"))
                    .smallModel("stub/fake-1")
                    .instruction("timezone.md", "Use IST.")
                    .policy(io.opencode4j.runtime.spec.PolicyRule.denyTool("shell"))
                    .enabledProvider("stub")
                    .build();
            var workspace = runtime.materialize(spec);
            assertTrue(runtime.materialize(spec) == workspace, "materializing again is a no-op");

            String effective = client.transport().sendForTree("GET",
                    client.transport().withLocation("/api/config", workspace.directory().toString()), null).toString();
            System.out.println("[live] effective config: " + effective);
            assertTrue(effective.contains("\"permissions\":[{\"action\":\"*\",\"resource\":\"*\",\"effect\":\"deny\"},"
                    + "{\"action\":\"alfred_*\",\"resource\":\"*\",\"effect\":\"allow\"}]"),
                    "the agent's rules start with deny-all");
            assertTrue(effective.contains("shell:*"), "the shell policy is in effect");

            var session = runtime.open(workspace, io.opencode4j.runtime.SessionOptions.builder()
                    .title("live").metadata("conversationId", "c-live").build());
            AgentResult result = session.send("CALL:catalog_lookup").result().get(60, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), result.errorMessage());
            assertEquals("pong", result.text());
            var context = catalog.seen.get();
            assertTrue(context != null, "the shared tool server was called through the {env:} reference");
            assertEquals(session.sessionId(), context.rootSessionId());
            assertEquals(result.runId(), context.runId());
            assertEquals("sre", context.agentName());

            String sessionId = session.sessionId();
            session.close();
            assertEquals(sessionId, client.sessions().get(sessionId).id(), "closing detaches; the session survives");
            var resumed = runtime.resume(workspace, sessionId, io.opencode4j.runtime.SessionOptions.defaults());
            assertTrue(resumed.send("ping").result().get(60, TimeUnit.SECONDS).isSuccess());
            resumed.delete();
        }
    }
}
