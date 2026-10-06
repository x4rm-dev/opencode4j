package io.opencode4j.agent.runtime.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
record WorkspaceConfiguration(
        Experimental experimental,
        Map<String, Agent> agent,
        Mcp mcp
) {
    @Builder
    record Experimental(@JsonProperty("subagent_depth") int subagentDepth) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Agent(
            String mode,
            String description,
            String prompt,
            String model,
            Integer steps,
            List<Permission> permissions
    ) {}

    @Builder
    record Permission(String action, String resource, String effect) {}

    @Builder
    record Mcp(Map<String, McpServer> servers) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record McpServer(
            String type,
            List<String> command,
            Map<String, String> environment,
            String url,
            Map<String, String> headers
    ) {}
}
