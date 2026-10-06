package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import java.util.List;
import java.util.Optional;

/**
 * Example 5: Structured typed result.
 *
 * <p>Demonstrates configuring a Java record as the expected output via {@code resultType(Class)},
 * which auto-deploys a schema-validated in-JVM {@code submit_result} MCP tool and binds
 * the model's structured payload into a strongly-typed Java instance.</p>
 */
public final class StructuredResultExample {

    /** Strongly-typed domain record for the agent's structured response. */
    public record CodeReview(
            String verdict, // "APPROVE" or "REQUEST_CHANGES"
            int score,      // 1 to 10
            List<String> strengths,
            List<String> concerns
    ) {}

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 5: Structured Typed Result")) {
            // 1. Declare agent with CodeReview as target result type
            AgentSpec spec = AgentSpec.builder("code_reviewer")
                    .description("Automated PR reviewer")
                    .instruction("You are a strict code reviewer. Evaluate the submitted code change, "
                            + "and submit your evaluation by calling the submit_result tool.")
                    .model(env.model())
                    .resultType(CodeReview.class)
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                String codeChange = """
                        public int divide(int a, int b) {
                            // Division without zero-check
                            return a / b;
                        }
                        """;

                String prompt = "Review this Java function:\n" + codeChange;
                System.out.println("User: " + prompt);
                System.out.print("Agent: ");

                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    } else if (event instanceof ToolCallCompletedEvent tc && "submit_result".equals(tc.toolName())) {
                        System.out.printf("%n  [submit_result accepted by JVM: %s]%n  ", tc.output());
                    }
                });

                AgentResult result = run.await();

                System.out.println("\n\n--- Structured Result Extraction ---");
                System.out.println("Success: " + result.isSuccess());

                Optional<CodeReview> reviewOpt = result.typedResult(CodeReview.class);
                if (reviewOpt.isPresent()) {
                    CodeReview review = reviewOpt.get();
                    System.out.println("Verdict:   " + review.verdict());
                    System.out.println("Score:     " + review.score() + "/10");
                    System.out.println("Strengths: " + review.strengths());
                    System.out.println("Concerns:  " + review.concerns());
                } else {
                    System.err.println("No typed CodeReview was submitted by the model.");
                }
            }

            runtime.close();
        }
    }
}
