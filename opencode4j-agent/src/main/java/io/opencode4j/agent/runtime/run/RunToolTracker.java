package io.opencode4j.agent.runtime.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.event.ToolCalled;
import io.opencode4j.event.ToolStarted;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.val;

/** Tool correlation and MCP unmasking, accessed under the run's state lock. */
final class RunToolTracker {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MCP_CALL = Pattern.compile("tools\\.([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)\\(");

    private record Key(String sessionId, String callId) { }

    @Builder(toBuilder = true)
    record Tool(String rawName, String name, String arguments, long startTime) { }

    private final Map<Key, Tool> tools = new HashMap<>();

    void started(ToolStarted event, long now) {
        tools.put(new Key(event.sessionId(), event.callId()), Tool.builder()
                .rawName(event.name()).name(event.name()).arguments("{}").startTime(now).build());
    }

    Tool called(ToolCalled event, long now) {
        val key = new Key(event.sessionId(), event.callId());
        val previous = tools.getOrDefault(key, Tool.builder().name("tool").arguments("{}").startTime(now).build());
        String arguments;
        try {
            arguments = MAPPER.writeValueAsString(event.input());
        } catch (JsonProcessingException e) {
            arguments = event.input().toString();
        }
        String name = previous.name();
        if ("execute".equals(previous.rawName())) {
            val matcher = MCP_CALL.matcher(String.valueOf(event.input().getOrDefault("code", "")));
            if (matcher.find()) {
                name = matcher.group(1) + "." + matcher.group(2);
            }
        }
        val tool = previous.toBuilder().name(name).arguments(arguments).build();
        tools.put(key, tool);
        return tool;
    }

    Tool finished(String sessionId, String callId, long now) {
        val tool = tools.remove(new Key(sessionId, callId));
        return tool != null ? tool : Tool.builder().name("tool").arguments("{}").startTime(now).build();
    }
}
