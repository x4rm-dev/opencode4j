package io.opencode4j.agent.runtime.server;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.LocalServerDefaults;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle to a running local OpenCode server subprocess.
 */
@Slf4j
@Getter
public final class OpenCodeServerInstance implements AutoCloseable {

    private final Process process;
    private final int port;
    private final String password;
    private final String url;
    private final Path cleanRoomDir;
    private final OpenCodeConfig config;

    public OpenCodeServerInstance(
            Process process,
            int port,
            String password,
            Path cleanRoomDir
    ) {
        this.process = Objects.requireNonNull(process, "process");
        this.port = port;
        this.password = Objects.requireNonNull(password, "password");
        this.url = LocalServerDefaults.HTTP_URL_PREFIX + port;
        this.cleanRoomDir = cleanRoomDir;
        this.config = OpenCodeConfig.builder(url)
                .servePassword(password)
                .serveUsername(OpenCodeConfig.DEFAULT_SERVE_USERNAME)
                .build();
    }

    public int port() {
        return port;
    }

    public String password() {
        return password;
    }

    public String url() {
        return url;
    }

    public OpenCodeConfig toConfig() {
        return config;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public OpenCodeClient toClient() {
        return OpenCodeClient.create(config);
    }

    @Override
    public void close() {
        if (!process.isAlive()) {
            cleanUpDirectory();
            return;
        }

        log.debug("Stopping OpenCode server process (PID {})", process.pid());
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        cleanUpDirectory();
    }

    private void cleanUpDirectory() {
        if (cleanRoomDir != null && Files.exists(cleanRoomDir)) {
            try (var walk = Files.walk(cleanRoomDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                log.debug("Cleaned up clean-room directory {}", cleanRoomDir);
            } catch (IOException e) {
                log.warn("Failed to delete clean-room directory {}: {}", cleanRoomDir, e.getMessage());
            }
        }
    }
}
