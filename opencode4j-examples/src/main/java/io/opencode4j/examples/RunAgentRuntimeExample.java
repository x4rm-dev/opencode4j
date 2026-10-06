package io.opencode4j.examples;

import io.opencode4j.OpenCode;
import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.agent.runtime.server.OpenCodeServerLauncher;
import io.opencode4j.agent.runtime.server.OpenCodeServerInstance;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.EnvironmentVariables;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.SkillSpec;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.util.Objects;

/**
 * End-to-end example demonstrating the transport-agnostic {@link io.opencode4j.runtime.AgentRuntime}:
 * <ul>
 *     <li>Exposing Java methods as tools to OpenCode over an in-JVM loopback MCP server</li>
 *     <li>Registering workspace skills with prompt attachment activation</li>
 *     <li>Hierarchical sub-agent delegation</li>
 *     <li>Real-time event streaming with path tagging and tool unmasking</li>
 * </ul>
 */
public final class RunAgentRuntimeExample {

    public static class InventoryService {
        @Tool(description = "Looks up current stock quantity of a warehouse item by SKU")
        public int checkStock(@Param(name = "sku", description = "Item SKU, e.g. SKU-100") String sku, ToolContext ctx) {
            System.out.printf("  [Java Method invoked] checkStock(%s) from session %s%n", sku, ctx.sessionId());
            return switch (sku.toUpperCase()) {
                case "SKU-APPLE" -> 42;
                case "SKU-BANANA" -> 150;
                default -> 0;
            };
        }
    }

    public static void main(String[] args) throws Exception {
        String serverUrl = System.getenv(EnvironmentVariables.OPENCODE_SERVER_URL);
        String password = System.getenv(EnvironmentVariables.OPENCODE_SERVER_PASSWORD);
        String model = System.getenv().getOrDefault(
                EnvironmentVariables.OPENCODE_MODEL, "github-copilot/gpt-6.1-sol");

        System.out.println("=== OpenCode4J AgentRuntime Example ===");
        if (serverUrl != null && !serverUrl.isBlank()) {
            System.out.println("Server URL: " + serverUrl);
            System.out.println("Password provided: " + (password != null && !password.isBlank()));
        } else {
            System.out.println("Server: auto-launching local clean-room subprocess (OPENCODE_SERVER_URL not set)");
        }
        System.out.println("Model: " + model);

        OpenCodeServerInstance managedServer = null;
        OpenCodeClient client;

        if (serverUrl != null && !serverUrl.isBlank()) {
            OpenCodeConfig config = OpenCodeConfig.builder(serverUrl)
                    .servePassword(password)
                    .build();
            client = OpenCodeClient.create(config);
            try {
                var info = client.server().info();
                System.out.printf("Connected to OpenCode server %s (PID %s)%n", info.version(), info.pid());
            } catch (Exception e) {
                System.err.printf("%n[ERROR] Could not connect to OpenCode server at %s: %s%n", serverUrl, e.getMessage());
                System.err.println("Ensure the server is running on that port, and set OPENCODE_SERVER_PASSWORD if secured.");
                System.err.println("Or unset OPENCODE_SERVER_URL to let OpenCode4J launch an isolated server automatically.");
                System.exit(1);
                return;
            }
        } else {
            managedServer = OpenCodeServerLauncher.builder()
                    .build()
                    .launch();

            client = managedServer.toClient();
            System.out.println("Clean-room server listening on " + managedServer.getUrl());
        }

        try {
            // 1. Define sub-agent: word reverser
            AgentSpec reverser = AgentSpec.builder("reverser")
                    .description("Specialized subagent that reverses words")
                    .instruction("You reverse any text given to you. Reply only with the reversed word.")
                    .model(model)
                    .build();

            // 2. Define root agent: coordinator with Java inventory tool and protocol skill
            AgentSpec coordinator = AgentSpec.builder("coordinator")
                    .description("Store coordinator")
                    .instruction("You are a store coordinator. When asked for inventory, use the checkStock tool. "
                            + "When asked to reverse text, delegate to the reverser sub-agent. "
                            + "Always follow the store-policy skill.")
                    .model(model)
                    .javaTools(new InventoryService())
                    .skill(SkillSpec.always("store-policy", "Store policies",
                            "Always greet the customer politely and end every message with 'HAVE A NICE DAY!'"))
                    .subAgent(reverser)
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            // 3. Open session on runtime
            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(client);

            System.out.println("\nOpening AgentSession for coordinator tree (depth=" + coordinator.calculateDepth() + ")...");
            try (AgentSession session = runtime.open(coordinator)) {
                System.out.println("Session established with ID: " + session.sessionId());

                // 4. Send prompt turn and stream events
                String prompt = "Check stock for SKU-APPLE and also reverse the word 'KOTLIN'.";
                System.out.println("\nPrompt: " + prompt);
                System.out.print("Streaming output: ");

                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                    } else if (event instanceof ToolCallCompletedEvent tc) {
                        System.out.printf("%n  [Tool Completed: %s by %s]%n", tc.toolName(), tc.agentPath());
                    } else if (event instanceof DelegationStartedEvent ds) {
                        System.out.printf("%n  [Delegating to %s: %s]%n", ds.targetAgentName(), ds.prompt());
                    }
                });

                AgentResult result = run.await();
                System.out.println("\n\n--- Result Summary ---");
                System.out.println("Success: " + result.isSuccess());
                if (!result.isSuccess()) {
                    System.out.println("Error: " + result.errorMessage());
                }
                System.out.println("Total Cost: $" + result.totalCost());
                System.out.println("Total Tokens: " + result.tokenUsage().total());
                System.out.println("Tool Calls Made: " + result.toolExecutions().size());
                for (var tool : result.toolExecutions()) {
                    System.out.printf("  - %s (%dms): %s%n", tool.toolName(), tool.durationMs(), tool.result());
                }
            }

            runtime.close();
        } finally {
            if (managedServer != null) {
                managedServer.close();
                System.out.println("Cleaned up managed server.");
            }
        }
    }
}
