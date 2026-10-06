package io.opencode4j.runtime.spec;

/**
 * How a skill is activated for an agent.
 */
public enum SkillActivation {
    /** Injected deterministically on every prompt turn via prompt attachments. */
    ALWAYS,
    /** Registered in workspace and available for the agent to load dynamically via the skill tool. */
    ON_DEMAND,
    /** Registered but blocked by default permissions for this agent. */
    OFF
}
