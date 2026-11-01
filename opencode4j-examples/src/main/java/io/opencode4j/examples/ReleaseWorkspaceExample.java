package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.SessionOptions;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.WorkspaceValidation;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.PolicyRule;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * Example 8: a read-only service agent in a shared release workspace.
 *
 * <p>Materializes one deny-by-default workspace, validates it, serves a Java tool through one long-lived tool
 * server, and runs two independent conversations in the same workspace with a deliberately slow event listener,
 * which delays only its own conversation.</p>
 *
 * <p>The tool server is referenced by port and token directly here because the example may launch its own
 * OpenCode server. In production, start OpenCode with {@code ALFRED_MCP_PORT}/{@code ALFRED_MCP_TOKEN} in its
 * environment and use {@code tools.asMcpToolSpec("alfred", "ALFRED_MCP_PORT", "ALFRED_MCP_TOKEN")} so the token
 * is never written to disk.</p>
 */
public final class ReleaseWorkspaceExample {

    /** A read-only lookup the agent may call. */
    public static final class ServiceCatalog {
        @Tool(name = "catalog_lookup", description = "Returns the cluster and owner team of a service")
        public String lookup(@Param(name = "service") String service, ToolContext context) {
            return "{\"service\":\"" + service + "\",\"cluster\":\"aerospike-theo\",\"team\":\"trading\","
                    + "\"conversation\":\"" + context.rootSessionId() + "\"}";
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 8: Release workspace");
             OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(env.client())
                     .baseWorkspacesDir(Files.createTempDirectory("opencode4j-releases-"))
                     .build();
             JvmMcpServer tools = JvmMcpServer.builder()
                     .serverId("catalog").path("/mcp")
                     .javaTools(List.of(JavaMethodToolSpec.of(new ServiceCatalog())))
                     .resolver(runtime.toolCallResolver())
                     .build()) {

            AgentSpec agent = AgentSpec.builder("sre")
                    .description("Read-only production debugging assistant")
                    .instruction("Answer operational questions. Use catalog_catalog_lookup to find where a service "
                            + "runs. Say so when you do not know.")
                    .model(env.model())
                    .permission(PermissionRule.allow("catalog_*", "*"))
                    .build();

            Workspace release = runtime.materialize(WorkspaceSpec.builder("sre/rel_" + UUID.randomUUID()
                            .toString().substring(0, 8), agent)
                    .mcpServer(tools.asMcpToolSpec("catalog"))
                    .instruction("answers.md", "Answer in at most three sentences.")
                    .policy(PolicyRule.denyTool("shell"))
                    .allowInlineSecrets(true) // the per-boot loopback token above; see the class comment
                    .build());

            WorkspaceValidation validation = runtime.validate(release);
            System.out.println("Workspace valid: " + validation.valid() + " " + validation.problems()
                    + (validation.warnings().isEmpty() ? "" : " warnings=" + validation.warnings()));

            try (AgentSession fast = runtime.open(release, SessionOptions.builder().title("fast").build());
                 AgentSession slow = runtime.open(release, SessionOptions.builder().title("slow").build())) {
                var slowRun = slow.send("Where does the order-router service run?", event -> {
                    if (event instanceof TextDeltaEvent) {
                        sleep(200); // a rate-limited consumer, such as a chat message editor
                    }
                });
                var fastRun = fast.send("Where does the theo service run?", event -> {
                    if (event instanceof ToolCallCompletedEvent tool) {
                        System.out.println("[fast] " + tool.toolName() + " -> " + tool.output());
                    }
                });

                AgentResult fastResult = fastRun.await();
                System.out.println("[fast] " + fastResult.status() + ": " + fastResult.text());
                AgentResult slowResult = slowRun.await();
                System.out.println("[slow] " + slowResult.status() + ": " + slowResult.text());
            }
            release.retire();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
