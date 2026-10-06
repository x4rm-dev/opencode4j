package io.opencode4j.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot of one tool call.
 *
 * @param callId the server's call ID, or {@code null} when the server did not send one
 * @param name   the tool name, for example {@code shell}
 * @param input  the parsed input; empty until the call has been issued
 * @param output the textual result once {@link ToolStatus#COMPLETED}, otherwise {@code null}
 * @param error  the failure message when {@link ToolStatus#FAILED}, otherwise {@code null}
 */
public record ToolCall(
        String callId,
        String name,
        ToolStatus status,
        Map<String, Object> input,
        String output,
        String error
) {

    public ToolCall {
        input = input == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public ToolCall withStatus(ToolStatus newStatus) {
        return new ToolCall(callId, name, newStatus, input, output, error);
    }

    public ToolCall withInput(Map<String, Object> newInput) {
        return new ToolCall(callId, name, status, newInput, output, error);
    }

    public ToolCall completed(String newOutput) {
        return new ToolCall(callId, name, ToolStatus.COMPLETED, input, newOutput, null);
    }

    public ToolCall failed(String newError) {
        return new ToolCall(callId, name, ToolStatus.FAILED, input, output, newError);
    }
}
