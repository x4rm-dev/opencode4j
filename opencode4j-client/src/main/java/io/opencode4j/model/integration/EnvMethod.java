package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A method resolved from environment variables on the server. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvMethod(
        List<String> names
) implements IntegrationMethod {

    public EnvMethod {
        names = names == null ? List.of() : List.copyOf(names);
    }
}
