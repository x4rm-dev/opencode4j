package io.opencode4j.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InsecureRemoteTest {

    @Test
    void refusesToSendThePasswordInClearToARemoteHost() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenCodeConfig.builder("http://opencode.internal:4096").servePassword("pw").build());
    }

    @Test
    void allowsLoopbackTlsPasswordlessOrExplicitOptIn() {
        assertDoesNotThrow(() -> OpenCodeConfig.builder("http://127.0.0.1:4096").servePassword("pw").build());
        assertDoesNotThrow(() -> OpenCodeConfig.builder("http://localhost:4096").servePassword("pw").build());
        assertDoesNotThrow(() -> OpenCodeConfig.builder("http://[::1]:4096").servePassword("pw").build());
        assertDoesNotThrow(() -> OpenCodeConfig.builder("https://opencode.internal").servePassword("pw").build());
        assertDoesNotThrow(() -> OpenCodeConfig.builder("http://opencode.internal:4096").build());
        assertDoesNotThrow(() -> OpenCodeConfig.builder("http://opencode.internal:4096").servePassword("pw")
                .allowInsecureRemote(true).build());
    }
}
