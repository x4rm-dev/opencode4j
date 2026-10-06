package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.PermissionRequestedEvent;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.RunLimits;
import io.opencode4j.runtime.spec.SkillSpec;
import java.time.Duration;

/**
 * Example 6: Agent with Skills, Resource Limits, and Human-in-the-Loop Permissions.
 *
 * <p>Demonstrates:
 * <ul>
 *     <li>Injecting specialized behavioral instructions through {@link SkillSpec}</li>
 *     <li>Setting execution limits (maximum cost budget, step count cap, wall-clock timeout)</li>
 *     <li>Intercepting action requests through {@link PermissionRequestedEvent} with approval policies</li>
 * </ul>
 */
public final class AgentWithSkillsAndLimitsExample {

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 6: Skills, Limits, and Approval Policies")) {
            // 1. Define behavioral skill
            SkillSpec securitySkill = SkillSpec.always(
                    "security-audit-standard",
                    "Security audit protocol",
                    """
                    # Security Audit Standard
                    Always categorize vulnerabilities by severity: CRITICAL, HIGH, MEDIUM, LOW.
                    Provide exact code remediation snippets.
                    """
            );

            // 2. Define safety and budget limits
            RunLimits limits = RunLimits.builder()
                    .maxSteps(5)
                    .maxCost(0.50) // $0.50 budget cap
                    .timeout(Duration.ofSeconds(60))
                    .build();

            // 3. Define agent with permission ask rules for destructive actions
            AgentSpec spec = AgentSpec.builder("auditor")
                    .description("Security auditor with policy enforcement")
                    .instruction("You are a security auditor. Audit the user's scenario according to the security audit standard.")
                    .model(env.model())
                    .skill(securitySkill)
                    .limits(limits)
                    .permission(PermissionRule.ask("shell", "*"))
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                String prompt = "Review this setup: API key hardcoded in git repository and exposed via public HTTP endpoint.";
                System.out.println("User: " + prompt + "\n");
                System.out.print("Agent: ");

                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    } else if (event instanceof StepEndedEvent se) {
                        System.out.printf("%n  [Step %s finish=%s cost=$%.4f tokens=%d]%n  ",
                                se.messageId(), se.finishReason(), se.cost(), se.tokens().total());
                    } else if (event instanceof PermissionRequestedEvent pe) {
                        System.out.printf("%n  [Approval Policy] Agent asks permission for '%s' on %s%n",
                                pe.action(), pe.resources());
                        // Policy decision: approve read/echo actions, reject shell
                        if ("shell".equals(pe.action())) {
                            System.out.println("  [Decision] Rejected: Shell execution forbidden by policy");
                            pe.reject("Shell execution is disallowed in audit mode.");
                        } else {
                            System.out.println("  [Decision] Allowed once");
                            pe.allowOnce();
                        }
                    }
                });

                AgentResult result = run.await();

                System.out.println("\n\n--- Execution Summary ---");
                System.out.println("Success:      " + result.isSuccess());
                System.out.println("Truncated:    " + result.truncated());
                System.out.printf("Total Cost:   $%.4f%n", result.totalCost());
                System.out.println("Total Tokens: " + result.tokenUsage().total());
            }

            runtime.close();
        }
    }
}
