package io.opencode4j.runtime.media;

import java.util.Objects;

/**
 * A document or binary asset (PDF, CSV, JSON, TXT) attached to an agent prompt turn.
 */
public record DocumentAttachment(
        String name,
        String mimeType,
        String description,
        String dataUri,
        byte[] bytes
) implements MediaAttachment {

    public DocumentAttachment {
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(dataUri, "dataUri");
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
