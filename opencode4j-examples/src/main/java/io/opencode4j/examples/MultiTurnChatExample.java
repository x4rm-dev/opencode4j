package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import java.util.List;

/**
 * Example 4: Multi-turn chat conversation.
 *
 * <p>Demonstrates maintaining conversational state and memory across multiple sequential turns
 * within the same {@link AgentSession} without restarting or losing context.</p>
 */
public final class MultiTurnChatExample {

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 4: Multi-Turn Conversational Chat")) {
            AgentSpec spec = AgentSpec.builder("concise_chat")
                    .description("Friendly and concise conversational assistant")
                    .instruction("You are a friendly, witty assistant. Answer in one or two clear sentences. "
                            + "Remember all facts the user shares with you across turns.")
                    .model(env.model())
                    .permission(PermissionRule.denyAll())
                    .build();

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                System.out.printf("Opened conversational session: %s%n%n", session.sessionId());

                List<String> userTurns = List.of(
                        "Hello! My name is Arthur Dent and I am an astronomer from London.",
                        "What is my profession, and what city am I from?",
                        "Could you suggest a one-day weekend trip for me based on my location?"
                );

                for (int i = 0; i < userTurns.size(); i++) {
                    String userMessage = userTurns.get(i);
                    System.out.printf("--- Turn %d ---%n", i + 1);
                    System.out.println("User:  " + userMessage);
                    System.out.print("Agent: ");

                    AgentRun run = session.send(userMessage, event -> {
                        if (event instanceof TextDeltaEvent delta) {
                            System.out.print(delta.delta());
                            System.out.flush();
                        }
                    });

                    AgentResult result = run.await();
                    System.out.printf("%n[tokens: %d, cost: $%.4f]%n%n",
                            result.tokenUsage().total(), result.totalCost());
                }
            }

            runtime.close();
        }
    }
}
