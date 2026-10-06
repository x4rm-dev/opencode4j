package io.opencode4j.runtime.spec;

import java.util.Objects;

/**
 * Exposes methods on a Java instance (or static methods on a Class) as tools over an in-JVM MCP server.
 *
 * @param name        logical namespace/name for this group of Java tools
 * @param description description of what these Java tools provide
 * @param instance    the object holding {@code @Tool} annotated methods, or a {@link Class} for static methods
 */
public record JavaMethodToolSpec(
        String name,
        String description,
        Object instance
) implements ToolSpec {

    public JavaMethodToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(instance, "instance");
    }

    public static JavaMethodToolSpec of(String name, String description, Object instance) {
        return new JavaMethodToolSpec(name, description, instance);
    }

    public static JavaMethodToolSpec of(Object instance) {
        Objects.requireNonNull(instance, "instance");
        Class<?> clazz = instance instanceof Class<?> c ? c : instance.getClass();
        String simpleName = clazz.getSimpleName();
        return new JavaMethodToolSpec(simpleName, "Java tools from " + simpleName, instance);
    }
}
