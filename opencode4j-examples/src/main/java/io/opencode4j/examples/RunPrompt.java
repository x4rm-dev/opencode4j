package io.opencode4j.examples;

import io.opencode4j.OpenCode;
import io.opencode4j.OpenCodeException;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.model.ServerInfo;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.PermissionRequestedEvent;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;

/**
 * CLI runner that executes an agent run against an OpenCode server using {@link io.opencode4j.runtime.AgentRuntime}.
 */
public final class RunPrompt {

    private static final String DEFAULT_URL = "http://localhost:4096";
    private static final String DEFAULT_PROMPT = "Say hello from opencode4j and describe what you can do.";

    public static final class EnvironmentVariables {
        public static final String OPENCODE_URL = "OPENCODE_URL";
        public static final String OPENCODE_SERVER_USERNAME = "OPENCODE_SERVER_USERNAME";
        public static final String OPENCODE_SERVER_PASSWORD = "OPENCODE_SERVER_PASSWORD";
        public static final String OPENCODE_MODEL = "OPENCODE_MODEL";
        public static final String OPENCODE_AGENT = "OPENCODE_AGENT";
        public static final String OPENCODE_AUTO_APPROVE = "OPENCODE_AUTO_APPROVE";
    }

    private RunPrompt() {
    }

    public static void main(String[] args) {
        String url = env(EnvironmentVariables.OPENCODE_URL, DEFAULT_URL);
        String prompt = args.length > 0 ? args[0] : DEFAULT_PROMPT;
        boolean autoApprove = Boolean.parseBoolean(env(EnvironmentVariables.OPENCODE_AUTO_APPROVE, "false"));

        OpenCodeConfig config = OpenCodeConfig.builder(url)
                .serveUsername(System.getenv(EnvironmentVariables.OPENCODE_SERVER_USERNAME))
                .servePassword(System.getenv(EnvironmentVariables.OPENCODE_SERVER_PASSWORD))
                .defaultModel(System.getenv(EnvironmentVariables.OPENCODE_MODEL))
                .defaultAgent(System.getenv(EnvironmentVariables.OPENCODE_AGENT))
                .build();
        OpenCode openCode = OpenCode.create(config);

        printHeader(url, prompt, config);
        if (!connect(openCode, url)) {
            System.exit(1);
        }

        String agentName = config.defaultAgent() != null ? config.defaultAgent() : "build";
        AgentSpec spec = AgentSpec.builder(agentName)
                .model(config.defaultModel())
                .instruction(prompt)
                .permission(autoApprove ? PermissionRule.allow("*", "*") : PermissionRule.deny("*", "*"))
                .build();

        long start = System.currentTimeMillis();
        try (AgentSession session = openCode.runtime().open(spec)) {
            AgentRun run = session.send(prompt, event -> {
                if (event instanceof TextDeltaEvent delta) {
                    System.out.print(delta.delta());
                    System.out.flush();
                } else if (event instanceof ToolCallCompletedEvent tc) {
                    System.out.printf("%n[tool %s] %s%n", tc.toolName(), tc.output());
                } else if (event instanceof StepEndedEvent se) {
                    System.out.printf("%n[step] %s in=%d out=%d cost=$%.4f%n",
                            se.finishReason(), se.tokens().input(), se.tokens().output(), se.cost());
                } else if (event instanceof PermissionRequestedEvent pe) {
                    if (autoApprove) {
                        pe.allowOnce();
                        System.out.printf("%n[permission] %s -> allowed%n", pe.action());
                    } else {
                        pe.reject("Rejected by policy");
                        System.out.printf("%n[permission] %s -> rejected%n", pe.action());
                    }
                }
            });

            AgentResult result = run.await();
            printSummary(result, System.currentTimeMillis() - start);
            System.exit(result.isSuccess() ? 0 : 2);
        } catch (Exception e) {
            System.err.println("[error] " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean connect(OpenCode openCode, String url) {
        try {
            ServerInfo info = openCode.client().server().info();
            System.out.println("[server] OpenCode " + info.version() + " at " + url);
            return true;
        } catch (OpenCodeException failure) {
            System.err.println("[error] " + describe(failure, url));
            return false;
        }
    }

    private static String describe(OpenCodeException failure, String url) {
        if (failure.statusCode() == 401) {
            return "The server requires authentication. Set OPENCODE_SERVER_PASSWORD (and "
                    + "OPENCODE_SERVER_USERNAME if the server overrides the default \"opencode\").";
        }
        if (failure.statusCode() == -1) {
            return "Cannot reach " + url + ". Start a server with: opencode serve --port 4096";
        }
        return failure.getMessage();
    }

    private static void printHeader(String url, String prompt, OpenCodeConfig config) {
        System.out.println("=== OpenCode4J agent runner ===");
        System.out.println("server: " + url + (config.hasServePassword() ? " (authenticated)" : ""));
        System.out.println("task:   " + prompt);
        System.out.println("agent:  " + orDefault(config.defaultAgent())
                + "   model: " + orDefault(config.defaultModel()));
    }

    private static void printSummary(AgentResult result, long millis) {
        System.out.println("\n=== summary ===");
        System.out.println("session:    " + result.sessionId());
        System.out.println("success:    " + result.isSuccess());
        System.out.printf("duration:   %.1fs%n", millis / 1000.0);
        System.out.println("tool calls: " + result.toolExecutions().size());
        if (result.errorMessage() != null) {
            System.out.println("error:      " + result.errorMessage());
        }
        System.out.printf("tokens:     in=%d out=%d reasoning=%d cache_read=%d%n",
                result.tokenUsage().input(), result.tokenUsage().output(),
                result.tokenUsage().reasoning(), result.tokenUsage().cacheRead());
        System.out.printf("cost:       $%.4f%n", result.totalCost());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String orDefault(String value) {
        return value == null ? "(server default)" : value;
    }
}
