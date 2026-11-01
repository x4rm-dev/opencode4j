package io.opencode4j.model.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared normalisation for message records. */
final class Messages {

    private Messages() {
    }

    /** An unmodifiable copy that tolerates {@code null} values, or an empty map. */
    static Map<String, Object> metadata(Map<String, Object> metadata) {
        return metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
