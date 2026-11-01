package io.opencode4j.runtime;

/** A workspace failed validation; {@link #validation()} lists the problems. */
public class WorkspaceValidationException extends RuntimeException {

    private final transient WorkspaceValidation validation;

    public WorkspaceValidationException(WorkspaceValidation validation) {
        super("Workspace " + validation.workspaceId() + " is not valid: " + String.join("; ", validation.problems()));
        this.validation = validation;
    }

    public WorkspaceValidation validation() {
        return validation;
    }
}
