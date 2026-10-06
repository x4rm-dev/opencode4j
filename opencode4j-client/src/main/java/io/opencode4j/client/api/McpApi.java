package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.mcp.McpResourceCatalog;
import io.opencode4j.model.mcp.McpServer;
import io.opencode4j.model.mcp.McpServerConfig;
import io.opencode4j.model.mcp.PutMcpServerRequest;
import java.util.List;
import java.util.Objects;

/**
 * Inspects and manages MCP (Model Context Protocol) servers.
 *
 * <p>Listing is stable. Adding, removing, connecting and disconnecting servers are experimental routes the
 * server may change. Every operation takes an optional {@code directory}; when it is {@code null} or blank the
 * server uses its default location.</p>
 */
public final class McpApi {

    private static final String MCP_PATH = "/api/mcp";
    private static final String SERVER_PATH = "/api/experimental/mcp/";

    private final OpenCodeTransport transport;

    public McpApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** Lists the configured MCP servers with their connection state. */
    public List<McpServer> list(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(MCP_PATH, directory),
                null,
                new TypeReference<List<McpServer>>() { });
    }

    /** Lists the resources and resource templates offered by the connected servers. */
    public McpResourceCatalog resources(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(MCP_PATH + "/resource", directory),
                null,
                McpResourceCatalog.class);
    }

    /** Adds an MCP server, or replaces the one with the same name. Experimental. */
    public void put(String name, McpServerConfig config, String directory) {
        transport.send(
                HttpMethod.PUT,
                transport.withLocation(serverPath(name), directory),
                new PutMcpServerRequest(config),
                Void.class);
    }

    /** Removes an MCP server. Experimental. */
    public void remove(String name, String directory) {
        transport.send(
                HttpMethod.DELETE, transport.withLocation(serverPath(name), directory), null, Void.class);
    }

    /** Connects a configured MCP server. Experimental. */
    public void connect(String name, String directory) {
        transport.send(
                HttpMethod.POST,
                transport.withLocation(serverPath(name) + "/connect", directory),
                null,
                Void.class);
    }

    /** Disconnects an MCP server without removing it. Experimental. */
    public void disconnect(String name, String directory) {
        transport.send(
                HttpMethod.POST,
                transport.withLocation(serverPath(name) + "/disconnect", directory),
                null,
                Void.class);
    }

    private String serverPath(String name) {
        return SERVER_PATH + transport.segment(Objects.requireNonNull(name, "name"));
    }
}
