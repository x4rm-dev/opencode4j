package io.opencode4j.agent.runtime.observe;

/**
 * Receives runtime measurements. Bind it to Micrometer, Dropwizard Metrics or a log; every method has a no-op
 * default and must return quickly.
 */
public interface RuntimeMetrics {

    /** A run ended. */
    default void runFinished(RunSummary summary) {
    }

    /**
     * An in-JVM tool call finished.
     *
     * @param server the tool server's id, for example {@code alfred}
     */
    default void toolCall(String server, String tool, boolean failed, long durationNanos) {
    }

    static RuntimeMetrics noop() {
        return new RuntimeMetrics() { };
    }
}
