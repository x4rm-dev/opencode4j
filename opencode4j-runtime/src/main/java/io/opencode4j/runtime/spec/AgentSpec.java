package io.opencode4j.runtime.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable specification defining an agent, its instructions, tools, skills, permissions, and sub-agents.
 *
 * <p>Agents can be nested hierarchically. A root agent becomes a primary agent; any nested agents
 * automatically become subagents reachable through delegation.</p>
 */
public record AgentSpec(
        String name,
        String description,
        String instruction,
        String model,
        Integer steps,
        List<PermissionRule> permissions,
        List<ToolSpec> tools,
        List<SkillSpec> skills,
        List<AgentSpec> subAgents,
        ResultSpec<?> resultSpec,
        RunLimits limits
) {

    public AgentSpec {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("agent name must not be blank");
        }
        permissions = permissions == null || permissions.isEmpty() ? List.of() : List.copyOf(permissions);
        tools = tools == null || tools.isEmpty() ? List.of() : List.copyOf(tools);
        skills = skills == null || skills.isEmpty() ? List.of() : List.copyOf(skills);
        subAgents = subAgents == null || subAgents.isEmpty() ? List.of() : List.copyOf(subAgents);
        limits = limits == null ? RunLimits.UNLIMITED : limits;
    }

    /**
     * Calculates the sub-agent tree depth (number of child levels below this agent).
     *
     * <p>A root agent with no subagents has depth 0. Root with direct subagents has depth 1.
     * A three-tier hierarchy (boss &gt; lead &gt; worker) has depth 2.</p>
     */
    public int calculateDepth() {
        if (subAgents.isEmpty()) {
            return 0;
        }
        int maxChildDepth = 0;
        for (AgentSpec subAgent : subAgents) {
            maxChildDepth = Math.max(maxChildDepth, subAgent.calculateDepth());
        }
        return 1 + maxChildDepth;
    }

    /**
     * Returns a flat list of this agent and all transitive sub-agents.
     */
    public List<AgentSpec> allAgents() {
        Map<String, AgentSpec> collected = new LinkedHashMap<>();
        collectAll(this, collected);
        return List.copyOf(collected.values());
    }

    private static void collectAll(AgentSpec spec, Map<String, AgentSpec> target) {
        target.put(spec.name(), spec);
        for (AgentSpec child : spec.subAgents()) {
            collectAll(child, target);
        }
    }

    /**
     * Finds an agent by name within this spec tree.
     */
    public AgentSpec findAgent(String targetName) {
        Objects.requireNonNull(targetName, "targetName");
        if (name.equals(targetName)) {
            return this;
        }
        for (AgentSpec sub : subAgents) {
            AgentSpec found = sub.findAgent(targetName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String description;
        private String instruction;
        private String model;
        private Integer steps;
        private final List<PermissionRule> permissions = new ArrayList<>();
        private final List<ToolSpec> tools = new ArrayList<>();
        private final List<SkillSpec> skills = new ArrayList<>();
        private final List<AgentSpec> subAgents = new ArrayList<>();
        private ResultSpec<?> resultSpec;
        private RunLimits limits;

        public Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder steps(Integer steps) {
            this.steps = steps;
            return this;
        }

        public Builder permission(PermissionRule rule) {
            this.permissions.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder permissions(List<PermissionRule> rules) {
            if (rules != null) {
                this.permissions.addAll(rules);
            }
            return this;
        }

        public Builder tool(ToolSpec tool) {
            this.tools.add(Objects.requireNonNull(tool, "tool"));
            return this;
        }

        public Builder tools(ToolSpec... tools) {
            if (tools != null) {
                this.tools.addAll(List.of(tools));
            }
            return this;
        }

        public Builder tools(List<ToolSpec> tools) {
            if (tools != null) {
                this.tools.addAll(tools);
            }
            return this;
        }

        public Builder javaTools(Object toolInstance) {
            this.tools.add(JavaMethodToolSpec.of(toolInstance));
            return this;
        }

        public Builder skill(SkillSpec skill) {
            this.skills.add(Objects.requireNonNull(skill, "skill"));
            return this;
        }

        public Builder skills(List<SkillSpec> skills) {
            if (skills != null) {
                this.skills.addAll(skills);
            }
            return this;
        }

        public Builder subAgent(AgentSpec subAgent) {
            this.subAgents.add(Objects.requireNonNull(subAgent, "subAgent"));
            return this;
        }

        public Builder subAgents(AgentSpec... subAgents) {
            if (subAgents != null) {
                this.subAgents.addAll(List.of(subAgents));
            }
            return this;
        }

        public Builder subAgents(List<AgentSpec> subAgents) {
            if (subAgents != null) {
                this.subAgents.addAll(subAgents);
            }
            return this;
        }

        public <T> Builder resultType(Class<T> type) {
            this.resultSpec = ResultSpec.of(type);
            return this;
        }

        public <T> Builder resultSpec(ResultSpec<T> resultSpec) {
            this.resultSpec = resultSpec;
            return this;
        }

        public Builder limits(RunLimits limits) {
            this.limits = limits;
            return this;
        }

        public AgentSpec build() {
            return new AgentSpec(
                    name,
                    description,
                    instruction,
                    model,
                    steps,
                    permissions,
                    tools,
                    skills,
                    subAgents,
                    resultSpec,
                    limits
            );
        }
    }
}
