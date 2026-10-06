package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * A file or media item attached to a prompt, identified by a URI (e.g. {@code file://}, {@code https://},
 * or a RFC 2397 {@code data:<mime>;base64,<data>} URL).
 *
 * @param uri         resource URI or data URI
 * @param name        optional filename (e.g. {@code "chart.png"})
 * @param description optional description or caption for the agent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileAttachment(
        String uri,
        String name,
        String description
) {

    public FileAttachment {
        Objects.requireNonNull(uri, "uri");
    }

    public static FileAttachment of(String uri) {
        return new FileAttachment(uri, null, null);
    }

    public static FileAttachment of(String uri, String name, String description) {
        return new FileAttachment(uri, name, description);
    }

    /**
     * Constructs a {@link FileAttachment} by base64-encoding raw bytes into an RFC 2397 Data URI.
     *
     * @param bytes       raw file or image bytes
     * @param mimeType    MIME content type (e.g. {@code "image/png"}, {@code "application/pdf"})
     * @param name        optional filename
     * @param description optional caption or instructions
     * @return a data-URI backed FileAttachment
     */
    public static FileAttachment fromBytes(byte[] bytes, String mimeType, String name, String description) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String dataUri = "data:" + mimeType + ";base64," + base64;
        return new FileAttachment(dataUri, name, description);
    }

    /**
     * Reads a local file into an RFC 2397 Data URI attachment.
     *
     * @param path        path to the local file
     * @param description optional caption or instructions
     * @return a data-URI backed FileAttachment
     * @throws IOException if reading the file fails
     */
    public static FileAttachment fromPath(Path path, String description) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path);
        String probedMime = Files.probeContentType(path);
        String mimeType = probedMime != null ? probedMime : "application/octet-stream";
        String filename = path.getFileName() != null ? path.getFileName().toString() : "file";
        return fromBytes(bytes, mimeType, filename, description);
    }

    /**
     * Reads a local file into an RFC 2397 Data URI attachment.
     *
     * @param path path to the local file
     * @return a data-URI backed FileAttachment
     * @throws IOException if reading the file fails
     */
    public static FileAttachment fromPath(Path path) throws IOException {
        return fromPath(path, null);
    }
}
