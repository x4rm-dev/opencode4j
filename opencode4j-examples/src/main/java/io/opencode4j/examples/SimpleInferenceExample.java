package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;

/**
 * Example 1: Simple inference.
 *
 * <p>Demonstrates creating a single agent with a system instruction, sending a user prompt,
 * streaming the token response in real time, and capturing completion metrics.</p>
 */
public final class SimpleInferenceExample {

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 1: Simple Inference")) {
            // 1. Define agent specification
            AgentSpec spec = AgentSpec.builder("explainer")
                    .description("Clear technical teacher")
                    .instruction("You explain computer science concepts simply and concisely in 2 or 3 short paragraphs.")
                    .model(env.model())
                    .permission(PermissionRule.denyAll())
                    .build();

            // 2. Open session
            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                System.out.println("Session ID: " + session.sessionId());

                String prompt = "What is the difference between a process and a thread?";
                System.out.println("User: " + prompt + "\n");
                System.out.print("Agent: ");

                // 3. Send prompt and stream output
                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    }
                });

                // 4. Await result
                AgentResult result = run.await();

                System.out.println("\n\n--- Turn Summary ---");
                System.out.println("Success:      " + result.isSuccess());
                System.out.println("Cost:         $" + result.totalCost());
                System.out.printf("Tokens:       in=%d out=%d total=%d%n",
                        result.tokenUsage().input(), result.tokenUsage().output(), result.tokenUsage().total());
            }

            runtime.close();
        }
    }
}
