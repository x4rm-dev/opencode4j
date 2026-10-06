package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.config.ConfigDirectory;
import io.opencode4j.model.config.ConfigDocument;
import io.opencode4j.model.config.ConfigEntry;
import io.opencode4j.model.config.ShellOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigApiTest extends MockServerTest {

    @Test
    void readsEntriesFromABareArrayResponse() throws Exception {
        // Given: the real server returns this route without a {data} envelope
        server.enqueue(json("[{\"type\":\"document\",\"path\":\"/c/opencode.json\","
                + "\"info\":{\"default_agent\":\"build\"}},{\"type\":\"directory\",\"path\":\"/c\"}]"));

        // When
        List<ConfigEntry> entries = client.config().entries("/work");

        // Then
        ConfigDocument document = assertInstanceOf(ConfigDocument.class, entries.get(0));
        assertEquals("build", document.info().defaultAgent());
        assertEquals("/c", assertInstanceOf(ConfigDirectory.class, entries.get(1)).path());
        assertRequest(HttpMethod.GET, "/api/config?location%5Bdirectory%5D=%2Fwork", "");
    }

    @Test
    void readsEntriesFromAnEnvelopedResponseToo() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[{\"type\":\"directory\",\"path\":\"/c\"}]}"));

        // When
        List<ConfigEntry> entries = client.config().entries(null);

        // Then
        assertEquals(1, entries.size());
        assertRequest(HttpMethod.GET, "/api/config", "");
    }

    @Test
    void listsShells() throws Exception {
        // Given
        server.enqueue(json("[{\"path\":\"/bin/zsh\",\"name\":\"zsh\",\"acceptable\":true}]"));

        // When
        List<ShellOption> shells = client.config().shells();

        // Then
        assertEquals("zsh", shells.getFirst().name());
        assertRequest(HttpMethod.GET, "/api/config/shell", "");
    }

    @Test
    void setsAndClearsTheShell() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        client.config().setShell("/bin/zsh");
        client.config().setShell(null);

        // Then
        assertRequest(HttpMethod.PATCH, "/api/experimental/config", "{\"shell\":\"/bin/zsh\"}");
        assertRequest(HttpMethod.PATCH, "/api/experimental/config", "{\"shell\":null}");
    }
}
