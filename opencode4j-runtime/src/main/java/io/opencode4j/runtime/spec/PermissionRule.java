package io.opencode4j.runtime.spec;

import java.util.Objects;

/**
 * An individual permission rule governing agent capabilities (e.g. bash, edit, skill, mcp tool).
 *
 * <p>OpenCode evaluates permission rules using last-match-wins semantics.</p>
 *
 * @param action   action pattern, e.g. {@code "*"}, {@code "bash"}, {@code "skill"}, {@code "vault_*"}
 * @param resource resource pattern, e.g. {@code "*"}, {@code "launch-protocol"}, {@code "echo *"}
 * @param effect   effect of the rule: {@link PermissionEffect#ALLOW}, {@link PermissionEffect#DENY}, or {@link PermissionEffect#ASK}
 */
public record PermissionRule(
        String action,
        String resource,
        PermissionEffect effect
) {

    public PermissionRule {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(effect, "effect");
    }

    public static PermissionRule allow(String action, String resource) {
        return new PermissionRule(action, resource, PermissionEffect.ALLOW);
    }

    public static PermissionRule deny(String action, String resource) {
        return new PermissionRule(action, resource, PermissionEffect.DENY);
    }

    public static PermissionRule ask(String action, String resource) {
        return new PermissionRule(action, resource, PermissionEffect.ASK);
    }

    public static PermissionRule denyAll() {
        return new PermissionRule("*", "*", PermissionEffect.DENY);
    }
}
