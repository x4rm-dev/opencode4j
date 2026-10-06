package io.opencode4j.agent.runtime.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.SkillSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunPromptFactoryTest {
    @Test
    void combinesPromptAndAlwaysSkillsWithoutDuplicatingExplicitSkills() {
        var prompt = new RunPrompt("hello", List.of("chosen", "shared"), "worker",
                Map.of("runId", "caller-value", "tenant", "acme"));
        var spec = AgentSpec.builder("boss")
                .skill(SkillSpec.always("shared", "always", ""))
                .skill(SkillSpec.always("automatic", "always", ""))
                .skill(SkillSpec.onDemand("available", "on demand", ""))
                .skill(SkillSpec.off("disabled", "off", ""))
                .build();

        var request = RunPromptFactory.create("actual-run", prompt, spec);

        assertEquals("hello", request.text());
        assertNotNull(request.id());
        assertFalse(request.id().contains("-"));
        assertEquals(List.of("chosen", "shared", "automatic"),
                request.skills().stream().map(skill -> skill.id()).toList());
        assertEquals(List.of("worker"), request.agents().stream().map(agent -> agent.name()).toList());
        assertEquals(Map.of("runId", "actual-run", "tenant", "acme"), request.metadata());
    }

    @Test
    void omitsEmptySkillAndBlankDelegationAttachments() {
        var request = RunPromptFactory.create("run", new RunPrompt("hello", List.of(), "  ", Map.of()),
                AgentSpec.builder("boss").build());

        assertNull(request.skills());
        assertNull(request.agents());
        assertEquals(Map.of("runId", "run"), request.metadata());
    }
}
