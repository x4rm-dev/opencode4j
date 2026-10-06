package io.opencode4j.runtime.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates parameters of a {@link Tool} method to describe parameter schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {

    /**
     * Parameter name in JSON Schema.
     */
    String name();

    /**
     * Parameter description for the agent.
     */
    String description() default "";

    /**
     * Whether the parameter is required. Defaults to true.
     */
    boolean required() default true;
}
