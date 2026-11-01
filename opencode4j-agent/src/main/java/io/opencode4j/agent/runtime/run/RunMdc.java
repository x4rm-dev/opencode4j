package io.opencode4j.agent.runtime.run;

import org.slf4j.MDC;

/**
 * Puts {@code ocRunId} and {@code ocSessionId} into the SLF4J MDC for the duration of a block, so log lines
 * written while a run's events or tool calls are handled carry them.
 */
public final class RunMdc implements AutoCloseable {

    public static final String RUN_ID = "ocRunId";
    public static final String SESSION_ID = "ocSessionId";

    private final String previousRun;
    private final String previousSession;

    private RunMdc(String runId, String sessionId) {
        previousRun = MDC.get(RUN_ID);
        previousSession = MDC.get(SESSION_ID);
        put(RUN_ID, runId);
        put(SESSION_ID, sessionId);
    }

    public static RunMdc of(String runId, String sessionId) {
        return new RunMdc(runId, sessionId);
    }

    @Override
    public void close() {
        put(RUN_ID, previousRun);
        put(SESSION_ID, previousSession);
    }

    private static void put(String key, String value) {
        if (value == null || value.isEmpty()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
