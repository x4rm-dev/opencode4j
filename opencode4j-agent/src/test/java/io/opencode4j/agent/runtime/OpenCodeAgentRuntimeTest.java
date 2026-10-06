package io.opencode4j.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunStartedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeAgentRuntimeTest {

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
    void runsCompleteAgentSessionLifecycleWithMockServer(@TempDir Path tempDir) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/agent")) {
                    // Warmup listing
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":[{\"id\":\"boss\",\"name\":\"boss\"},{\"id\":\"worker\",\"name\":\"worker\"}]}");
                }
                if (path != null && "/api/session".equals(path) && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"ses_mock_root\",\"title\":\"boss\"}}");
                }
                if (path != null && path.startsWith("/api/event")) {
                    String sse = "data: {\"type\":\"server.connected\"}\n\n"
                            + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_mock_root\"}}\n\n"
                            + "data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_mock_root\",\"assistantMessageID\":\"m1\",\"ordinal\":0,\"delta\":\"Hello \"}}\n\n"
                            + "data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_mock_root\",\"assistantMessageID\":\"m1\",\"ordinal\":1,\"delta\":\"world!\"}}\n\n"
                            + "data: {\"type\":\"session.step.ended\",\"data\":{\"sessionID\":\"ses_mock_root\",\"assistantMessageID\":\"m1\",\"finish\":\"stop\",\"cost\":0.002,\"tokens\":{\"input\":20,\"output\":10,\"reasoning\":0}}}\n\n"
                            + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_mock_root\"}}\n\n";

                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody(sse);
                }
                if (path != null && path.contains("/prompt") && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"msg_mock_user_1\"}}");
                }
                if (path != null && path.startsWith("/api/session/ses_mock_root") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                if (path != null && path.startsWith("/api/debug/location") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        AgentSpec worker = AgentSpec.builder("worker")
                .description("Worker subagent")
                .instruction("I am worker")
                .build();

        AgentSpec boss = AgentSpec.builder("boss")
                .description("Boss orchestrator")
                .instruction("I delegate work")
                .subAgent(worker)
                .permission(PermissionRule.allow("*", "*"))
                .build();

        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        List<AgentEvent> capturedEvents = Collections.synchronizedList(new ArrayList<>());
        AgentEventListener listener = capturedEvents::add;

        try (AgentSession session = runtime.open(boss)) {
            assertEquals("ses_mock_root", session.sessionId());

            AgentRun run = session.send("Hello agent", listener);
            assertNotNull(run.runId());
            assertEquals("ses_mock_root", run.sessionId());

            AgentResult result = run.await();
            assertNotNull(result);
            assertTrue(result.success(), result.errorMessage());
            assertEquals("Hello world!", result.text());
            assertEquals(30, result.tokenUsage().total());
            assertEquals(0.002, result.totalCost());
            assertEquals(1, result.steps().size());
            assertEquals("stop", result.steps().get(0).finishReason());

            // Verify event emission
            assertFalse(capturedEvents.isEmpty());
            assertTrue(capturedEvents.stream().anyMatch(e -> e instanceof RunStartedEvent));
            assertTrue(capturedEvents.stream().anyMatch(e -> e instanceof TextDeltaEvent t && "Hello ".equals(t.delta())));
            assertTrue(capturedEvents.stream().anyMatch(e -> e instanceof TextDeltaEvent t && "world!".equals(t.delta())));
            assertTrue(capturedEvents.stream().anyMatch(e -> e instanceof RunCompletedEvent));
        }

        runtime.close();
    }

    @Test
    void rejectsDuplicateAgentNamesBeforeCallingServer(@TempDir Path tempDir) {
        AgentSpec spec = AgentSpec.builder("boss")
                .subAgent(AgentSpec.builder("worker").build())
                .subAgent(AgentSpec.builder("worker").build())
                .build();

        try (OpenCodeAgentRuntime runtime = new OpenCodeAgentRuntime.Builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(null)
                .deleteWorkspaceOnClose(false)
                .build()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> runtime.open(spec));

            assertTrue(failure.getMessage().contains("Duplicate agent names"));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    void forwardsMultimodalMediaAttachmentsToPromptRequest(@TempDir Path tempDir) throws InterruptedException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/agent")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":[{\"id\":\"vision\",\"name\":\"vision\"}]}");
                }
                if (path != null && "/api/session".equals(path) && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"ses_vision_root\"}}");
                }
                if (path != null && path.startsWith("/api/event")) {
                    String sse = "data: {\"type\":\"server.connected\"}\n\n"
                            + "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"ses_vision_root\"}}\n\n"
                            + "data: {\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_vision_root\",\"assistantMessageID\":\"m1\",\"ordinal\":0,\"delta\":\"I see a red square\"}}\n\n"
                            + "data: {\"type\":\"session.step.ended\",\"data\":{\"sessionID\":\"ses_vision_root\",\"assistantMessageID\":\"m1\",\"finish\":\"stop\",\"cost\":0.001,\"tokens\":{\"input\":50,\"output\":10,\"reasoning\":0}}}\n\n"
                            + "data: {\"type\":\"session.execution.succeeded\",\"data\":{\"sessionID\":\"ses_vision_root\"}}\n\n";

                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody(sse);
                }
                if (path != null && path.contains("/prompt") && "POST".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"msg_1\"}}");
                }
                if (path != null && path.startsWith("/api/session/") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                if (path != null && path.startsWith("/api/debug/location") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(204);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        AgentSpec spec = AgentSpec.builder("vision").build();
        OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
                .baseWorkspacesDir(tempDir)
                .warmupTimeout(Duration.ofSeconds(3))
                .build();

        try (AgentSession session = runtime.open(spec)) {
            io.opencode4j.runtime.media.ImageAttachment image = io.opencode4j.runtime.media.MediaAttachment.image(
                    new byte[] { 0x1, 0x2, 0x3 }, "image/png", "sample.png", "Red square image"
            );
            io.opencode4j.runtime.RunPrompt prompt = io.opencode4j.runtime.RunPrompt.builder("What is in this image?")
                    .attach(image)
                    .build();

            AgentRun run = session.send(prompt, null);
            AgentResult result = run.await();

            assertTrue(result.isSuccess());
            assertEquals("I see a red square", result.text());

            boolean sawFileInPrompt = false;
            for (int i = 0; i < server.getRequestCount(); i++) {
                RecordedRequest req = server.takeRequest();
                if (req.getPath() != null && req.getPath().contains("/prompt")) {
                    String body = req.getBody().readUtf8();
                    if (body.contains("data:image/png;base64,") && body.contains("sample.png")) {
                        sawFileInPrompt = true;
                        break;
                    }
                }
            }
            assertTrue(sawFileInPrompt, "Server prompt request should contain data URI file attachment");
        }

        runtime.close();
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}
