package io.opencode4j.agent.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Removes a session workspace directory (which may hold credentials in its {@code opencode.json}). */
final class WorkspaceCleaner {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceCleaner.class);

    private WorkspaceCleaner() {
    }

    static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean workspace {}: {}", directory, e.getMessage());
        }
    }
}
