package io.opencode4j.runtime.spec;

import java.util.Objects;

/**
 * A hard server policy (OpenCode {@code policies}). Unlike {@link PermissionRule}s, which an agent may be asked
 * about, a denied policy removes the capability outright.
 *
 * @param action   {@code provider.use}, {@code tool.use}, {@code mcp.use}, {@code skill.use}, {@code plugin.use},
 *                 {@code integration.use} or {@code integration.connect}
 * @param resource what the action applies to; for {@code tool.use} it is {@code <tool>:<input>}, such as
 *                 {@code shell:*}
 * @param allow    {@code true} to allow, {@code false} to deny
 */
public record PolicyRule(String action, String resource, boolean allow) {

    public PolicyRule {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resource, "resource");
    }

    public static PolicyRule allow(String action, String resource) {
        return new PolicyRule(action, resource, true);
    }

    public static PolicyRule deny(String action, String resource) {
        return new PolicyRule(action, resource, false);
    }

    /** Denies every use of {@code tool}, for example {@code shell}. */
    public static PolicyRule denyTool(String tool) {
        return deny("tool.use", tool + ":*");
    }

    /** The wire value of {@code effect}. */
    public String effect() {
        return allow ? "allow" : "deny";
    }
}
