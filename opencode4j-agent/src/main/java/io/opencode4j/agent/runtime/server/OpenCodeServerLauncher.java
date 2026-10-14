package io.opencode4j.agent.runtime.server;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.EnvironmentVariables;
import io.opencode4j.config.LocalServerDefaults;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launches an isolated {@code opencode serve} subprocess in a clean-room environment,
 * capturing the dynamic port and generated authentication password from stdout.
 */
public final class OpenCodeServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeServerLauncher.class);

    private static final String BINARY_NAME = "opencode";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String DATA_DIRECTORY = "data";
    private static final String AUTH_FILE = "auth.json";
    private static final Map<String, String> CLEAN_ROOM_ENV_DIRECTORIES = Map.of(
            EnvironmentVariables.HOME, "home",
            EnvironmentVariables.XDG_CONFIG_HOME, "config",
            EnvironmentVariables.XDG_DATA_HOME, DATA_DIRECTORY,
            EnvironmentVariables.XDG_STATE_HOME, "state",
            EnvironmentVariables.XDG_CACHE_HOME, "cache"
    );
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(250);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("server password (\\S+)");
    private static final Pattern PORT_PATTERN = Pattern.compile("listening on http://[0-9a-zA-Z.]+:(?:(\\d+))");

    private final String binaryPath;
    private final int requestedPort;
    private final boolean cleanRoom;
    private final Duration startupTimeout;
    private final Map<String, String> environment;

    public OpenCodeServerLauncher(String binaryPath, int port, boolean cleanRoom, Duration startupTimeout) {
        this(binaryPath, port, cleanRoom, startupTimeout, null);
    }

    /**
     * @param environment extra environment variables for the spawned server process (for example
     *                    {@code NODE_TLS_REJECT_UNAUTHORIZED=0} for an endpoint with an internal CA); applied
     *                    after the launcher's own variables, so they take precedence
     */
    @Builder
    public OpenCodeServerLauncher(
            String binaryPath,
            int port,
            boolean cleanRoom,
            Duration startupTimeout,
            Map<String, String> environment
    ) {
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.binaryPath = binaryPath != null ? binaryPath : findBinary();
        this.requestedPort = port;
        this.cleanRoom = cleanRoom;
        this.startupTimeout = startupTimeout != null ? startupTimeout : DEFAULT_STARTUP_TIMEOUT;
    }

    public static OpenCodeServerLauncher create() {
        return new OpenCodeServerLauncher(null, 0, false, DEFAULT_STARTUP_TIMEOUT);
    }

    /**
     * Spawns the server process and blocks until the server is listening and authenticated.
     *
     * @return the running server instance
     * @throws IOException if process creation or I/O fails
     * @throws InterruptedException if thread is interrupted while waiting
     */
    public OpenCodeServerInstance launch() throws IOException, InterruptedException {
        Path cleanRoomDir = cleanRoom ? prepareCleanRoom() : null;
        Process process = startProcess(cleanRoomDir);
        OpenCodeServerInstance instance = awaitServerStartup(process, cleanRoomDir);

        // Add shutdown hook to prevent orphaned processes
        Runtime.getRuntime().addShutdownHook(new Thread(instance::close));

        waitForHealth(instance.toClient(), startupTimeout);

        log.info("Launched OpenCode server on port {} (PID {})", instance.getPort(), process.pid());
        return instance;
    }

    private Path prepareCleanRoom() throws IOException {
        Path directory = Files.createTempDirectory("opencode-cleanroom-");
        for (String subdirectory : CLEAN_ROOM_ENV_DIRECTORIES.values()) {
            Files.createDirectories(directory.resolve(subdirectory));
        }
        Path dataDirectory = Files.createDirectories(directory.resolve(DATA_DIRECTORY).resolve(BINARY_NAME));
        copyProviderAuth(dataDirectory);
        return directory;
    }

    private void copyProviderAuth(Path dataDirectory) {
        // Share provider credentials while keeping database, config, and skills isolated.
        String userHome = System.getProperty(USER_HOME_PROPERTY);
        if (userHome != null) {
            Path hostAuth = Path.of(userHome, ".local", "share", BINARY_NAME, AUTH_FILE);
            if (Files.exists(hostAuth)) {
                try {
                    Files.copy(hostAuth, dataDirectory.resolve(AUTH_FILE));
                } catch (IOException e) {
                    log.warn("Failed to copy provider credentials into clean room: {}", e.getMessage());
                }
            }
        }
    }

    private Process startProcess(Path cleanRoomDir) throws IOException {
        List<String> command = List.of(
                binaryPath, "serve", "--port", String.valueOf(requestedPort),
                "--hostname", LocalServerDefaults.LOOPBACK_HOST);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        env.put(EnvironmentVariables.OPENCODE_DISABLE_AUTOUPDATE, "true");

        if (cleanRoomDir != null) {
            CLEAN_ROOM_ENV_DIRECTORIES.forEach(
                    (name, directory) -> env.put(name, cleanRoomDir.resolve(directory).toString()));
        }
        env.putAll(environment);

        return pb.start();
    }

    private OpenCodeServerInstance awaitServerStartup(Process process, Path cleanRoomDir)
            throws InterruptedException {
        StartupState state = new StartupState(requestedPort);
        Thread.ofVirtual().name("opencode-launcher-stdout").start(() -> readServerOutput(process, state));

        boolean ready = state.readyLatch.await(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!ready || state.password.get() == null || state.port.get() <= 0) {
            process.destroyForcibly();
            throw new IllegalStateException("Failed to launch OpenCode server within " + startupTimeout.toMillis() + "ms");
        }
        return new OpenCodeServerInstance(process, state.port.get(), state.password.get(), cleanRoomDir);
    }

    private void readServerOutput(Process process, StartupState state) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEachOrdered(state::accept);
        } catch (UncheckedIOException e) {
            log.debug("Stopped reading OpenCode server output: {}", e.getCause().getMessage());
        } catch (IOException e) {
            log.debug("Stopped reading OpenCode server output: {}", e.getMessage());
        }
    }

    private static final class StartupState {
        private final AtomicReference<String> password = new AtomicReference<>();
        private final AtomicInteger port;
        private final AtomicBoolean listening = new AtomicBoolean(false);
        private final CountDownLatch readyLatch = new CountDownLatch(1);

        private StartupState(int requestedPort) {
            port = new AtomicInteger(requestedPort);
        }

        private void accept(String line) {
            Matcher passwordMatcher = PASSWORD_PATTERN.matcher(line);
            if (passwordMatcher.find()) {
                password.set(passwordMatcher.group(1));
            }
            Matcher portMatcher = PORT_PATTERN.matcher(line);
            if (portMatcher.find()) {
                try {
                    port.set(Integer.parseInt(portMatcher.group(1)));
                } catch (NumberFormatException e) {
                    log.warn("Invalid port in OpenCode server output: {}", portMatcher.group(1));
                }
            }
            if (line.contains("listening")) {
                listening.set(true);
            }
            if (listening.get() && password.get() != null) {
                readyLatch.countDown();
            }
        }
    }

    private void waitForHealth(OpenCodeClient client, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                var info = client.server().info();
                if (info != null && info.version() != null) {
                    return;
                }
            } catch (Exception ignored) {
            }
            LockSupport.parkNanos(HEALTH_POLL_INTERVAL.toNanos());
        }
        throw new IllegalStateException("Server failed health check after launch");
    }

    private static String findBinary() {
        String env = System.getenv(EnvironmentVariables.OPENCODE_BIN);
        String home = System.getProperty(USER_HOME_PROPERTY);
        String homeBinary = home != null ? new File(home, ".opencode/bin/opencode").getAbsolutePath() : null;
        return Stream.of(env, "/opt/homebrew/bin/opencode", "/usr/local/bin/opencode", homeBinary)
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.isBlank())
                .filter(candidate -> new File(candidate).canExecute())
                .findFirst()
                .orElse(BINARY_NAME);
    }

}
