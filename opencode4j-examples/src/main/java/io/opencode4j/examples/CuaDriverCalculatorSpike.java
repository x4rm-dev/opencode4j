package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.event.ToolCallStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import java.util.List;
import java.util.Set;

/**
 * Spike: an LLM drives Calculator through `cua-driver mcp` registered as a plain MCP server, with no Octane code
 * in the path. Only window-scoped cua tools are allowed (no whole-desktop capture, no kill_app, no clipboard).
 */
public final class CuaDriverCalculatorSpike {

    private static final Set<String> ALLOWED = Set.of(
            "launch_app", "list_windows", "get_window_state", "click", "type_text", "press_key", "hotkey");

    public static void main(String[] args) throws Exception {
        String driver = System.getProperty("user.home") + "/.local/bin/cua-driver";
        try (ExampleHelper env = ExampleHelper.init("Spike: LLM + cua-driver MCP, Calculator")) {
            AgentSpec.Builder spec = AgentSpec.builder("operator")
                    .instruction("You operate the user's Mac only through the cua tools. Always target a specific app "
                            + "window (pid and window_id from list_windows). Prefer element_token clicks from "
                            + "get_window_state; re-run get_window_state before every action batch because tokens go "
                            + "stale. Do not use any other tool.")
                    .model(env.model())
                    .tool(McpToolSpec.local("cua", "Computer use driver", List.of(driver, "mcp")))
                    .permission(PermissionRule.deny("*", "*"));
            ALLOWED.forEach(t -> spec.permission(PermissionRule.allow("cua_" + t, "*")));

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());
            try (var session = runtime.open(spec.build())) {
                String custom = System.getProperty("spike.prompt");
                var run = session.send(custom != null ? custom :
                        "Open the Calculator app, clear it, then compute 7 + 8 by pressing the buttons 7, +, 8 and =. "
                                + "Then read the display and answer with the number shown.",
                        ev -> {
                            if (ev instanceof ToolCallStartedEvent t) {
                                String a = String.valueOf(t.arguments());
                                System.out.println("  [tool] " + t.toolName() + " "
                                        + a.substring(0, Math.min(260, a.length())).replace("\n", " "));
                            }
                        });
                var result = run.await();
                System.out.println("\nSuccess=" + result.isSuccess() + " error=" + result.errorMessage());
                System.out.println("Answer: " + result.text());
                System.out.println("Tokens: " + result.tokenUsage() + " steps=" + result.steps().size());
            }
            runtime.close();
        }
    }
}
