package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.config.ConfigEntry;
import io.opencode4j.model.config.ConfigPatch;
import io.opencode4j.model.config.ShellOption;
import java.util.List;

/**
 * Reads server configuration.
 *
 * <p>The v2 API is read-mostly: the only setting that can be changed through it is the shell. Everything
 * else, including providers and models, is configured in the config files.</p>
 */
public final class ConfigApi {

    private static final String CONFIG_PATH = "/api/config";

    private final OpenCodeTransport transport;

    public ConfigApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Lists the configuration sources the server loaded for a location, in the order it merges them: settings
     * documents and the directories it scans.
     *
     * @param directory the location to resolve configuration for, or {@code null} for the server's default
     */
    public List<ConfigEntry> entries(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(CONFIG_PATH, directory),
                null,
                new TypeReference<List<ConfigEntry>>() { });
    }

    /** Lists the shells installed on the server's machine. */
    public List<ShellOption> shells() {
        return transport.send(
                HttpMethod.GET, CONFIG_PATH + "/shell", null, new TypeReference<List<ShellOption>>() { });
    }

    /**
     * Sets the shell OpenCode runs commands with, in the global configuration. Experimental.
     *
     * @param shell the shell's path, or {@code null} to clear the setting
     */
    public void setShell(String shell) {
        transport.send(
                HttpMethod.PATCH, "/api/experimental/config", new ConfigPatch(shell), Void.class);
    }
}
