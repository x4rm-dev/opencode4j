package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A method that authenticates by running a local command. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandMethod(
        String id,
        String label,
        List<String> command
) implements IntegrationMethod {

    public CommandMethod {
        command = command == null ? List.of() : List.copyOf(command);
    }
}
