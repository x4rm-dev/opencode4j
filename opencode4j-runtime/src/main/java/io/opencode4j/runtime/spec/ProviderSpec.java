package io.opencode4j.runtime.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Declares a custom, OpenAI-compatible LLM provider (for example an internal gateway) that is written into
 * each session workspace's {@code opencode.json}.
 *
 * <p>{@code headers} is a {@link Supplier} evaluated every time a session is opened, so short-lived
 * credentials (such as an Olympus token) are refreshed per session without restarting the OpenCode server.
 * Because the provider lives in the session workspace, one server can serve sessions that use different
 * credentials.</p>
 *
 * @param id      provider id; models are referenced as {@code <id>/<model id>}
 * @param name    display name
 * @param baseUrl OpenAI-compatible base URL, including the {@code /v1} suffix if the API expects it
 * @param headers supplies HTTP headers sent on every request, such as {@code Authorization}
 * @param models  models the provider serves
 */
public record ProviderSpec(
        String id,
        String name,
        String baseUrl,
        Supplier<Map<String, String>> headers,
        List<ModelSpec> models
) {

    public ProviderSpec {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
        Objects.requireNonNull(baseUrl, "baseUrl");
        name = name == null || name.isBlank() ? id : name;
        headers = headers == null ? Map::of : headers;
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static ProviderSpec openAiCompatible(
            String id, String baseUrl, Supplier<Map<String, String>> headers, List<ModelSpec> models) {
        return new ProviderSpec(id, id, baseUrl, headers, models);
    }

    /**
     * A model served by a {@link ProviderSpec}.
     *
     * @param id            model id as the provider expects it (may contain colons)
     * @param name          display name
     * @param toolCalling   whether the model supports tool calls
     * @param vision        whether the model accepts image input
     * @param contextTokens context window size, or {@code 0} if unknown
     * @param outputTokens  maximum output tokens, or {@code 0} if unknown
     */
    public record ModelSpec(
            String id,
            String name,
            boolean toolCalling,
            boolean vision,
            int contextTokens,
            int outputTokens
    ) {

        public ModelSpec {
            Objects.requireNonNull(id, "id");
            name = name == null || name.isBlank() ? id : name;
        }

        /** A tool-calling, vision-capable model. */
        public static ModelSpec multimodal(String id, int contextTokens, int outputTokens) {
            return new ModelSpec(id, id, true, true, contextTokens, outputTokens);
        }
    }
}
