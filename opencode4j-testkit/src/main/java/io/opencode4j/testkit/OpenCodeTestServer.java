package io.opencode4j.testkit;

import io.opencode4j.agent.runtime.server.OpenCodeServerInstance;
import io.opencode4j.agent.runtime.server.OpenCodeServerLauncher;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.EnvironmentVariables;
import io.opencode4j.config.OpenCodeConfig;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A real {@code opencode serve} process in a clean room (temporary HOME and XDG directories, no host
 * credentials), for contract tests.
 *
 * <p>The binary is taken from {@code OPENCODE_BIN} or found on the {@code PATH}; tests should skip when
 * {@link #findBinary()} is empty. Environment variables passed to {@link #start(Map)} are visible to the server, so
 * a workspace can reference them as {@code {env:NAME}}.</p>
 */
public final class OpenCodeTestServer implements AutoCloseable {

    private final OpenCodeServerInstance instance;
    private final OpenCodeConfig config;

    private OpenCodeTestServer(OpenCodeServerInstance instance) {
        this.instance = instance;
        this.config = instance.toConfig();
    }

    /** The {@code opencode} binary to test against, if one is installed. */
    public static Optional<String> findBinary() {
        String configured = System.getenv(EnvironmentVariables.OPENCODE_BIN);
        if (configured != null && !configured.isBlank()) {
            return new File(configured).canExecute() ? Optional.of(configured) : Optional.empty();
        }
        String path = System.getenv("PATH");
        Stream<String> onPath = path == null ? Stream.empty()
                : Stream.of(path.split(File.pathSeparator)).map(directory -> directory + File.separator + "opencode");
        String home = System.getProperty("user.home");
        return Stream.concat(onPath, Stream.of("/opt/homebrew/bin/opencode", "/usr/local/bin/opencode",
                        home + "/.opencode/bin/opencode"))
                .filter(candidate -> new File(candidate).canExecute())
                .findFirst();
    }

    /** Starts a server with a random password and the given extra environment. */
    public static OpenCodeTestServer start(Map<String, String> environment) throws IOException, InterruptedException {
        String binary = findBinary().orElseThrow(() -> new IllegalStateException(
                "No opencode binary found; set " + EnvironmentVariables.OPENCODE_BIN));
        Map<String, String> env = new LinkedHashMap<>(environment);
        env.put(EnvironmentVariables.OPENCODE_SERVER_PASSWORD, UUID.randomUUID().toString());
        OpenCodeServerInstance instance = OpenCodeServerLauncher.builder()
                .binaryPath(binary)
                .port(0)
                .cleanRoom(true)
                .startupTimeout(Duration.ofSeconds(30))
                .environment(env)
                .build()
                .launch();
        return new OpenCodeTestServer(instance);
    }

    /** Connection settings, including the password. */
    public OpenCodeConfig config() {
        return config;
    }

    /** A new client for this server; the caller closes it. */
    public OpenCodeClient newClient() {
        return OpenCodeClient.create(config);
    }

    public String url() {
        return instance.url();
    }

    public boolean isAlive() {
        return instance.isAlive();
    }

    @Override
    public void close() {
        instance.close();
    }
}
