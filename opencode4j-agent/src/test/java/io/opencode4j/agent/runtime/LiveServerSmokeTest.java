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
}
