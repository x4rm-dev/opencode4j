package io.opencode4j;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentRuntime;
import java.util.Objects;

/** Ergonomic entry point for a configured OpenCode client and AgentRuntime. */
public final class OpenCode {
    private final OpenCodeClient client;
    private final AgentRuntime runtime;

    public OpenCode(OpenCodeClient client) {
        this(client, OpenCodeAgentRuntime.of(client));
    }

    public OpenCode(OpenCodeClient client, AgentRuntime runtime) {
        this.client = Objects.requireNonNull(client, "client");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public static OpenCode create(OpenCodeConfig config) {
        OpenCodeClient client = OpenCodeClient.create(config);
        return new OpenCode(client, OpenCodeAgentRuntime.of(client));
    }

    public OpenCodeClient client() {
        return client;
    }

    public AgentRuntime runtime() {
        return runtime;
    }
}
