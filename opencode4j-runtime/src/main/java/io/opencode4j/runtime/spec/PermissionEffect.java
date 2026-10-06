package io.opencode4j.runtime.spec;

/**
 * Effect of an agent permission rule.
 */
public enum PermissionEffect {
    ALLOW,
    DENY,
    ASK;

    public String wireValue() {
        return name().toLowerCase();
    }
}
