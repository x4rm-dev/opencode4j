package io.opencode4j.model.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An LLM provider registered in a location.
 *
 * @param activation  {@code auto}, {@code enabled} or {@code disabled}
 * @param packageName the AI SDK package that talks to the provider, for example {@code @ai-sdk/openai-compatible}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderInfo(
        String id,
        String name,
        String activation,
        @JsonProperty("package") String packageName
) {
}
