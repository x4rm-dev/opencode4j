package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.model.ModelRef;
import io.opencode4j.model.provider.ModelInfo;
import io.opencode4j.model.provider.ProviderInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderApiTest extends MockServerTest {

    @Test
    void readsProvidersWithoutTheirSettings() throws Exception {
        // Given: the server echoes provider settings, including the API key
        server.enqueue(fixture("providers"));

        // When
        List<ProviderInfo> providers = client.providers().providers("/work");

        // Then
        assertEquals("/api/provider?location%5Bdirectory%5D=%2Fwork", takeRequest().getPath());
        ProviderInfo provider = providers.getFirst();
        assertEquals("fake", provider.id());
        assertEquals("enabled", provider.activation());
        assertFalse(provider.toString().contains("apiKey"), "settings must not be modelled");
    }

    @Test
    void readsModelCapabilitiesAndLimits() {
        // Given
        server.enqueue(fixture("models"));

        // When
        ModelInfo model = client.providers().models("/work").getFirst();

        // Then
        assertEquals(new ModelRef("fake-1", "fake", null), model.ref());
        assertTrue(model.supportsTools());
        assertTrue(model.enabled());
        assertEquals(100_000, model.limit().context());
        assertEquals(4_096, model.limit().output());
        assertEquals(List.of("text", "image"), model.capabilities().input());
    }

    @Test
    void filtersModelsByProvider() {
        // Given
        server.enqueue(json("{\"data\":[{\"id\":\"a\",\"modelID\":\"a\",\"providerID\":\"godric\",\"name\":\"a\","
                + "\"capabilities\":{\"tools\":false,\"input\":[\"text\"],\"output\":[\"text\"]},"
                + "\"limit\":{\"context\":1,\"output\":1},\"status\":\"active\",\"enabled\":true},"
                + "{\"id\":\"b\",\"modelID\":\"global:LLM_B\",\"providerID\":\"other\",\"name\":\"b\","
                + "\"limit\":{\"context\":1,\"output\":1},\"status\":\"active\",\"enabled\":true}]}"));

        // When
        List<ModelInfo> models = client.providers().models("godric", null);

        // Then
        assertEquals(List.of("a"), models.stream().map(ModelInfo::id).toList());
        assertFalse(models.getFirst().supportsTools());
    }
}
