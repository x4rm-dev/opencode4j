package io.opencode4j.runtime.spec;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A secret written into workspace configuration as a reference that OpenCode resolves when it loads the
 * configuration, so the value itself never reaches the disk.
 *
 * <p>{@link #env(String)} renders {@code {env:NAME}} (an environment variable of the OpenCode process) and
 * {@link #file(String)} renders {@code {file:path}}. {@link #literal(String)} embeds the value and is refused by
 * workspaces unless {@code allowInlineSecrets} is set; use it only for local development.</p>
 *
 * <p>OpenCode returns resolved values from {@code /api/provider}, {@code /api/model} and {@code /api/config}, so
 * the OpenCode server password protects every secret a workspace references.</p>
 */
public sealed interface SecretRef permits SecretRef.Env, SecretRef.File, SecretRef.Literal {

    /** Matches a value that is entirely one OpenCode substitution, such as {@code {env:TOKEN}}. */
    Pattern REFERENCE = Pattern.compile("\\{(env|file):[^{}]+}");

    /** The value as it is written into {@code opencode.json}. */
    String render();

    /** Whether the rendered value holds the secret itself rather than a reference to it. */
    default boolean inline() {
        return false;
    }

    /** An environment variable of the OpenCode process. */
    static SecretRef env(String variable) {
        return new Env(variable);
    }

    /** A file readable by the OpenCode process, absolute or relative to the workspace. */
    static SecretRef file(String path) {
        return new File(path);
    }

    /** The secret value itself. Development only. */
    static SecretRef literal(String value) {
        return new Literal(value);
    }

    /** Whether {@code value} contains a literal secret, that is anything but one substitution reference. */
    static boolean isInline(String value) {
        return value != null && !value.isEmpty() && !REFERENCE.matcher(value.replaceFirst("^Bearer ", "")).matches();
    }

    /** {@code {env:NAME}}. */
    record Env(String variable) implements SecretRef {
        public Env {
            Objects.requireNonNull(variable, "variable");
            if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid environment variable name: " + variable);
            }
        }

        @Override
        public String render() {
            return "{env:" + variable + "}";
        }
    }

    /** {@code {file:path}}. */
    record File(String path) implements SecretRef {
        public File {
            Objects.requireNonNull(path, "path");
            if (path.isBlank() || path.contains("}")) {
                throw new IllegalArgumentException("Invalid secret file path: " + path);
            }
        }

        @Override
        public String render() {
            return "{file:" + path + "}";
        }
    }

    /** An inline value; {@link #toString()} never shows it. */
    record Literal(String value) implements SecretRef {
        public Literal {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String render() {
            return value;
        }

        @Override
        public boolean inline() {
            return true;
        }

        @Override
        public String toString() {
            return "Literal[value=****]";
        }
    }
}
