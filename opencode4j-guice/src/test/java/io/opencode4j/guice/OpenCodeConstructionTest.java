package io.opencode4j.guice;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opencode4j.OpenCode;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentRuntime;
import org.junit.jupiter.api.Test;

class OpenCodeConstructionTest {

    @Test
    void createsWithoutAndWithGuice() {
        OpenCodeConfig config = OpenCodeConfig.builder("http://localhost:4096").build();

        assertNotNull(OpenCodeClient.create(config));
        assertNotNull(OpenCode.create(config).runtime());

        Injector injector = Guice.createInjector(new OpenCodeModule(config));
        assertNotNull(injector.getInstance(OpenCodeClient.class));
        assertNotNull(injector.getInstance(AgentRuntime.class));
    }
}
