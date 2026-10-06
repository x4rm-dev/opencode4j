package io.opencode4j.config;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Connection and behavior configuration for an {@code opencode serve} instance.
 *
 * <p>Immutable; construct via {@link #builder(String)}.</p>
 */
@Getter
@Accessors(fluent = true)
public final class OpenCodeConfig {

    /** Username the server expects for HTTP basic auth unless {@code OPENCODE_SERVER_USERNAME} overrides it. */
    public static final String DEFAULT_SERVE_USERNAME = "opencode";

    private final String baseUrl;
    private final String serveUsername;
    private final String servePassword;
    private final String authCookie;
    private final String defaultModel;
    private final String defaultAgent;
    private final long connectTimeoutMs;
    private final long requestTimeoutMs;
    private final long sseReadTimeoutMs;
    private final long sseReconnectBaseDelayMs;
    private final long sseReconnectMaxDelayMs;
    private final int sseMaxRetries;

    private OpenCodeConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.serveUsername = builder.serveUsername;
        this.servePassword = builder.servePassword;
        this.authCookie = builder.authCookie;
        this.defaultModel = builder.defaultModel;
        this.defaultAgent = builder.defaultAgent;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.requestTimeoutMs = builder.requestTimeoutMs;
        this.sseReadTimeoutMs = builder.sseReadTimeoutMs;
        this.sseReconnectBaseDelayMs = builder.sseReconnectBaseDelayMs;
        this.sseReconnectMaxDelayMs = builder.sseReconnectMaxDelayMs;
        this.sseMaxRetries = builder.sseMaxRetries;
    }

    public boolean hasServePassword() {
        return servePassword != null && !servePassword.isBlank();
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /** Builder for {@link OpenCodeConfig}. */
    public static final class Builder {

        private final String baseUrl;
        private String serveUsername = DEFAULT_SERVE_USERNAME;
        private String servePassword;
        private String authCookie;
        private String defaultModel;
        private String defaultAgent;
        private long connectTimeoutMs = 10_000;
        private long requestTimeoutMs = 120_000;
        /** {@code 0} means no read timeout, because the SSE stream is long-lived. */
        private long sseReadTimeoutMs = 0;
        private long sseReconnectBaseDelayMs = 1_000;
        private long sseReconnectMaxDelayMs = 30_000;
        private int sseMaxRetries = 10;

        private Builder(String baseUrl) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }
            // Strip the trailing slash so endpoint concatenation is predictable.
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        /**
         * Username for HTTP basic auth. Defaults to {@value OpenCodeConfig#DEFAULT_SERVE_USERNAME};
         * set it when the server was started with {@code OPENCODE_SERVER_USERNAME}.
         */
        public Builder serveUsername(String serveUsername) {
            this.serveUsername = serveUsername == null || serveUsername.isBlank()
                    ? DEFAULT_SERVE_USERNAME
                    : serveUsername;
            return this;
        }

        /** Password for HTTP basic auth, required when the server has {@code OPENCODE_SERVER_PASSWORD} set. */
        public Builder servePassword(String servePassword) {
            this.servePassword = servePassword;
            return this;
        }

        /** Optional raw Cookie header value forwarded to the server (e.g. an ide_token JWT in hosted setups). */
        public Builder authCookie(String authCookie) {
            this.authCookie = authCookie;
            return this;
        }

        /** Default {@code "provider/model"} used when an agent request does not name one. */
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /** Default agent used when an agent request does not name one. */
        public Builder defaultAgent(String defaultAgent) {
            this.defaultAgent = defaultAgent;
            return this;
        }

        public Builder connectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder requestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder sseReadTimeoutMs(long sseReadTimeoutMs) {
            this.sseReadTimeoutMs = sseReadTimeoutMs;
            return this;
        }

        public Builder sseReconnectBaseDelayMs(long sseReconnectBaseDelayMs) {
            this.sseReconnectBaseDelayMs = sseReconnectBaseDelayMs;
            return this;
        }

        public Builder sseReconnectMaxDelayMs(long sseReconnectMaxDelayMs) {
            this.sseReconnectMaxDelayMs = sseReconnectMaxDelayMs;
            return this;
        }

        public Builder sseMaxRetries(int sseMaxRetries) {
            this.sseMaxRetries = sseMaxRetries;
            return this;
        }

        public OpenCodeConfig build() {
            return new OpenCodeConfig(this);
        }
    }
}
