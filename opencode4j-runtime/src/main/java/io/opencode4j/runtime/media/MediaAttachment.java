package io.opencode4j.runtime.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * Base sealed interface for multimodal prompt attachments (images, audio, documents).
 */
public sealed interface MediaAttachment permits ImageAttachment, AudioAttachment, DocumentAttachment {

    /** Optional filename (e.g. {@code "architecture.png"}). */
    String name();

    /** Content MIME type (e.g. {@code "image/png"}, {@code "application/pdf"}). */
    String mimeType();

    /** Optional description or instructions guiding the model on this media. */
    String description();

    /** RFC 2397 Data URI representation: {@code data:<mime>;base64,<payload>}. */
    String dataUri();

    /** Raw unencoded bytes of the media item. */
    byte[] bytes();

    /** Constructs an image attachment from raw bytes defaulting to {@link ImageDetail#AUTO}. */
    static ImageAttachment image(byte[] bytes, String mimeType) {
        return image(bytes, mimeType, "image", null, ImageDetail.AUTO);
    }

    /** Constructs an image attachment from raw bytes with a specified {@link ImageDetail} resolution. */
    static ImageAttachment image(byte[] bytes, String mimeType, ImageDetail detail) {
        return image(bytes, mimeType, "image", null, detail);
    }

    /** Constructs an image attachment with full metadata defaulting to {@link ImageDetail#AUTO}. */
    static ImageAttachment image(byte[] bytes, String mimeType, String name, String description) {
        return image(bytes, mimeType, name, description, ImageDetail.AUTO);
    }

    /** Constructs an image attachment with full metadata and specified {@link ImageDetail} resolution. */
    static ImageAttachment image(byte[] bytes, String mimeType, String name, String description, ImageDetail detail) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
        String dataUri = toDataUri(bytes, mimeType);
        return new ImageAttachment(name, mimeType, description, detail, dataUri, bytes);
    }

    /** Reads a local image file into an image attachment defaulting to {@link ImageDetail#AUTO}. */
    static ImageAttachment image(Path path) throws IOException {
        return image(path, null, ImageDetail.AUTO);
    }

    /** Reads a local image file into an image attachment with a specified {@link ImageDetail}. */
    static ImageAttachment image(Path path, ImageDetail detail) throws IOException {
        return image(path, null, detail);
    }

    /** Reads a local image file with a description into an image attachment defaulting to {@link ImageDetail#AUTO}. */
    static ImageAttachment image(Path path, String description) throws IOException {
        return image(path, description, ImageDetail.AUTO);
    }

    /** Reads a local image file with a description and specified {@link ImageDetail}. */
    static ImageAttachment image(Path path, String description, ImageDetail detail) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path);
        String probed = Files.probeContentType(path);
        String mime = probed != null ? probed : "image/png";
        String name = path.getFileName() != null ? path.getFileName().toString() : "image.png";
        return image(bytes, mime, name, description, detail);
    }

    /** Constructs an audio attachment from raw bytes. */
    static AudioAttachment audio(byte[] bytes, String mimeType) {
        return audio(bytes, mimeType, "audio", null);
    }

    /** Constructs an audio attachment with full metadata. */
    static AudioAttachment audio(byte[] bytes, String mimeType, String name, String description) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
        String dataUri = toDataUri(bytes, mimeType);
        return new AudioAttachment(name, mimeType, description, dataUri, bytes);
    }

    /** Reads a local audio file into an audio attachment. */
    static AudioAttachment audio(Path path) throws IOException {
        return audio(path, null);
    }

    /** Reads a local audio file with a description into an audio attachment. */
    static AudioAttachment audio(Path path, String description) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path);
        String probed = Files.probeContentType(path);
        String mime = probed != null ? probed : "audio/mp3";
        String name = path.getFileName() != null ? path.getFileName().toString() : "audio.mp3";
        return audio(bytes, mime, name, description);
    }

    /** Constructs a document attachment from raw bytes. */
    static DocumentAttachment document(byte[] bytes, String mimeType, String name, String description) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
        String dataUri = toDataUri(bytes, mimeType);
        return new DocumentAttachment(name, mimeType, description, dataUri, bytes);
    }

    /** Reads a local document into a document attachment. */
    static DocumentAttachment document(Path path) throws IOException {
        return document(path, null);
    }

    /** Reads a local document with a description into a document attachment. */
    static DocumentAttachment document(Path path, String description) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path);
        String probed = Files.probeContentType(path);
        String mime = probed != null ? probed : "application/octet-stream";
        String name = path.getFileName() != null ? path.getFileName().toString() : "document";
        return document(bytes, mime, name, description);
    }

    private static String toDataUri(byte[] bytes, String mimeType) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
