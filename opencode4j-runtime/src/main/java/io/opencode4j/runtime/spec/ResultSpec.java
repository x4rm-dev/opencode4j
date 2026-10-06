package io.opencode4j.runtime.spec;

import java.util.Objects;

/**
 * Specification for typed structured results requested from an agent via an auto-registered result tool.
 *
 * @param <T>         the target Java type to bind the submitted structured result to
 * @param targetType  class of the target Java result
 * @param description prompt explanation guiding the model on what output is expected
 * @param maxRetries  maximum repair attempts if schema or business validation fails
 */
public record ResultSpec<T>(
        Class<T> targetType,
        String description,
        int maxRetries
) {

    public ResultSpec {
        Objects.requireNonNull(targetType, "targetType");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0: " + maxRetries);
        }
    }

    public static <T> ResultSpec<T> of(Class<T> targetType) {
        return new ResultSpec<>(targetType, "Structured result submission", 3);
    }

    public static <T> ResultSpec<T> of(Class<T> targetType, String description) {
        return new ResultSpec<>(targetType, description, 3);
    }

    public static <T> ResultSpec<T> of(Class<T> targetType, String description, int maxRetries) {
        return new ResultSpec<>(targetType, description, maxRetries);
    }
}
