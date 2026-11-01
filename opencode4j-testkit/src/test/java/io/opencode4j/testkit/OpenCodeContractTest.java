package io.opencode4j.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.client.event.ConnectionState;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.message.AssistantMessage;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.SessionOptions;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.WorkspaceValidation;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.PolicyRule;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.SecretRef;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs opencode4j against a real {@code opencode serve} and a scripted model, the way Alfred uses it: one release
 * workspace with deny-all permissions, a shared in-JVM tool server referenced through {@code {env:}}, and
 * credentials that never touch the disk. Skipped when no {@code opencode} binary is installed.
 */
@Tag("contract")
@EnabledIf("opencodeInstalled")
class OpenCodeContractTest {

    private static final String TOOLS_PORT = "ALFRED_MCP_PORT";
    private static final String TOOLS_TOKEN = "ALFRED_MCP_TOKEN";
    private static final String LLM_KEY = "FAKE_LLM_KEY";
    private static final Duration TURN = Duration.ofSeconds(60);

    static FakeLlmServer llm;
    static OpenCodeTestServer opencode;
    static OpenCodeClient client;
    static OpenCodeAgentRuntime runtime;
    static JvmMcpServer tools;
    static Workspace workspace;
    static Path workspaces;
    static final Catalog CATALOG = new Catalog();

    static boolean opencodeInstalled() {
        return OpenCodeTestServer.findBinary().isPresent();
    }

    /** A stand-in for Alfred's catalog lookup tool. */
    public static final class Catalog {
        final AtomicReference<ToolContext> lastCall = new AtomicReference<>();

        @Tool(name = "catalog_lookup", description = "Looks up a service's cluster")
        public String lookup(@Param(name = "name") String name, ToolContext context) {
            lastCall.set(context);
            return "cluster of " + name;
        }
    }

    @BeforeAll
    static void startServers() throws Exception {
        llm = FakeLlmServer.start();
        int toolsPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            toolsPort = probe.getLocalPort();
        }
        String toolsToken = UUID.randomUUID().toString();
        opencode = OpenCodeTestServer.start(Map.of(
                TOOLS_PORT, String.valueOf(toolsPort),
                TOOLS_TOKEN, toolsToken,
                LLM_KEY, "fake-key"));
        client = opencode.newClient();
        workspaces = Files.createTempDirectory("opencode4j-contract-");
        runtime = OpenCodeAgentRuntime.builder(client).baseWorkspacesDir(workspaces)
                .warmupTimeout(Duration.ofSeconds(30)).build();
        tools = JvmMcpServer.builder().serverId("alfred").path("/mcp").port(toolsPort).token(toolsToken)
                .javaTools(java.util.List.of(JavaMethodToolSpec.of(CATALOG)))
                .resolver(runtime.toolCallResolver())
                .build();
        workspace = runtime.materialize(release());
    }

    @AfterAll
    static void stopServers() {
        if (runtime != null) {
            runtime.close();
        }
        if (tools != null) {
            tools.close();
        }
        if (client != null) {
            client.close();
        }
        if (opencode != null) {
            opencode.close();
        }
        if (llm != null) {
            llm.close();
        }
    }

    @BeforeEach
    void resetModel() {
        llm.reset();
        CATALOG.lastCall.set(null);
    }

    static WorkspaceSpec release() {
        ProviderSpec fake = ProviderSpec.builder("fake").baseUrl(llm.baseUrl())
                .apiKey(SecretRef.env(LLM_KEY))
                .model(ProviderSpec.ModelSpec.toolCalling(FakeLlmServer.MODEL_ID, 100_000, 4_096))
                .build();
        AgentSpec sre = AgentSpec.builder("sre").model("fake", FakeLlmServer.MODEL_ID)
                .permission(PermissionRule.allow("alfred_*", "*"))
                .build();
        return WorkspaceSpec.builder("debugging/rel_contract", sre)
                .provider(fake)
                .mcpServer(tools.asMcpToolSpec("alfred", TOOLS_PORT, TOOLS_TOKEN))
                .smallModel("fake/" + FakeLlmServer.MODEL_ID)
                .instruction("timezone.md", "Times are in IST unless stated otherwise.")
                .policy(PolicyRule.denyTool("shell"))
                .enabledProvider("fake")
                .build();
    }

    private static AgentSession open() {
        return runtime.open(workspace, SessionOptions.builder().title("contract")
                .metadata("conversationId", UUID.randomUUID().toString()).build());
    }

    private static AgentResult await(AgentRun run) throws Exception {
        return run.result().get(TURN.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void theReleaseWorkspaceValidates() {
        WorkspaceValidation validation = runtime.validate(workspace);

        assertTrue(validation.valid(), validation.problems().toString());
        assertTrue(validation.warnings().isEmpty(), validation.warnings().toString());
        assertTrue(runtime.materialize(release()) == workspace, "an identical spec is not rewritten");
    }

    @Test
    void answersAndRecordsTheTurnInHistory() throws Exception {
        try (AgentSession session = open()) {
            AgentResult result = await(session.send("Was Theo timing out?"));

            assertEquals(RunStatus.SUCCEEDED, result.status(), result.errorMessage());
            assertEquals(FakeLlmServer.DEFAULT_ANSWER, result.text());
            assertTrue(result.tokenUsage().total() > 0);
            assertEquals(ConnectionState.IDLE, client.eventBus().state(), "no run holds the stream");
            assertTrue(client.messages().list(session.sessionId(), PageRequest.first(10)).items().stream()
                    .anyMatch(message -> message instanceof AssistantMessage assistant
                            && FakeLlmServer.DEFAULT_ANSWER.equals(assistant.text())));
        }
    }

    @Test
    void offersOnlyTheAllowedToolsToTheModel() throws Exception {
        try (AgentSession session = open()) {
            await(session.send("hello"));

            var offered = llm.lastOfferedTools();
            assertTrue(offered.contains("alfred_catalog_lookup"), offered.toString());
            for (String restricted : new String[] {"shell", "execute", "edit", "write", "webfetch", "websearch",
                    "question", "read"}) {
                assertFalse(offered.contains(restricted), restricted + " was offered: " + offered);
            }
        }
    }

    @Test
    void callsTheSharedToolWithTheCallersContext() throws Exception {
        llm.enqueue(new FakeLlmServer.ToolCall("catalog_lookup", "{\"name\":\"theo\"}"));
        try (AgentSession session = open()) {
            AgentResult result = await(session.send("Which cluster is Theo on?"));

            assertEquals(RunStatus.SUCCEEDED, result.status(), result.errorMessage());
            ToolContext call = CATALOG.lastCall.get();
            assertNotNull(call, "the tool server was reached through its {env:} reference");
            assertEquals(session.sessionId(), call.rootSessionId());
            assertEquals(result.runId(), call.runId());
            assertEquals("sre", call.agentName());
            assertEquals(1, result.toolExecutions().size());
            assertEquals("cluster of theo", result.toolExecutions().getFirst().result());
        }
    }

    @Test
    void cancelsALongTurnAndTheNextTurnWaitsForTheServer() throws Exception {
        llm.enqueue(new FakeLlmServer.Delayed(Duration.ofSeconds(20), new FakeLlmServer.Text("too late")));
        try (AgentSession session = open()) {
            AgentRun slow = session.send("take your time");
            long deadline = System.nanoTime() + TURN.toNanos();
            while (llm.agentSteps() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }

            slow.cancel();
            AgentResult cancelled = await(slow);
            AgentResult next = await(session.send("are you there?"));

            assertEquals(RunStatus.CANCELLED, cancelled.status());
            assertEquals(RunStatus.SUCCEEDED, next.status(), next.errorMessage());
            assertEquals(FakeLlmServer.DEFAULT_ANSWER, next.text());
        }
    }

    @Test
    void classifiesARejectedProviderCredential() throws Exception {
        llm.enqueue(new FakeLlmServer.Failure(401,
                "{\"error\":{\"message\":\"invalid api key\",\"type\":\"invalid_request_error\"}}", null));
        try (AgentSession session = open()) {
            AgentResult result = await(session.send("hello"));

            assertEquals(RunStatus.FAILED, result.status());
            assertEquals(FailureCategory.LLM_AUTH, result.failureCategory(), result.errorMessage());
        }
    }

    @Test
    void detachedSessionsSurviveAndCanBeResumedThenDeleted() throws Exception {
        AgentSession session = open();
        String sessionId = session.sessionId();
        await(session.send("first"));
        session.close();

        assertEquals(sessionId, client.sessions().get(sessionId).id());
        AgentSession resumed = runtime.resume(workspace, sessionId, SessionOptions.defaults());
        assertEquals(RunStatus.SUCCEEDED, await(resumed.send("second")).status());
        resumed.delete();
        assertTrue(client.sessions().activeSessionIds().stream().noneMatch(sessionId::equals));
    }
}
