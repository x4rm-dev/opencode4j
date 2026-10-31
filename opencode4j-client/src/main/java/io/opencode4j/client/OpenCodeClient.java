package io.opencode4j.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.client.api.AgentApi;
import io.opencode4j.client.event.EventBus;
import io.opencode4j.client.event.OpenCodeEventBus;
import io.opencode4j.client.api.ConfigApi;
import io.opencode4j.client.api.CredentialApi;
import io.opencode4j.client.api.EventApi;
import io.opencode4j.client.api.FileApi;
import io.opencode4j.client.api.IntegrationApi;
import io.opencode4j.client.api.InteractionApi;
import io.opencode4j.client.api.McpApi;
import io.opencode4j.client.api.MessageApi;
import io.opencode4j.client.api.PermissionApi;
import io.opencode4j.client.api.PromptApi;
import io.opencode4j.client.api.ProviderApi;
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
public final class OpenCodeClient implements AutoCloseable {

    private final OkHttpClient http;
    private final boolean ownsHttpClient;
    private final OpenCodeTransport transport;
    private final Object busLock = new Object();
    private OpenCodeEventBus eventBus;
    private boolean closed;
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
    private final MessageApi messages;
    private final PermissionApi permissions;
    private final ProviderApi providers;

    public OpenCodeClient(OkHttpClient http,
                          OpenCodeConfig config,
                          ObjectMapper mapper) {
        this(http, config, mapper, false);
    }

    private OpenCodeClient(OkHttpClient http, OpenCodeConfig config, ObjectMapper mapper, boolean ownsHttpClient) {
        this.http = http;
        this.ownsHttpClient = ownsHttpClient;
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
        this.messages = new MessageApi(transport);
        this.permissions = new PermissionApi(transport);
        this.providers = new ProviderApi(transport);
    }

    public static OpenCodeClient create(OpenCodeConfig config) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
        return new OpenCodeClient(http, config, new ObjectMapper(), true);
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

    /** Session message history. */
    public MessageApi messages() {
        return messages;
    }

    /** Pending permission requests and their answers. */
    public PermissionApi permissions() {
        return permissions;
    }

    /** Providers and models available in a location. */
    public ProviderApi providers() {
        return providers;
    }

    /**
     * The shared event bus: one self-healing connection to {@code /api/event} fanned out to subscribers.
     * Created on first use; prefer it over {@link #events()}, which opens a raw stream per call.
     */
    public EventBus eventBus() {
        synchronized (busLock) {
            if (closed) {
                throw new IllegalStateException("OpenCodeClient is closed");
            }
            if (eventBus == null) {
                eventBus = new OpenCodeEventBus(transport);
            }
            return eventBus;
        }
    }

    /**
     * Closes the event bus and, when this client created its own HTTP client ({@link #create}), shuts down its
     * dispatcher and connection pool. An HTTP client passed in by the caller is left running.
     */
    @Override
    public void close() {
        OpenCodeEventBus bus;
        synchronized (busLock) {
            if (closed) {
                return;
            }
            closed = true;
            bus = eventBus;
        }
        if (bus != null) {
            bus.close();
        }
        if (ownsHttpClient) {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
    }

    /** Underlying transport for direct low-level requests. */
    public OpenCodeTransport transport() {
        return transport;
    }
}
