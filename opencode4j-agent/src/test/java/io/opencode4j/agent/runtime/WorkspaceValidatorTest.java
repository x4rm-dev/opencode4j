package io.opencode4j.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.model.PermissionEffect;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.WorkspaceValidation;
import io.opencode4j.runtime.WorkspaceValidationException;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.PolicyRule;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.SecretRef;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceValidatorTest {

    private MockWebServer server;
    private OpenCodeClient client;
    private volatile String sreRules;
    private volatile String models;

    @TempDir
    Path workspaces;

    @BeforeEach
    void setUp() throws IOException {
        sreRules = "[{\"action\":\"*\",\"resource\":\"*\",\"effect\":\"deny\"},"
                + "{\"action\":\"alfred_*\",\"resource\":\"*\",\"effect\":\"allow\"}]";
        models = "[{\"id\":\"x\",\"modelID\":\"global:LLM_X\",\"providerID\":\"godric\",\"name\":\"x\",\"enabled\":true,"
                + "\"status\":\"active\",\"capabilities\":{\"tools\":true,\"input\":[\"text\"],\"output\":[\"text\"]},"
                + "\"limit\":{\"context\":200000,\"output\":32000}}]";
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/api/agent/sre")) {
                    return json("{\"data\":{\"id\":\"sre\",\"name\":\"sre\",\"mode\":\"primary\",\"permissions\":"
                            + sreRules + "}}");
                }
                if (path.startsWith("/api/agent")) {
                    return json("{\"data\":[{\"id\":\"sre\"}]}");
                }
                if (path.startsWith("/api/provider")) {
                    return json("{\"data\":[{\"id\":\"godric\",\"name\":\"Godric\",\"activation\":\"enabled\"}]}");
                }
                if (path.startsWith("/api/model")) {
                    return json("{\"data\":" + models + "}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        client = OpenCodeClient.create(OpenCodeConfig.builder(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    private WorkspaceValidation validate(WorkspaceSpec spec) {
        try (OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client).baseWorkspacesDir(workspaces)
                .warmupTimeout(Duration.ofSeconds(3)).build()) {
            Workspace workspace = runtime.materialize(spec);
            return runtime.validate(workspace);
        }
    }

    private static WorkspaceSpec.Builder release() {
        ProviderSpec godric = ProviderSpec.builder("godric").baseUrl("http://127.0.0.1:{env:GW_PORT}/v1")
                .apiKey(SecretRef.env("GW_TOKEN"))
                .model(ProviderSpec.ModelSpec.toolCalling("global:LLM_X", 200_000, 32_000))
                .build();
        return WorkspaceSpec.builder("rel", AgentSpec.builder("sre").model("godric/global:LLM_X")
                        .permission(PermissionRule.allow("alfred_*", "*")).build())
                .provider(godric);
    }

    @Test
    void aLockedDownReleaseIsValid() {
        WorkspaceValidation validation = validate(release().build());

        assertTrue(validation.valid(), validation.problems().toString());
        validation.requireValid();
    }

    @Test
    void reportsAgentsThatCanUseRestrictedTools() {
        // Given: the server reports the agent with a later shell allow overriding the deny-all
        sreRules = "[{\"action\":\"*\",\"resource\":\"*\",\"effect\":\"deny\"},"
                + "{\"action\":\"shell\",\"resource\":\"*\",\"effect\":\"allow\"},"
                + "{\"action\":\"edit\",\"resource\":\"src/*\",\"effect\":\"deny\"}]";

        // When
        WorkspaceValidation validation = validate(release().build());

        // Then
        assertFalse(validation.valid());
        assertTrue(validation.problems().contains("Agent sre can use shell"), validation.problems().toString());
        assertTrue(validation.problems().contains("Agent sre can use edit"),
                "a deny of some resources does not remove the tool");
        WorkspaceValidationException failure = assertThrows(WorkspaceValidationException.class,
                validation::requireValid);
        assertTrue(failure.getMessage().contains("can use shell"));
    }

    @Test
    void aShellPolicyOrAnExplicitAllowSatisfiesTheCheck() {
        sreRules = "[{\"action\":\"*\",\"resource\":\"*\",\"effect\":\"allow\"}]";

        WorkspaceValidation withPolicies = validate(release()
                .policy(PolicyRule.denyTool("shell")).policy(PolicyRule.denyTool("execute"))
                .policy(PolicyRule.denyTool("edit")).policy(PolicyRule.denyTool("write"))
                .policy(PolicyRule.denyTool("webfetch")).policy(PolicyRule.denyTool("websearch"))
                .policy(PolicyRule.denyTool("question")).build());

        assertTrue(withPolicies.valid(), withPolicies.problems().toString());
    }

    @Test
    void reportsMissingModelsAndModelsWithoutTools() {
        models = "[{\"id\":\"x\",\"modelID\":\"global:LLM_X\",\"providerID\":\"godric\",\"name\":\"x\",\"enabled\":true,"
                + "\"status\":\"active\",\"capabilities\":{\"tools\":false,\"input\":[\"text\"],\"output\":[\"text\"]},"
                + "\"limit\":{\"context\":200000,\"output\":32000}}]";

        WorkspaceValidation validation = validate(release().smallModel("godric/global:LLM_SMALL").build());

        assertTrue(validation.problems().contains("Model godric/global:LLM_SMALL is not available"),
                validation.problems().toString());
        assertTrue(validation.problems().contains(
                "Model godric/global:LLM_X is used by an agent but does not support tool calls"));
    }

    @Test
    void matchesWildcardsTheWayOpenCodeDoes() {
        assertTrue(WorkspaceValidator.matches("alfred_catalog_lookup", "alfred_*"));
        assertTrue(WorkspaceValidator.matches("shell", "*"));
        assertTrue(WorkspaceValidator.matches("edit", "ed?t"));
        assertTrue(WorkspaceValidator.matches("git", "git *"), "a trailing ' *' is optional");
        assertFalse(WorkspaceValidator.matches("shell", "shel"));
        assertFalse(WorkspaceValidator.matches("a.b", "a?c"));
        assertTrue(WorkspaceValidator.disabled("shell", List.of(
                new io.opencode4j.model.PermissionRule("*", "*", PermissionEffect.DENY))));
        assertFalse(WorkspaceValidator.disabled("shell", List.of()), "OpenCode allows by default");
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
