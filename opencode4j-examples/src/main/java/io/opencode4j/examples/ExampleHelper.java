package io.opencode4j.examples;

import io.opencode4j.OpenCodeException;
import io.opencode4j.agent.runtime.server.OpenCodeServerInstance;
import io.opencode4j.agent.runtime.server.OpenCodeServerLauncher;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;

/**
 * Shared helper utility for running opencode4j example programs.
 *
 * <p>Connects to a running server if {@code OPENCODE_SERVER_URL} is set; otherwise launches
 * an isolated local OpenCode server instance automatically.</p>
 */
public final class ExampleHelper implements AutoCloseable {

    private final OpenCodeClient client;
    private final OpenCodeServerInstance managedServer;
    private final String model;

    private ExampleHelper(OpenCodeClient client, OpenCodeServerInstance managedServer, String model) {
        this.client = client;
        this.managedServer = managedServer;
        this.model = model;
    }

    public static ExampleHelper init(String title) throws IOException, InterruptedException {
        System.out.println("==================================================");
        System.out.println(" " + title);
        System.out.println("==================================================");

        String serverUrl = System.getenv("OPENCODE_SERVER_URL");
        String password = System.getenv("OPENCODE_SERVER_PASSWORD");
        String model = System.getenv().getOrDefault("OPENCODE_MODEL", "github-copilot/gpt-6.1-sol");

        OpenCodeServerInstance managed = null;
        OpenCodeClient client;

        if (serverUrl != null && !serverUrl.isBlank()) {
            System.out.println("[mode] Using existing server: " + serverUrl);
            OpenCodeConfig config = OpenCodeConfig.builder(serverUrl)
                    .servePassword(password)
                    .build();
            client = OpenCodeClient.create(config);
            try {
                var info = client.server().info();
                System.out.printf("[server] OpenCode %s (PID %s)%n", info.version(), info.pid());
            } catch (OpenCodeException e) {
                System.err.printf("[error] Could not connect to %s: %s%n", serverUrl, e.getMessage());
                throw e;
            }
        } else {
            System.out.println("[mode] Auto-launching local OpenCode server...");
            managed = OpenCodeServerLauncher.create().launch();
            client = managed.toClient();
            System.out.printf("[server] Launched on %s (port %d)%n", managed.url(), managed.port());
        }

        System.out.println("[model] " + model);
        System.out.println("--------------------------------------------------\n");

        return new ExampleHelper(client, managed, model);
    }

    public OpenCodeClient client() {
        return client;
    }

    public String model() {
        return model;
    }

    @Override
    public void close() {
        if (managedServer != null) {
            System.out.println("\n[cleanup] Shutting down managed OpenCode server...");
            managedServer.close();
        }
    }
}
