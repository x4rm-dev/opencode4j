package io.opencode4j.agent.runtime;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.model.AgentSummary;
import io.opencode4j.model.PermissionEffect;
import io.opencode4j.model.provider.ModelInfo;
import io.opencode4j.model.provider.ProviderInfo;
import io.opencode4j.runtime.WorkspaceValidation;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.PolicyRule;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a workspace's effective state back from the server and compares it with its spec.
 *
 * <p>Permission checks use the rules the server reports for each agent, evaluated the way OpenCode does: the last
 * rule whose action matches wins, and a tool is unavailable only when that rule denies it for every resource.
 * A {@code tool.use} policy that denies the tool for every input also counts.</p>
 */
final class WorkspaceValidator {

    /** Tools a read-only agent must not have unless its spec allows them explicitly. */
    static final Set<String> RESTRICTED_TOOLS =
            Set.of("shell", "execute", "edit", "write", "webfetch", "websearch", "question");

    private final OpenCodeClient client;

    WorkspaceValidator(OpenCodeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    WorkspaceValidation validate(OpenCodeWorkspace workspace, Set<String> restrictedTools) {
        WorkspaceSpec spec = workspace.spec();
        String directory = workspace.directory().toString();
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        workspace.unavailableMcpServers().forEach((name, status) ->
                warnings.add("MCP server " + name + " did not connect (" + status + ")"));
        try {
            checkProviders(spec, directory, problems);
            checkModels(spec, directory, problems);
            checkPermissions(spec, directory, restrictedTools, problems);
        } catch (OpenCodeException e) {
            problems.add("Could not read the workspace state from the server: " + e.getMessage());
        }
        return new WorkspaceValidation(spec.id(), problems, warnings);
    }

    private void checkProviders(WorkspaceSpec spec, String directory, List<String> problems) {
        if (spec.providers().isEmpty()) {
            return;
        }
        Map<String, ProviderInfo> registered = new LinkedHashMap<>();
        client.providers().providers(directory).forEach(provider -> registered.put(provider.id(), provider));
        for (ProviderSpec provider : spec.providers()) {
            ProviderInfo info = registered.get(provider.id());
            if (info == null) {
                problems.add("Provider " + provider.id() + " is not registered");
            } else if ("disabled".equals(info.activation())) {
                problems.add("Provider " + provider.id() + " is disabled");
            }
        }
    }

    private void checkModels(WorkspaceSpec spec, String directory, List<String> problems) {
        Map<String, Boolean> required = new LinkedHashMap<>();
        if (spec.model() != null) {
            required.put(spec.model(), false);
        }
        if (spec.smallModel() != null) {
            required.putIfAbsent(spec.smallModel(), false);
        }
        for (AgentSpec agent : spec.rootAgent().allAgents()) {
            if (agent.model() != null && !agent.model().isBlank()) {
                required.put(agent.model(), true);
            }
        }
        if (required.isEmpty()) {
            return;
        }
        Map<String, ModelInfo> visible = new LinkedHashMap<>();
        client.providers().models(directory).forEach(model -> visible.put(model.providerId() + "/" + model.modelId(), model));
        required.forEach((reference, needsTools) -> {
            ModelInfo model = visible.get(reference);
            if (model == null) {
                problems.add("Model " + reference + " is not available");
            } else if (!model.enabled()) {
                problems.add("Model " + reference + " is disabled");
            } else if (needsTools && !model.supportsTools()) {
                problems.add("Model " + reference + " is used by an agent but does not support tool calls");
            }
        });
    }

    private void checkPermissions(WorkspaceSpec spec, String directory, Set<String> restrictedTools,
                                  List<String> problems) {
        for (AgentSpec agent : spec.rootAgent().allAgents()) {
            AgentSummary effective = client.agents().get(agent.name(), directory);
            if (effective == null) {
                problems.add("Agent " + agent.name() + " is not registered");
                continue;
            }
            for (String tool : restrictedTools) {
                if (explicitlyAllowed(spec, agent, tool)) {
                    continue;
                }
                if (!disabled(tool, effective.permissions()) && !deniedByPolicy(tool, spec.policies())) {
                    problems.add("Agent " + agent.name() + " can use " + tool);
                }
            }
        }
    }

    private static boolean explicitlyAllowed(WorkspaceSpec spec, AgentSpec agent, String tool) {
        List<PermissionRule> rules = new ArrayList<>(spec.permissions());
        rules.addAll(agent.permissions());
        return rules.stream().anyMatch(rule -> rule.effect() != io.opencode4j.runtime.spec.PermissionEffect.DENY
                && !"*".equals(rule.action()) && matches(tool, rule.action()));
    }

    /** OpenCode's rule: the last rule matching the action decides, and only a deny of every resource disables. */
    static boolean disabled(String tool, List<io.opencode4j.model.PermissionRule> rules) {
        io.opencode4j.model.PermissionRule decisive = null;
        for (io.opencode4j.model.PermissionRule rule : rules) {
            if (rule.action() != null && matches(tool, rule.action())) {
                decisive = rule;
            }
        }
        return decisive != null && "*".equals(decisive.resource()) && decisive.effect() == PermissionEffect.DENY;
    }

    private static boolean deniedByPolicy(String tool, List<PolicyRule> policies) {
        PolicyRule decisive = null;
        for (PolicyRule policy : policies) {
            if ("tool.use".equals(policy.action()) && (policy.resource().equals("*")
                    || policy.resource().equals(tool + ":*"))) {
                decisive = policy;
            }
        }
        return decisive != null && !decisive.allow();
    }

    /** OpenCode's wildcard: {@code *} any run, {@code ?} one character, and a trailing {@code " *"} optional. */
    static boolean matches(String input, String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.replace('\\', '/').toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        String compiled = regex.toString();
        String optionalTail = Pattern.quote(" ") + ".*";
        if (compiled.endsWith(optionalTail)) {
            compiled = compiled.substring(0, compiled.length() - optionalTail.length()) + "( .*)?";
        }
        return Pattern.compile(compiled, Pattern.DOTALL).matcher(input.replace('\\', '/')).matches();
    }
}
