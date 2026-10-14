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
        Map<String, Provider> provider,
        Map<String, Agent> agent,
        Mcp mcp
) {
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Provider(
            String npm,
            String name,
            ProviderOptions options,
            Map<String, ProviderModel> models
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record ProviderOptions(
            @JsonProperty("baseURL") String baseUrl,
            Map<String, String> headers
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProviderModel(
            String id,
            String name,
            @JsonProperty("tool_call") boolean toolCall,
            boolean attachment,
            Modalities modalities,
            Limit limit
    ) {}

    record Modalities(List<String> input, List<String> output) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Limit(Integer context, Integer output) {}

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
            Map<String, Object> permission
    ) {}

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
