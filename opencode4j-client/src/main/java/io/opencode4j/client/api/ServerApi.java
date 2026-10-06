package io.opencode4j.client.api;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.ServerInfo;

/** Reads server identity and readiness. */
public final class ServerApi {

    private static final String INFO_PATH = "/api/info";

    private final OpenCodeTransport transport;

    public ServerApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Fetches server identity: version, process ID, listen URLs and paths.
     *
     * @throws OpenCodeException with status {@code 401} when the server requires authentication and the
     *         configured credentials are missing or wrong
     */
    public ServerInfo info() {
        return transport.send(HttpMethod.GET, INFO_PATH, null, ServerInfo.class);
    }

    /**
     * Returns whether the server answers {@link #info()}. Any failure, including an authentication failure,
     * yields {@code false}; call {@link #info()} directly to find out why.
     */
    public boolean isHealthy() {
        try {
            return info() != null;
        } catch (OpenCodeException exception) {
            return false;
        }
    }
}
