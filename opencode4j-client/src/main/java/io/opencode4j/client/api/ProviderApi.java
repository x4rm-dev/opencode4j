package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.provider.ModelInfo;
import io.opencode4j.model.provider.ProviderInfo;
import java.util.List;
import java.util.Objects;

/**
 * Reads the providers and models a location can use, for example to check that a configured model exists and
 * supports tools before creating sessions.
 */
public final class ProviderApi {

    private static final TypeReference<List<ProviderInfo>> PROVIDERS = new TypeReference<>() { };
    private static final TypeReference<List<ModelInfo>> MODELS = new TypeReference<>() { };

    private final OpenCodeTransport transport;

    public ProviderApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** Providers registered in {@code directory}, or in the server's default location if {@code null}. */
    public List<ProviderInfo> providers(String directory) {
        List<ProviderInfo> providers = transport.send(HttpMethod.GET,
                transport.withLocation("/api/provider", directory), null, PROVIDERS);
        return providers == null ? List.of() : providers;
    }

    /** Chat models visible in {@code directory}, across all providers. */
    public List<ModelInfo> models(String directory) {
        List<ModelInfo> models = transport.send(HttpMethod.GET,
                transport.withLocation("/api/model", directory), null, MODELS);
        return models == null ? List.of() : models;
    }

    /** Chat models of one provider visible in {@code directory}. */
    public List<ModelInfo> models(String providerId, String directory) {
        Objects.requireNonNull(providerId, "providerId");
        return models(directory).stream().filter(model -> providerId.equals(model.providerId())).toList();
    }
}
