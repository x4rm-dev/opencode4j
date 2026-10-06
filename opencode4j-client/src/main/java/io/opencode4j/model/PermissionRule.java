package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** One permission rule: what to do ({@code effect}) when {@code action} is attempted on {@code resource}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionRule(
        String action,
        String resource,
        PermissionEffect effect
) {
}
