package io.opencode4j.agent.runtime.workspace;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.EnvironmentVariables;
import io.opencode4j.model.AgentSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles workspace location canonicalization, agent warm-up polling, and cache disposal.
 */
public final class LocationManager {

    private static final Logger log = LoggerFactory.getLogger(LocationManager.class);
    private static final Duration DEFAULT_WARMUP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(300);

    private final OpenCodeClient client;

    public LocationManager(OpenCodeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Resolves the real canonical path for the given workspace directory.
     *
     * @param directory directory path to canonicalize
     * @return the real canonical path
     */
    public Path canonicalize(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            if (Files.exists(directory)) {
                return directory.toRealPath();
            }
            return directory.toAbsolutePath().normalize();
        } catch (IOException e) {
            return directory.toAbsolutePath().normalize();
        }
    }

    /**
     * Polls the server until all declared agents appear in the workspace location catalog.
     *
     * <p>On OpenCode 2.x, a freshly accessed workspace location often answers its very first
     * query with an empty catalog while building services in the background. Polling ensures
     * sessions are created only after the agents are fully registered.</p>
     *
     * @param canonicalDir   canonical workspace path
     * @param expectedAgents set of expected agent names/IDs
     * @param timeout        maximum duration to wait
     * @throws IllegalStateException if the agents do not appear before timeout
     */
    public void warmLocation(
            Path canonicalDir,
            Set<String> expectedAgents,
            Duration timeout) throws InterruptedException {
        Objects.requireNonNull(canonicalDir, "canonicalDir");
        Objects.requireNonNull(expectedAgents, "expectedAgents");
        Duration effectiveTimeout = timeout == null ? DEFAULT_WARMUP_TIMEOUT : timeout;

        String dirStr = canonicalDir.toString();
        long deadline = System.nanoTime() + effectiveTimeout.toNanos();
        WarmupState state = new WarmupState();

        while (System.nanoTime() < deadline) {
            if (pollLocation(dirStr, expectedAgents, state)) {
                return;
            }
            LockSupport.parkNanos(POLL_INTERVAL.toNanos());
        }

        throw warmupFailure(dirStr, expectedAgents, effectiveTimeout, state);
    }

    private boolean pollLocation(String directory, Set<String> expectedAgents, WarmupState state) {
        state.attempts++;
        try {
            state.lastSeen = visibleAgentIds(directory);
            if (state.lastSeen.containsAll(expectedAgents)) {
                log.debug("Workspace {} warmed up in {} attempts; visible agents: {}",
                        directory, state.attempts, state.lastSeen);
                return true;
            }
            log.trace("Warmup attempt {} on {}: visible={}, expected={}",
                    state.attempts, directory, state.lastSeen, expectedAgents);
        } catch (OpenCodeException e) {
            switch (e.statusCode()) {
                case 401 -> throw new IllegalStateException(
                        "Authentication failed connecting to OpenCode server (HTTP 401): please verify "
                                + EnvironmentVariables.OPENCODE_SERVER_PASSWORD, e);
                case 403 -> throw new IllegalStateException("Access forbidden by OpenCode server (HTTP 403)", e);
            }
            state.lastException = e;
            log.trace("Warmup poll failed on attempt {}: {}", state.attempts, e.getMessage());
        }
        return false;
    }

    private IllegalStateException warmupFailure(
            String directory, Set<String> expectedAgents, Duration timeout, WarmupState state) {
        String causeMsg = state.lastException != null
                ? " (last error: " + state.lastException.getMessage() + ")" : "";
        return new IllegalStateException(
                "Workspace location " + directory + " failed to register agents " + expectedAgents
                        + " within " + timeout.toMillis() + "ms. Visible agents were: " + state.lastSeen + causeMsg,
                state.lastException
        );
    }

    private static final class WarmupState {
        private int attempts;
        private Set<String> lastSeen = Set.of();
        private OpenCodeException lastException;
    }

    private Set<String> visibleAgentIds(String directory) {
        List<AgentSummary> summaries = client.agents().list(directory);
        return summaries == null ? Set.of() : summaries.stream()
                .map(AgentSummary::id)
                .collect(Collectors.toSet());
    }

    /**
     * Disposes the cached services for a location on the server so its next use boots fresh.
     *
     * @param canonicalDir canonical workspace path
     */
    public void evictLocation(Path canonicalDir) {
        if (canonicalDir == null) {
            return;
        }
        try {
            String path = client.transport().withLocation("/api/debug/location", canonicalDir.toString());
            client.transport().send(HttpMethod.DELETE, path, null, Void.class);
            log.debug("Evicted location cache for {}", canonicalDir);
        } catch (Exception e) {
            log.warn("Failed to evict location cache for {}: {}", canonicalDir, e.getMessage());
        }
    }
}
