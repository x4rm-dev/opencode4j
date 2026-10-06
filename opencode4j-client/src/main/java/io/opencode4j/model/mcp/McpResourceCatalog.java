package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.Immutable;
import java.util.List;

/** The resources and resource templates offered by all connected MCP servers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpResourceCatalog(
        List<McpResource> resources,
        List<McpResourceTemplate> templates
) {

    public McpResourceCatalog {
        resources = Immutable.list(resources);
        templates = Immutable.list(templates);
    }
}
