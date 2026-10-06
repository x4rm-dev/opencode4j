package io.opencode4j.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.ResultSpec;
import io.opencode4j.runtime.spec.RunLimits;
import io.opencode4j.runtime.spec.SkillSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentSpecTest {

    record SampleResult(String status, int code) {}

    static class SampleToolService {
        public String ping() {
            return "pong";
        }
    }

    @Test
    void calculatesHierarchicalTreeDepthAccurately() {
        AgentSpec solo = AgentSpec.builder("solo").instruction("I am solo").build();
        assertEquals(0, solo.calculateDepth());

        AgentSpec worker = AgentSpec.builder("worker").instruction("I work").build();
        AgentSpec boss = AgentSpec.builder("boss")
                .subAgent(worker)
                .build();
        assertEquals(1, boss.calculateDepth());

        AgentSpec subWorker = AgentSpec.builder("subWorker").build();
        AgentSpec lead = AgentSpec.builder("lead")
                .subAgent(subWorker)
                .build();
        AgentSpec executive = AgentSpec.builder("exec")
                .subAgent(lead)
                .build();
        assertEquals(2, executive.calculateDepth());
    }

    @Test
    void flattensAndFindsAgentsInHierarchy() {
        AgentSpec leaf1 = AgentSpec.builder("leaf1").build();
        AgentSpec leaf2 = AgentSpec.builder("leaf2").build();
        AgentSpec mid = AgentSpec.builder("mid").subAgent(leaf1).build();
        AgentSpec root = AgentSpec.builder("root").subAgent(mid).subAgent(leaf2).build();

        List<AgentSpec> all = root.allAgents();
        assertEquals(4, all.size());
        assertEquals(List.of("root", "mid", "leaf1", "leaf2"), all.stream().map(AgentSpec::name).toList());

        assertNotNull(root.findAgent("leaf1"));
        assertNotNull(root.findAgent("mid"));
        assertNull(root.findAgent("unknown"));
    }

    @Test
    void buildsRichSpecificationWithToolsSkillsAndPermissions() {
        AgentSpec spec = AgentSpec.builder("reviewer")
                .description("Code reviewer")
                .instruction("Review PRs thoroughly")
                .model("github-copilot/gpt-6.1-sol")
                .steps(10)
                .permission(PermissionRule.deny("*", "*"))
                .permission(PermissionRule.allow("skill", "launch-protocol"))
                .skill(SkillSpec.always("launch-protocol", "Launch protocol skill", "# Content"))
                .tool(JavaMethodToolSpec.of(new SampleToolService()))
                .tool(McpToolSpec.local("vault", "Vault", List.of("python3", "vault.py")))
                .resultType(SampleResult.class)
                .limits(RunLimits.builder().maxCost(1.5).build())
                .build();

        assertEquals("reviewer", spec.name());
        assertEquals(10, spec.steps());
        assertEquals(2, spec.permissions().size());
        assertEquals(1, spec.skills().size());
        assertEquals(2, spec.tools().size());
        assertNotNull(spec.resultSpec());
        assertEquals(SampleResult.class, spec.resultSpec().targetType());
        assertEquals(1.5, spec.limits().maxCost());
    }

    @Test
    void validatesBlankName() {
        assertThrows(NullPointerException.class, () -> AgentSpec.builder(null).build());
        assertThrows(IllegalArgumentException.class, () -> AgentSpec.builder("  ").build());
    }
}
