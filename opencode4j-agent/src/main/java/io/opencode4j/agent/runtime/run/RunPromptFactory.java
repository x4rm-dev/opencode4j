package io.opencode4j.agent.runtime.run;

import io.opencode4j.model.PromptAgentAttachment;
import io.opencode4j.model.PromptRequest;
import io.opencode4j.model.PromptSkillAttachment;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.SkillActivation;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.val;

/** Builds the OpenCode prompt request for one run. */
final class RunPromptFactory {
    private RunPromptFactory() {
    }

    static PromptRequest create(String runId, RunPrompt prompt, AgentSpec spec) {
        val builder = PromptRequest.builder(prompt.text())
                .id("msg_" + UUID.randomUUID().toString().replace("-", ""));
        val skills = new ArrayList<PromptSkillAttachment>();
        for (val name : prompt.useSkills()) {
            skills.add(PromptSkillAttachment.of(name));
        }
        for (val skill : spec.skills()) {
            if (skill.activation() == SkillActivation.ALWAYS && !prompt.useSkills().contains(skill.name())) {
                skills.add(PromptSkillAttachment.of(skill.name()));
            }
        }
        if (!skills.isEmpty()) {
            builder.skills(skills);
        }
        if (prompt.delegateTo() != null && !prompt.delegateTo().isBlank()) {
            builder.agents(List.of(PromptAgentAttachment.of(prompt.delegateTo())));
        }
        if (!prompt.attachments().isEmpty()) {
            val files = new ArrayList<io.opencode4j.model.FileAttachment>();
            for (val media : prompt.attachments()) {
                files.add(new io.opencode4j.model.FileAttachment(media.dataUri(), media.name(), media.description()));
            }
            builder.files(files);
        }
        val metadata = new LinkedHashMap<String, Object>(prompt.metadata());
        metadata.put("runId", runId);
        return builder.metadata(metadata).build();
    }
}
