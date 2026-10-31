package io.opencode4j.model.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opencode4j.model.ModelRef;
import java.util.List;

/**
 * A model visible in a location, with what it can do and its limits.
 *
 * @param id       catalog id
 * @param modelId  id the provider expects, for example {@code global:LLM_GLOBAL_X}
 * @param status   {@code alpha}, {@code beta}, {@code active} or {@code deprecated}
 * @param enabled  whether sessions may use it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelInfo(
        String id,
        @JsonProperty("modelID") String modelId,
        @JsonProperty("providerID") String providerId,
        String name,
        String status,
        boolean enabled,
        Capabilities capabilities,
        Limit limit,
        List<Cost> cost
) {

    public ModelInfo {
        cost = cost == null ? List.of() : List.copyOf(cost);
    }

    /** The reference to use in agent and session configuration. */
    public ModelRef ref() {
        return new ModelRef(modelId, providerId, null);
    }

    /** Whether the model can call tools, which every agent model needs. */
    public boolean supportsTools() {
        return capabilities != null && capabilities.tools();
    }

    /**
     * @param tools  whether the model supports tool calls
     * @param input  accepted input modalities, for example {@code text} and {@code image}
     * @param output produced output modalities
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Capabilities(boolean tools, List<String> input, List<String> output) {

        public Capabilities {
            input = input == null ? List.of() : List.copyOf(input);
            output = output == null ? List.of() : List.copyOf(output);
        }
    }

    /**
     * Token limits. Auto-compaction depends on {@code context} being right.
     *
     * @param input maximum input tokens when it differs from {@code context}, or {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Limit(long context, Long input, long output) {
    }

    /** Price in USD per million tokens, optionally for a context-size tier. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cost(double input, double output) {
    }
}
