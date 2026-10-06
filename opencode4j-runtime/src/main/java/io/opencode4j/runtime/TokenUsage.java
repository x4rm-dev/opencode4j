package io.opencode4j.runtime;

/**
 * Token counts reported for an agent step or cumulative run.
 */
public record TokenUsage(
        long input,
        long output,
        long reasoning,
        long cacheRead,
        long cacheWrite
) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0, 0);

    public long total() {
        return input + output + reasoning;
    }

    public TokenUsage add(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                input + other.input,
                output + other.output,
                reasoning + other.reasoning,
                cacheRead + other.cacheRead,
                cacheWrite + other.cacheWrite
        );
    }
}
