package io.opencode4j.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentResultTest {

    record WeatherReport(String city, int temp) {}

    @Test
    void accumulatesTokensAndExtractsTypedResult() {
        TokenUsage step1 = new TokenUsage(100, 20, 5, 10, 0);
        TokenUsage step2 = new TokenUsage(200, 30, 0, 0, 5);
        TokenUsage total = step1.add(step2);

        assertEquals(300, total.input());
        assertEquals(50, total.output());
        assertEquals(5, total.reasoning());
        assertEquals(355, total.total());

        WeatherReport report = new WeatherReport("San Francisco", 65);
        AgentResult result = AgentResult.builder("run-1", "ses-1")
                .success(true)
                .text("Weather in SF: 65F")
                .typedResult(report)
                .totalCost(0.015)
                .tokenUsage(total)
                .toolExecutions(List.of(new ToolExecution(
                        "call_1", "getWeather", "root", "{\"city\":\"SF\"}", "65F", false, 120)))
                .build();

        assertTrue(result.success());
        assertEquals("Weather in SF: 65F", result.text());
        assertTrue(result.typedResult(WeatherReport.class).isPresent());
        assertEquals("San Francisco", result.typedResult(WeatherReport.class).get().city());
        assertFalse(result.typedResult(String.class).isPresent());
        assertEquals(0.015, result.totalCost());
        assertEquals(1, result.toolExecutions().size());
    }

    @Test
    void verifiesAgentEventContract() {
        AgentEvent event = new TextDeltaEvent(
                "run-10", "ses-20", "worker", "boss/worker", 1000L, "msg-1", 1, "Hello"
        );
        assertEquals("run-10", event.runId());
        assertEquals("ses-20", event.sessionId());
        assertEquals("worker", event.agentName());
        assertEquals("boss/worker", event.agentPath());
        assertEquals(1000L, event.timestamp());
    }
}
