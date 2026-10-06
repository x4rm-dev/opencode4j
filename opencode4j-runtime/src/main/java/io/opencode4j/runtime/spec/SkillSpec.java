package io.opencode4j.runtime.spec;

import java.util.Objects;

/**
 * Specification for a skill available to an agent.
 *
 * <p>Skills are written to {@code .opencode/skills/<name>/SKILL.md} in the workspace.</p>
 *
 * @param name        unique skill name identifier
 * @param description brief description explaining when the agent should use this skill
 * @param content     markdown content and instructions of the skill
 * @param activation  how the skill should be activated ({@link SkillActivation#ALWAYS}, {@link SkillActivation#ON_DEMAND}, {@link SkillActivation#OFF})
 */
public record SkillSpec(
        String name,
        String description,
        String content,
        SkillActivation activation
) {

    public SkillSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(activation, "activation");
    }

    public static SkillSpec onDemand(String name, String description, String content) {
        return new SkillSpec(name, description, content, SkillActivation.ON_DEMAND);
    }

    public static SkillSpec always(String name, String description, String content) {
        return new SkillSpec(name, description, content, SkillActivation.ALWAYS);
    }

    public static SkillSpec off(String name, String description, String content) {
        return new SkillSpec(name, description, content, SkillActivation.OFF);
    }
}
