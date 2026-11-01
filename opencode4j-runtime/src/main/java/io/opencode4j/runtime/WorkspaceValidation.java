package io.opencode4j.runtime;

import java.util.List;
import java.util.Objects;

/**
 * The result of checking a materialized workspace against the server: whether its providers and models are
 * usable and its agents are locked down.
 *
 * @param problems conditions that make the workspace unsafe or unusable; it should not take traffic
 * @param warnings degraded but usable conditions, such as an MCP server that did not connect
 */
public record WorkspaceValidation(String workspaceId, List<String> problems, List<String> warnings) {

    public WorkspaceValidation {
        Objects.requireNonNull(workspaceId, "workspaceId");
        problems = problems == null ? List.of() : List.copyOf(problems);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean valid() {
        return problems.isEmpty();
    }

    /** Throws {@link WorkspaceValidationException} listing every problem, if there is any. */
    public WorkspaceValidation requireValid() {
        if (!valid()) {
            throw new WorkspaceValidationException(this);
        }
        return this;
    }
}
