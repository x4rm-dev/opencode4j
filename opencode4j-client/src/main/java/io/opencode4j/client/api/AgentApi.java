package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.AgentSummary;
import java.util.List;
import java.util.Objects;

/**
 * Lists and retrieves registered agents from the server for a specific workspace location.
 */
public final class AgentApi {

    private static final TypeReference<List<AgentSummary>> AGENT_LIST = new TypeReference<>() {};

    private final OpenCodeTransport transport;

    public AgentApi(OpenCodeTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * Lists agents visible in the given workspace directory (or server default if null).
     *
     * @param directory workspace location directory, or {@code null}
     * @return list of agent summaries
     */
    public List<AgentSummary> list(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation("/api/agent", directory),
                null,
                AGENT_LIST
        );
    }

    /**
     * Retrieves specific agent details by ID.
     *
     * @param agentId   agent identifier
     * @param directory workspace location directory, or {@code null}
     * @return agent summary
     */
    public AgentSummary get(String agentId, String directory) {
        Objects.requireNonNull(agentId, "agentId");
        return transport.send(
                HttpMethod.GET,
                transport.withLocation("/api/agent/" + transport.segment(agentId), directory),
                null,
                AgentSummary.class
        );
    }
}
