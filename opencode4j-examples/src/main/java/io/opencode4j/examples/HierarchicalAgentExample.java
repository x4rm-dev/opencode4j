package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.DelegationCompletedEvent;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;

/**
 * Example 3: Hierarchical multi-agent delegation.
 *
 * <p>Demonstrates constructing a multi-tier agent hierarchy:
 * {@code director -> lead_analyst -> bullet_summarizer}. The runtime automatically derives
 * the subagent depth, registers subagent modes, and tracks delegations across parent-child sessions.</p>
 */
public final class HierarchicalAgentExample {

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 3: Hierarchical Multi-Agent Delegation")) {
            // Tier 3: Leaf specialist (formats raw findings into 3 punchy bullet points)
            AgentSpec summarizer = AgentSpec.builder("bullet_summarizer")
                    .description("Formats analytical findings into exactly 3 punchy bullet points")
                    .instruction("You are a formatting specialist. Take the text given to you and format it "
                            + "into exactly 3 punchy bullet points. Return only the bullet points.")
                    .model(env.model())
                    .build();

            // Tier 2: Mid-level lead (gathers key architectural trade-offs, delegates formatting to summarizer)
            AgentSpec analyst = AgentSpec.builder("lead_analyst")
                    .description("Analyzes technology architecture and delegates formatting to bullet_summarizer")
                    .instruction("You are a technical analyst. When asked to evaluate an architecture, "
                            + "identify key architectural trade-offs, then delegate to 'bullet_summarizer' via the subagent tool "
                            + "to format your findings.")
                    .model(env.model())
                    .subAgent(summarizer)
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            // Tier 1: Root director (receives user question, delegates to lead_analyst)
            AgentSpec director = AgentSpec.builder("director")
                    .description("Engineering director orchestrating technical evaluations")
                    .instruction("You are an engineering director. You never perform analysis directly. "
                            + "Delegate evaluation requests to 'lead_analyst' via the subagent tool and present the final output.")
                    .model(env.model())
                    .subAgent(analyst)
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            System.out.printf("Constructed agent hierarchy with derived tree depth: %d%n%n", director.calculateDepth());

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(director)) {
                String prompt = "Evaluate monolithic architecture vs microservices for a 5-person startup.";
                System.out.println("User: " + prompt + "\n");
                System.out.print("Output: ");

                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    } else if (event instanceof DelegationStartedEvent ds) {
                        System.out.printf("%n  [Delegation] %s -> delegating to '%s': \"%s\"%n  ",
                                ds.agentPath(), ds.targetAgentName(), ds.prompt());
                    } else if (event instanceof DelegationCompletedEvent dc) {
                        System.out.printf("%n  [Completed Delegation to '%s']%n  ", dc.targetAgentName());
                    }
                });

                AgentResult result = run.await();

                System.out.println("\n\n--- Delegation Breakdown ---");
                System.out.println("Success: " + result.isSuccess());
                System.out.println("Step turns executed: " + result.steps().size());
                for (var step : result.steps()) {
                    System.out.printf("  * Step %d by %s: finish=%s cost=$%.4f%n",
                            step.ordinal(), step.agentPath(), step.finishReason(), step.cost());
                }
            }

            runtime.close();
        }
    }
}
