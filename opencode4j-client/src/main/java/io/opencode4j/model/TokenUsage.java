package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenUsage(
        long input,
        long output,
        long reasoning,
        long cacheRead,
        long cacheWrite,
        double cost
) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0, 0, 0.0);

    public TokenUsage add(TokenUsage other) {
        if (other == null) return this;
        return new TokenUsage(
                this.input + other.input,
                this.output + other.output,
                this.reasoning + other.reasoning,
                this.cacheRead + other.cacheRead,
                this.cacheWrite + other.cacheWrite,
                this.cost + other.cost
        );
    }
}
