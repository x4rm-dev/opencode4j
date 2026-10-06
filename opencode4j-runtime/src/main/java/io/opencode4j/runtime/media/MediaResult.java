package io.opencode4j.runtime.media;

import java.util.Base64;
import java.util.Objects;

/**
 * A multimodal result returned by a Java method annotated with {@link io.opencode4j.runtime.tool.Tool}.
 *
 * <p>Allows tools to produce charts, generated images, audio clips, or documents and return them
 * to the agent model formatted as standard MCP content.</p>
 */
public record MediaResult(
        byte[] bytes,
        String mimeType,
        String name,
        String text
) {

    public MediaResult {
        Objects.requireNonNull(mimeType, "mimeType");
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public String base64() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static MediaResult image(byte[] bytes, String mimeType, String name) {
        return new MediaResult(bytes, mimeType, name, null);
    }

    public static MediaResult image(byte[] bytes, String mimeType, String name, String caption) {
        return new MediaResult(bytes, mimeType, name, caption);
    }

    public static MediaResult document(byte[] bytes, String mimeType, String name) {
        return new MediaResult(bytes, mimeType, name, null);
    }

    public static MediaResult text(String text) {
        return new MediaResult(new byte[0], "text/plain", null, text);
    }
}
