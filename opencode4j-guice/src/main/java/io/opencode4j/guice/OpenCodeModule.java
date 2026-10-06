package io.opencode4j.guice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentRuntime;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/** Guice bindings for an OpenCode client and AgentRuntime configured by the caller. */
public final class OpenCodeModule extends AbstractModule {
    private final OpenCodeConfig config;

    public OpenCodeModule(OpenCodeConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        bind(OpenCodeConfig.class).toInstance(config);
    }

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient(OpenCodeConfig openCodeConfig) {
        // No global read/write timeout: sync prompts and the SSE stream are long-lived.
        // Per-endpoint timeouts are applied by OpenCodeClient via newBuilder() clones
        // (which share this instance's connection pool).
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(openCodeConfig.connectTimeoutMs()))
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }

    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Singleton
    public OpenCodeClient provideClient(OkHttpClient http, OpenCodeConfig openCodeConfig, ObjectMapper mapper) {
        return new OpenCodeClient(http, openCodeConfig, mapper);
    }

    @Provides
    @Singleton
    public AgentRuntime provideAgentRuntime(OpenCodeClient client) {
        return OpenCodeAgentRuntime.of(client);
    }
}
