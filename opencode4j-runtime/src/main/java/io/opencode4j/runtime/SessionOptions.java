package io.opencode4j.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How a session is opened.
 *
 * @param title         session title, or {@code null} for the agent name
 * @param metadata      caller metadata stored on the server session, for example a conversation id
 * @param deleteOnClose whether {@link AgentSession#close()} deletes the server session; by default it only
 *                      detaches, so the session can be resumed after a restart
 */
public record SessionOptions(String title, Map<String, Object> metadata, boolean deleteOnClose) {

    public SessionOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SessionOptions defaults() {
        return new SessionOptions(null, Map.of(), false);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link SessionOptions}. */
    public static final class Builder {
        private String title;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private boolean deleteOnClose;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder metadata(String key, Object value) {
            metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder metadata(Map<String, Object> values) {
            metadata.putAll(values);
            return this;
        }

        public Builder deleteOnClose(boolean deleteOnClose) {
            this.deleteOnClose = deleteOnClose;
            return this;
        }

        public SessionOptions build() {
            return new SessionOptions(title, metadata, deleteOnClose);
        }
    }
}
