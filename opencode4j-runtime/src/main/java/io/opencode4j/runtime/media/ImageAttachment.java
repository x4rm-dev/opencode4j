package io.opencode4j.runtime.media;

import java.util.Objects;

/**
 * An image attachment (PNG, JPEG, WebP, GIF) attached to an agent prompt turn.
 *
 * @param name        optional filename (e.g. {@code "screenshot.png"})
 * @param mimeType    image MIME type (e.g. {@code "image/png"})
 * @param description optional caption or prompt guidance
 * @param detail      vision resolution fidelity ({@link ImageDetail#AUTO}, {@link ImageDetail#LOW}, {@link ImageDetail#HIGH})
 * @param dataUri     RFC 2397 Data URI payload
 * @param bytes       raw uncompressed bytes
 */
public record ImageAttachment(
        String name,
        String mimeType,
        String description,
        ImageDetail detail,
        String dataUri,
        byte[] bytes
) implements MediaAttachment {

    public ImageAttachment {
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(dataUri, "dataUri");
        detail = detail == null ? ImageDetail.AUTO : detail;
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** Backward-compatible 5-argument constructor defaulting to {@link ImageDetail#AUTO}. */
    public ImageAttachment(
            String name,
            String mimeType,
            String description,
            String dataUri,
            byte[] bytes
    ) {
        this(name, mimeType, description, ImageDetail.AUTO, dataUri, bytes);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
