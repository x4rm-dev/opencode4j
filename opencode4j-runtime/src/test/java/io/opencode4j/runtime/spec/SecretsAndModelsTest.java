package io.opencode4j.runtime.spec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretsAndModelsTest {

    private static final AgentSpec ROOT = AgentSpec.builder("sre").build();

    @Test
    void secretReferencesRenderAsOpenCodeSubstitutions() {
        assertEquals("{env:GODRIC_GATEWAY_TOKEN}", SecretRef.env("GODRIC_GATEWAY_TOKEN").render());
        assertEquals("{file:/run/secrets/vm}", SecretRef.file("/run/secrets/vm").render());
        assertFalse(SecretRef.env("X").inline());
        assertTrue(SecretRef.literal("s3cr3t").inline());
        assertFalse(SecretRef.literal("s3cr3t").toString().contains("s3cr3t"));
        assertThrows(IllegalArgumentException.class, () -> SecretRef.env("not-valid"));
    }

    @Test
    void recognisesInlineValues() {
        assertFalse(SecretRef.isInline("{env:TOKEN}"));
        assertFalse(SecretRef.isInline("Bearer {env:TOKEN}"));
        assertFalse(SecretRef.isInline("{file:/run/secret}"));
        assertTrue(SecretRef.isInline("Bearer abc"));
        assertTrue(SecretRef.isInline("{env:A}{env:B}x"));
    }

    @Test
    void workspacesRefuseLiteralCredentialsUnlessAllowed() {
        ProviderSpec literal = ProviderSpec.openAiCompatible("godric", "http://127.0.0.1:9/v1",
                () -> Map.of("Authorization", "Bearer real-token"), List.of());
        ProviderSpec referenced = ProviderSpec.builder("godric").baseUrl("http://127.0.0.1:{env:PORT}/v1")
                .apiKey(SecretRef.env("GODRIC_GATEWAY_TOKEN"))
                .header("X-Tenant", SecretRef.env("TENANT"))
                .model(ProviderSpec.ModelSpec.toolCalling("global:LLM_X", 200_000, 32_000))
                .build();
        McpToolSpec literalMcp = McpToolSpec.remote("vm", "metrics", "https://vm/mcp", Map.of("Authorization", "abc"));

        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("w", ROOT).provider(literal).build());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("w", ROOT).mcpServer(literalMcp).build());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("w", AgentSpec.builder("sre")
                .tool(literalMcp).build()).build());
        assertDoesNotThrow(() -> WorkspaceSpec.builder("w", ROOT).provider(referenced).build());
        assertDoesNotThrow(() -> WorkspaceSpec.builder("w", ROOT).provider(literal).mcpServer(literalMcp)
                .allowInlineSecrets(true).build());
    }

    @Test
    void modelsNeedRealLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderSpec.ModelSpec("m", null, true, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ProviderSpec.ModelSpec.toolCalling("m", 1_000, 2_000));
        assertThrows(IllegalArgumentException.class, () -> new ProviderSpec.ModelCost(-1, 0));
        ProviderSpec.ModelSpec priced = ProviderSpec.ModelSpec.toolCalling("m", 8_000, 1_000).withCost(1, 2);
        assertEquals(new ProviderSpec.ModelCost(1, 2), priced.cost());
    }

    @Test
    void modelReferencesKeepColonsAndSlashesInTheModelPart() {
        assertEquals("godric/global:LLM_GLOBAL_X", AgentSpec.builder("a").model("godric", "global:LLM_GLOBAL_X")
                .build().model());
        assertEquals("p/a/b:c", AgentSpec.builder("a").model("p/a/b:c").build().model());
        assertThrows(IllegalArgumentException.class, () -> AgentSpec.builder("a").model("no-provider"));
        assertThrows(IllegalArgumentException.class, () -> AgentSpec.builder("a").model("/x"));
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("w", ROOT).smallModel("x/").build());
        assertThrows(IllegalArgumentException.class, () -> ProviderSpec.builder("a/b").baseUrl("u").build());
    }
}
