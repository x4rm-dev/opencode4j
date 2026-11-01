package io.opencode4j.runtime;

/**
 * Rewrites text before it leaves the runtime: event payloads delivered to listeners and the text, errors and tool
 * inputs and outputs of {@link AgentResult}. Use it to mask credentials or personal data that tools or the model
 * may echo.
 *
 * <p>Streamed deltas are redacted one at a time, so a value split across two deltas is not caught there; the
 * final answer in {@link AgentResult#text()} is redacted whole.</p>
 */
@FunctionalInterface
public interface TextRedactor {

    /** The text to expose instead of {@code text}; {@code null} in, {@code null} out. */
    String redact(String text);

    static TextRedactor none() {
        return text -> text;
    }
}
