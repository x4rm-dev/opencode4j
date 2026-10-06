package io.opencode4j.runtime.media;

import java.util.Objects;

/**
 * An audio recording or clip attached to an agent prompt turn.
 */
public record AudioAttachment(
        String name,
        String mimeType,
        String description,
        String dataUri,
        byte[] bytes
) implements MediaAttachment {

    public AudioAttachment {
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(dataUri, "dataUri");
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
