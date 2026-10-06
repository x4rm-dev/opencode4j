package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An integration (a provider or service OpenCode can connect to).
 *
 * <p>{@code metadata} is a free-form object in the OpenAPI schema, hence {@code Map<String, Object>}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Integration(
        String id,
        String name,
        Map<String, Object> metadata,
        List<IntegrationMethod> methods,
        List<Connection> connections
) {

    public Integration {
        // Map.copyOf would reject null values, which a free-form JSON object may legitimately contain.
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        methods = methods == null ? List.of() : List.copyOf(methods);
        connections = connections == null ? List.of() : List.copyOf(connections);
    }
}
