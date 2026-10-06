package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.event.ToolCallStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;

/**
 * Example 2: Agent with in-JVM Java tools.
 *
 * <p>Demonstrates annotating Java methods with {@link Tool} and {@link Param}, attaching them
 * to an {@link AgentSpec}, and allowing the model to invoke native Java code during execution.</p>
 */
public final class AgentWithToolsExample {

    /** Native Java service whose methods will be exposed as agent tools. */
    public static class CurrencyTools {

        @Tool(description = "Converts an amount from one fiat currency to another")
        public double convert(
                @Param(name = "amount", description = "Money amount to convert") double amount,
                @Param(name = "from", description = "Source 3-letter currency code, e.g. USD") String from,
                @Param(name = "to", description = "Target 3-letter currency code, e.g. EUR") String to,
                ToolContext context
        ) {
            System.out.printf("  [JVM Tool Invoked] convert(%.2f, %s -> %s) [runId=%s, session=%s]%n",
                    amount, from, to, context.runId(), context.sessionId());

            double rate = switch (from.toUpperCase() + "_" + to.toUpperCase()) {
                case "USD_EUR" -> 0.92;
                case "EUR_USD" -> 1.09;
                case "USD_GBP" -> 0.79;
                case "USD_JPY" -> 155.20;
                default -> 1.0;
            };
            return amount * rate;
        }

        @Tool(description = "Returns the current inflation rate percentage for a given country")
        public double getInflationRate(@Param(name = "country", description = "Country name or ISO code") String country) {
            System.out.printf("  [JVM Tool Invoked] getInflationRate(%s)%n", country);
            return switch (country.toLowerCase()) {
                case "us", "usa", "united states" -> 2.4;
                case "uk", "united kingdom" -> 2.3;
                case "germany", "eu" -> 2.0;
                default -> 3.0;
            };
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 2: Agent with Java Tools")) {
            // 1. Build agent with currency tools
            AgentSpec spec = AgentSpec.builder("financial_assistant")
                    .description("Financial advisor and currency calculator")
                    .instruction("You are a financial advisor. Use the available currency tools to answer questions. "
                            + "Always show your calculations.")
                    .model(env.model())
                    .javaTools(new CurrencyTools())
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            // 2. Open session
            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                String prompt = "How much is $250 USD in EUR, and what is the current US inflation rate?";
                System.out.println("User: " + prompt + "\n");
                System.out.print("Agent: ");

                // 3. Send prompt and watch tool executions
                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    } else if (event instanceof ToolCallStartedEvent tc) {
                        System.out.printf("%n  -> Starting tool: %s with args: %s%n  ", tc.toolName(), tc.arguments());
                    } else if (event instanceof ToolCallCompletedEvent tc) {
                        System.out.printf("%n  <- Tool %s finished: %s%n  ", tc.toolName(), tc.output());
                    }
                });

                AgentResult result = run.await();

                System.out.println("\n\n--- Tool Execution Breakdown ---");
                System.out.println("Success: " + result.isSuccess());
                System.out.println("Tools executed count: " + result.toolExecutions().size());
                for (var tool : result.toolExecutions()) {
                    System.out.printf("  * %s (took %dms): args=%s => output=%s%n",
                            tool.toolName(), tool.durationMs(), tool.arguments(), tool.result());
                }
            }

            runtime.close();
        }
    }
}
