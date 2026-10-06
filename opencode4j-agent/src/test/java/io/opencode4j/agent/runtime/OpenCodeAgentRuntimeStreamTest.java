package io.opencode4j.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.DelegationCompletedEvent;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.PermissionRequestedEvent;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunFailedEvent;
import io.opencode4j.runtime.event.RunInterruptedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.event.ToolCallFailedEvent;
import io.opencode4j.runtime.event.ToolCallStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end event stream tests for {@link OpenCodeAgentRuntime} against MockWebServer,
 * verifying unmasking, hierarchy, child sessions, failures, and permission replies.
 */
class OpenCodeAgentRuntimeStreamTest {

    private MockWebServer server;
    private OpenCodeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        OpenCodeConfig config = OpenCodeConfig.builder(server.url("/").toString())
                .connectTimeoutMs(2000)
                .build();
        client = OpenCodeClient.create(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void subAgentFinishingDoesNotEndTheParentRun(@TempDir Path tempDir) {
        // Child session finishes first; only root execution.succeeded ends the run
        String sse = "data: {\"type\":\"server.connected\"}\n\n"
                + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n"
                + "data: {\"type\":\"session.created\",\"data\":{\"sessionID\":\"ses_child\",\"parentID\":\"ses_root\",\"title\":\"worker\"}}\n\n"
                + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_child\"}}\n\n"
                + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_child\"}}\n\n"
                + "data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"ordinal\":0,\"delta\":\"Child finished, now parent done\"}}\n\n"
                + "data: {\"type\":\"session.step.ended\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"finish\":\"stop\",\"cost\":0.005,\"tokens\":{\"input\":100,\"output\":20,\"reasoning\":0}}}\n\n"
                + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n";

        configureServer(sse);

        AgentSpec worker = AgentSpec.builder("worker").build();
        AgentSpec boss = AgentSpec.builder("boss").subAgent(worker).build();

        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        try (AgentSession session = runtime.open(boss)) {
            AgentRun run = session.send("Delegate to child");
            AgentResult result = run.await();

            assertTrue(result.isSuccess());
            assertEquals("Child finished, now parent done", result.text());
            assertEquals(120, result.tokenUsage().total());
        }

        runtime.close();
    }

    @Test
    void answersPermissionRequestViaResponder(@TempDir Path tempDir) throws InterruptedException {
        String sse = "data: {\"type\":\"server.connected\"}\n\n"
                + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n"
                + "data: {\"type\":\"permission.asked\",\"data\":{\"id\":\"req_perm_1\",\"sessionID\":\"ses_root\",\"action\":\"shell\",\"resources\":[\"rm -rf /tmp/foo\"]}}\n\n"
                + "data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"ordinal\":0,\"delta\":\"Permission granted, done\"}}\n\n"
                + "data: {\"type\":\"session.step.ended\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"finish\":\"stop\",\"cost\":0.001,\"tokens\":{\"input\":10,\"output\":10,\"reasoning\":0}}}\n\n"
                + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n";

        configureServer(sse);

        AgentSpec spec = AgentSpec.builder("boss")
                .permission(PermissionRule.ask("shell", "*"))
                .build();

        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        AtomicBoolean permissionAsked = new AtomicBoolean(false);
        AgentEventListener listener = event -> {
            if (event instanceof PermissionRequestedEvent p) {
                permissionAsked.set(true);
                p.allowOnce();
            }
        };

        try (AgentSession session = runtime.open(spec)) {
            AgentRun run = session.send("Run shell command", listener);
            AgentResult result = run.await();

            assertTrue(result.isSuccess());
            assertTrue(permissionAsked.get());
        }

        runtime.close();

        // Verify that the permission reply was sent to the server
        boolean sawReply = false;
        for (int i = 0; i < server.getRequestCount(); i++) {
            RecordedRequest req = server.takeRequest();
            if (req.getPath() != null && req.getPath().contains("/permission/req_perm_1/reply")) {
                sawReply = true;
                assertTrue(req.getBody().readUtf8().contains("\"decision\":\"once\""));
                break;
            }
        }
        assertTrue(sawReply);
    }

    @Test
    void reportsFailedAndInterruptedRunsAccurately(@TempDir Path tempDir) {
        String failedSse = "data: {\"type\":\"server.connected\"}\n\n"
                + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n"
                + "data: {\"type\":\"session.execution.failed\",\"data\":{\"sessionID\":\"ses_root\",\"error\":{\"message\":\"Rate limit exceeded\"}}}\n\n";

        configureServer(failedSse);

        AgentSpec spec = AgentSpec.builder("boss").build();
        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        try (AgentSession session = runtime.open(spec)) {
            AgentRun run = session.send("Fail me", events::add);
            AgentResult result = run.await();

            assertFalse(result.isSuccess());
            assertTrue(result.errorMessage().contains("Rate limit exceeded"));
            assertTrue(events.stream().anyMatch(e -> e instanceof RunFailedEvent));
        }

        runtime.close();
    }

    @Test
    void unmasksMcpToolCallsAndTracksDelegations(@TempDir Path tempDir) {
        String sse = "data: {\"type\":\"server.connected\"}\n\n"
                + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n"
                + "data: {\"type\":\"session.tool.input.started\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"id\":\"c1\",\"name\":\"execute\"}}\n\n"
                + "data: {\"type\":\"session.tool.called\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"id\":\"c1\",\"input\":{\"code\":\"return await tools.vault.vault_code({key:\\\"alpha\\\"});\"}}}\n\n"
                + "data: {\"type\":\"session.tool.success\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"id\":\"c1\",\"content\":[{\"type\":\"text\",\"text\":\"SECRET-123\"}]}}\n\n"
                + "data: {\"type\":\"session.step.ended\",\"data\":{\"sessionID\":\"ses_root\",\"assistantMessageID\":\"m1\",\"finish\":\"stop\",\"cost\":0.001,\"tokens\":{\"input\":10,\"output\":10,\"reasoning\":0}}}\n\n"
                + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_root\"}}\n\n";

        configureServer(sse);

        AgentSpec spec = AgentSpec.builder("boss").build();
        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        try (AgentSession session = runtime.open(spec)) {
            AgentRun run = session.send("Get vault code", events::add);
            AgentResult result = run.await();

            assertTrue(result.isSuccess());
            assertEquals(1, result.toolExecutions().size());
            assertEquals("vault.vault_code", result.toolExecutions().get(0).toolName());
            assertEquals("SECRET-123", result.toolExecutions().get(0).result());

            assertTrue(events.stream().anyMatch(e -> e instanceof ToolCallStartedEvent tc && "vault.vault_code".equals(tc.toolName())));
            assertTrue(events.stream().anyMatch(e -> e instanceof ToolCallCompletedEvent tc && "vault.vault_code".equals(tc.toolName())));
        }

        runtime.close();
    }

    private void configureServer(String sseStreamBody) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/agent")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":[{\"id\":\"boss\",\"name\":\"boss\"},{\"id\":\"worker\",\"name\":\"worker\"}]}");
                }
                if (path != null && "/api/session".equals(path) && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"ses_root\",\"title\":\"boss\"}}");
                }
                if (path != null && path.startsWith("/api/event")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody(sseStreamBody);
                }
                if (path != null && path.contains("/prompt") && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"msg_1\"}}");
                }
                if (path != null && path.contains("/permission") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                if (path != null && path.startsWith("/api/session/ses_root") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                if (path != null && path.startsWith("/api/debug/location") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }
}
