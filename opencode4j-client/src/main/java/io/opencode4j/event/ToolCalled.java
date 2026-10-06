package io.opencode4j.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The model issued a tool call with its fully parsed input; the tool is now executing. */
public record ToolCalled(
        String sessionId,
        String messageId,
        String callId,
        Map<String, Object> input
) implements OpenCodeEvent {

    public ToolCalled {
        // Map.copyOf would reject null values, which a tool input may legitimately contain.
        input = input == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    @Override
    public String type() {
        return "session.tool.called";
    }
}
