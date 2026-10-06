package io.opencode4j.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.client.api.AgentApi;
import io.opencode4j.client.api.ConfigApi;
import io.opencode4j.client.api.CredentialApi;
import io.opencode4j.client.api.EventApi;
import io.opencode4j.client.api.FileApi;
import io.opencode4j.client.api.IntegrationApi;
import io.opencode4j.client.api.InteractionApi;
import io.opencode4j.client.api.McpApi;
import io.opencode4j.client.api.PromptApi;
import io.opencode4j.client.api.ServerApi;
import io.opencode4j.client.api.SessionApi;
import io.opencode4j.client.api.ShellApi;
import io.opencode4j.config.OpenCodeConfig;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/**
 * Thread-safe facade over the OpenCode v2 HTTP and event-stream APIs.
 *
 * <p>Each accessor returns a small API object for one area of the server. Create an instance with
 * {@link #create(OpenCodeConfig)}, or with {@link #OpenCodeClient(OkHttpClient, OpenCodeConfig, ObjectMapper)}
 * to supply your own HTTP client and mapper.</p>
 */
public final class OpenCodeClient {

    private final OpenCodeTransport transport;
    private final ServerApi server;
    private final SessionApi sessions;
    private final PromptApi prompts;
    private final InteractionApi interactions;
    private final EventApi events;
    private final IntegrationApi integrations;
    private final CredentialApi credentials;
    private final ShellApi shell;
    private final ConfigApi config;
    private final FileApi files;
    private final McpApi mcp;
    private final AgentApi agents;

    public OpenCodeClient(OkHttpClient http,
                          OpenCodeConfig config,
                          ObjectMapper mapper) {
        this.transport = new OpenCodeTransport(http, config, mapper);
        this.server = new ServerApi(transport);
        this.sessions = new SessionApi(transport);
        this.prompts = new PromptApi(transport);
        this.interactions = new InteractionApi(transport);
        this.events = new EventApi(transport);
        this.integrations = new IntegrationApi(transport);
        this.credentials = new CredentialApi(transport);
        this.shell = new ShellApi(transport);
        this.config = new ConfigApi(transport);
        this.files = new FileApi(transport);
        this.mcp = new McpApi(transport);
        this.agents = new AgentApi(transport);
    }

    public static OpenCodeClient create(OpenCodeConfig config) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
        return new OpenCodeClient(http, config, new ObjectMapper());
    }

    public ServerApi server() {
        return server;
    }

    public SessionApi sessions() {
        return sessions;
    }

    public PromptApi prompts() {
        return prompts;
    }

    public InteractionApi interactions() {
        return interactions;
    }

    public EventApi events() {
        return events;
    }

    public IntegrationApi integrations() {
        return integrations;
    }

    public CredentialApi credentials() {
        return credentials;
    }

    public ShellApi shell() {
        return shell;
    }

    /** Server configuration. */
    public ConfigApi config() {
        return config;
    }

    /** File listing, search, read and write. */
    public FileApi files() {
        return files;
    }

    /** MCP servers. */
    public McpApi mcp() {
        return mcp;
    }

    /** Registered agents for locations. */
    public AgentApi agents() {
        return agents;
    }

    /** Underlying transport for direct low-level requests. */
    public OpenCodeTransport transport() {
        return transport;
    }
}
