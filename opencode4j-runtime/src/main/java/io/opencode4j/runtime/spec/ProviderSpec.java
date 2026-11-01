package io.opencode4j.runtime.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Declares a custom, OpenAI-compatible LLM provider (for example an internal gateway) that is written into a
 * workspace's {@code opencode.json}.
 *
 * <p>Prefer {@link #builder(String)} with {@link SecretRef}s: the API key and header values are then written as
 * {@code {env:...}} or {@code {file:...}} references that OpenCode resolves, so no credential is stored on disk
 * and a rotated value takes effect without rewriting the workspace. The {@code headers} supplier of the record
 * constructor writes literal values and is accepted only for single-agent workspaces or with
 * {@code WorkspaceSpec.allowInlineSecrets}.</p>
 *
 * @param id         provider id; models are referenced as {@code <id>/<model id>}
 * @param name       display name
 * @param baseUrl    OpenAI-compatible base URL, including the {@code /v1} suffix if the API expects it; may itself
 *                   contain an {@code {env:...}} reference, for example for a port
 * @param headers    supplies literal HTTP headers, evaluated when a workspace is written
 * @param models     models the provider serves
 * @param apiKey     API key sent as {@code Authorization: Bearer <key>}, or {@code null}
 * @param headerRefs headers whose values are secret references
 */
public record ProviderSpec(
        String id,
        String name,
        String baseUrl,
        Supplier<Map<String, String>> headers,
        List<ModelSpec> models,
        SecretRef apiKey,
        Map<String, SecretRef> headerRefs
) {

    public ProviderSpec {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.contains("/")) {
            throw new IllegalArgumentException("provider id must be non-blank and contain no '/': " + id);
        }
        Objects.requireNonNull(baseUrl, "baseUrl");
        name = name == null || name.isBlank() ? id : name;
        headers = headers == null ? Map::of : headers;
        models = models == null ? List.of() : List.copyOf(models);
        headerRefs = headerRefs == null ? Map.of() : Map.copyOf(headerRefs);
    }

    /** A provider with literal headers and no API key. */
    public ProviderSpec(String id, String name, String baseUrl, Supplier<Map<String, String>> headers,
                        List<ModelSpec> models) {
        this(id, name, baseUrl, headers, models, null, null);
    }

    public static ProviderSpec openAiCompatible(
            String id, String baseUrl, Supplier<Map<String, String>> headers, List<ModelSpec> models) {
        return new ProviderSpec(id, id, baseUrl, headers, models);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Whether any configured value would put a secret on disk. */
    public boolean hasInlineSecrets() {
        if (apiKey != null && apiKey.inline()) {
            return true;
        }
        if (headerRefs.values().stream().anyMatch(SecretRef::inline)) {
            return true;
        }
        return headers.get().values().stream().anyMatch(SecretRef::isInline);
    }

    /**
     * A model served by a {@link ProviderSpec}. OpenCode needs the real limits to decide when to compact a
     * conversation, so both are required.
     *
     * @param id            model id as the provider expects it (may contain colons and slashes)
     * @param name          display name
     * @param toolCalling   whether the model supports tool calls
     * @param vision        whether the model accepts image input
     * @param contextTokens context window size, positive
     * @param outputTokens  maximum output tokens, positive
     * @param cost          price per million tokens, or {@code null} if unknown (costs then report as zero)
     */
    public record ModelSpec(
            String id,
            String name,
            boolean toolCalling,
            boolean vision,
            int contextTokens,
            int outputTokens,
            ModelCost cost
    ) {

        public ModelSpec {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("model id must not be blank");
            }
            if (contextTokens <= 0 || outputTokens <= 0) {
                throw new IllegalArgumentException("Model " + id + " needs positive context and output limits "
                        + "(got " + contextTokens + "/" + outputTokens + "); OpenCode uses them to compact");
            }
            if (outputTokens > contextTokens) {
                throw new IllegalArgumentException("Model " + id + " output limit exceeds its context window");
            }
            name = name == null || name.isBlank() ? id : name;
        }

        /** A model without cost information. */
        public ModelSpec(String id, String name, boolean toolCalling, boolean vision, int contextTokens,
                         int outputTokens) {
            this(id, name, toolCalling, vision, contextTokens, outputTokens, null);
        }

        /** A tool-calling, vision-capable model. */
        public static ModelSpec multimodal(String id, int contextTokens, int outputTokens) {
            return new ModelSpec(id, id, true, true, contextTokens, outputTokens);
        }

        /** A tool-calling, text-only model, the kind an agent needs. */
        public static ModelSpec toolCalling(String id, int contextTokens, int outputTokens) {
            return new ModelSpec(id, id, true, false, contextTokens, outputTokens);
        }

        /** The same model with a price. */
        public ModelSpec withCost(double inputPerMillion, double outputPerMillion) {
            return new ModelSpec(id, name, toolCalling, vision, contextTokens, outputTokens,
                    new ModelCost(inputPerMillion, outputPerMillion));
        }
    }

    /**
     * Price in USD per million tokens, used by OpenCode's cost accounting and therefore by
     * {@code RunLimits.maxCost}.
     */
    public record ModelCost(double inputPerMillion, double outputPerMillion) {

        public ModelCost {
            if (inputPerMillion < 0 || outputPerMillion < 0) {
                throw new IllegalArgumentException("costs must not be negative");
            }
        }
    }

    /** Builder for {@link ProviderSpec}. */
    public static final class Builder {
        private final String id;
        private String name;
        private String baseUrl;
        private SecretRef apiKey;
        private final Map<String, SecretRef> headerRefs = new LinkedHashMap<>();
        private final List<ModelSpec> models = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Sent as {@code Authorization: Bearer <key>} by the OpenAI-compatible client. */
        public Builder apiKey(SecretRef apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            return this;
        }

        public Builder header(String name, SecretRef value) {
            headerRefs.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder model(ModelSpec model) {
            models.add(Objects.requireNonNull(model, "model"));
            return this;
        }

        public ProviderSpec build() {
            return new ProviderSpec(id, name, baseUrl, Map::of, models, apiKey, headerRefs);
        }
    }
}
