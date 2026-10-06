package io.opencode4j.runtime.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a class as an agent-callable tool.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /**
     * Explanation of what the tool does, presented to the agent.
     */
    String description();

    /**
     * Override tool name. If empty, uses the method name.
     */
    String name() default "";
}
