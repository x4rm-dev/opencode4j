package io.opencode4j.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.SessionBusyException;
import io.opencode4j.runtime.SessionOptions;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace-per-release behaviour: one materialized location, many sessions, explicit retirement. */
class SharedWorkspaceTest {

    private MockWebServer server;
    private OpenCodeClient client;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String resumedLocation = "/elsewhere";
    private volatile String serverVersion = "2.2.0";

    @TempDir
    Path workspaces;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                String method = request.getMethod();
                requests.add(method + " " + path.replaceAll("\\?.*", ""));
                if (path.startsWith("/api/info")) {
                    return json("{\"version\":\"" + serverVersion + "\"}");
                }
                if (path.startsWith("/api/agent")) {
                    return json("{\"data\":[{\"id\":\"sre-debugger\"},{\"id\":\"logs-analyst\"}]}");
                }
                if ("POST".equals(method) && path.equals("/api/session")) {
                    String body = request.getBody().readUtf8();
                    requests.add("BODY " + body);
                    return json("{\"data\":{\"id\":\"ses_" + sessionCounter.incrementAndGet() + "\"}}");
                }
                if ("GET".equals(method) && path.startsWith("/api/session/ses_resume")) {
                    return json("{\"data\":{\"id\":\"ses_resume\",\"location\":{\"directory\":\""
                            + resumedLocation + "\"}}}");
                }
                if (path.startsWith("/api/event")) {
                    String body = "data: {\"type\":\"server.connected\",\"data\":{}}\n\n";
                    return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
                            .setHeader("Content-Length", body.length() + 1024);
                }
                if (path.contains("/prompt")) {
                    return json("{\"data\":{\"id\":\"msg_1\"}}");
                }
                if ("DELETE".equals(method) || path.contains("/interrupt")) {
                    return new MockResponse().setResponseCode(204);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        client = OpenCodeClient.create(OpenCodeConfig.builder(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    private OpenCodeAgentRuntime runtime() {
        return OpenCodeAgentRuntime.builder(client).baseWorkspacesDir(workspaces)
                .warmupTimeout(Duration.ofSeconds(3)).build();
    }

    private static WorkspaceSpec release(String id, String smallModel) {
        AgentSpec root = AgentSpec.builder("sre-debugger").subAgent(AgentSpec.builder("logs-analyst").build()).build();
        return WorkspaceSpec.builder(id, root).smallModel(smallModel).build();
    }

    @Test
    void materializesOnceAndSharesTheLocation() {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // When
            Workspace first = runtime.materialize(release("debugging/rel_1", "p/small"));
            Workspace again = runtime.materialize(release("debugging/rel_1", "p/small"));
            AgentSession a = runtime.open(first, SessionOptions.defaults());
            AgentSession b = runtime.open(first, SessionOptions.defaults());

            // Then
            assertSame(first, again);
            assertEquals(1, requests.stream().filter(r -> r.startsWith("GET /api/agent")).count(),
                    "warm-up runs once per workspace");
            assertTrue(first.directory().endsWith(Path.of("debugging", "rel_1")), first.directory().toString());
            assertEquals(2, first.openSessionCount());
            a.close();
            b.close();
        }
    }

    @Test
    void closingOneSessionDetachesWithoutTouchingTheServer() {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // Given
            Workspace workspace = runtime.materialize(release("rel_2", null));
            AgentSession first = runtime.open(workspace, SessionOptions.defaults());
            AgentSession second = runtime.open(workspace, SessionOptions.defaults());
            requests.clear();

            // When
            first.close();

            // Then
            assertTrue(requests.stream().noneMatch(r -> r.startsWith("DELETE")), requests.toString());
            assertEquals(1, workspace.openSessionCount());
            second.delete();
            assertTrue(requests.contains("DELETE /api/session/ses_2"), requests.toString());
            assertFalse(requests.stream().anyMatch(r -> r.startsWith("DELETE /api/debug/location")));
        }
    }

    @Test
    void retiringEvictsTheLocationOnlyWhenNoSessionIsOpen() {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // Given
            Workspace workspace = runtime.materialize(release("rel_3", null));
            AgentSession session = runtime.open(workspace, SessionOptions.defaults());

            // Then
            assertThrows(IllegalStateException.class, workspace::retire);
            session.close();
            workspace.retire();
            assertTrue(requests.contains("DELETE /api/debug/location"), requests.toString());
            assertThrows(IllegalArgumentException.class, () -> runtime.open(workspace, SessionOptions.defaults()));
        }
    }

    @Test
    void aChangedSpecUnderAnIdInUseIsRejected() {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // Given
            Workspace workspace = runtime.materialize(release("rel_4", "p/a"));
            AgentSession session = runtime.open(workspace, SessionOptions.defaults());

            // Then
            assertThrows(IllegalStateException.class, () -> runtime.materialize(release("rel_4", "p/b")));
            session.close();
            Workspace replaced = runtime.materialize(release("rel_4", "p/b"));
            assertFalse(replaced == workspace);
        }
    }

    @Test
    void passesTitleAndMetadataToTheServerSession() throws Exception {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // When
            Workspace workspace = runtime.materialize(release("rel_5", null));
            runtime.open(workspace, SessionOptions.builder().title("Theo timeouts")
                    .metadata("conversationId", "c-42").build()).close();

            // Then
            String body = requests.stream().filter(r -> r.startsWith("BODY ")).findFirst().orElseThrow().substring(5);
            var created = mapper.readTree(body);
            assertEquals("Theo timeouts", created.path("title").asText());
            assertEquals("c-42", created.path("metadata").path("conversationId").asText());
            assertEquals("sre-debugger", created.path("metadata").path("rootAgent").asText());
        }
    }

    @Test
    void resumesOnlySessionsOfTheSameLocation() {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // Given
            Workspace workspace = runtime.materialize(release("rel_6", null));

            // Then
            assertThrows(IllegalArgumentException.class,
                    () -> runtime.resume(workspace, "ses_resume", SessionOptions.defaults()));
            resumedLocation = workspace.directory().toString();
            AgentSession resumed = runtime.resume(workspace, "ses_resume", SessionOptions.defaults());
            assertEquals("ses_resume", resumed.sessionId());
            resumed.close();
        }
    }

    @Test
    void rejectsASecondPromptWhileARunIsActive() throws Exception {
        try (OpenCodeAgentRuntime runtime = runtime()) {
            // Given: the stream never ends the first run
            Workspace workspace = runtime.materialize(release("rel_7", null));
            AgentSession session = runtime.open(workspace, SessionOptions.defaults());
            AgentRun first = session.send("first");

            // Then
            assertThrows(SessionBusyException.class, () -> session.send("second"));
            assertTrue(session.activeRun().isPresent());
            first.cancel();
            first.result().get(3, TimeUnit.SECONDS);
            assertTrue(session.activeRun().isEmpty());
            session.close();
        }
    }

    @Test
    void appliesTheServerVersionPolicy() {
        serverVersion = "2.1.4";
        try (OpenCodeAgentRuntime warns = runtime()) {
            assertEquals("2.1.4", warns.checkServer());
            warns.materialize(release("rel_v1", null));
        }
        try (OpenCodeAgentRuntime strict = OpenCodeAgentRuntime.builder(client).baseWorkspacesDir(workspaces)
                .versionPolicy(io.opencode4j.client.ServerCompatibility.Policy.FAIL).build()) {
            assertThrows(io.opencode4j.client.ServerCompatibility.IncompatibleServerException.class,
                    () -> strict.materialize(release("rel_v2", null)));
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
