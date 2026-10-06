package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.file.FileEntry;
import io.opencode4j.model.file.FileType;
import io.opencode4j.model.file.FileWriteResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Lists, searches, reads and writes files on the server's machine.
 *
 * <p>Every operation takes an optional {@code directory}: the location the paths are relative to. When it is
 * {@code null} or blank the server uses its default location.</p>
 */
public final class FileApi {

    private static final String FS_PATH = "/api/fs";

    private final OpenCodeTransport transport;

    public FileApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Lists the direct children of a directory.
     *
     * @param path an absolute path or one relative to the location; {@code null} lists the location itself
     * @return entries with paths relative to the location; directories end with {@code /}
     */
    public List<FileEntry> list(String path, String directory) {
        String target = FS_PATH + "/list" + (path == null ? "" : "?path=" + transport.enc(path));
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(target, directory),
                null,
                new TypeReference<List<FileEntry>>() { });
    }

    /**
     * Finds files and directories by name, best matches first.
     *
     * @param type  restrict the result to files or directories, or {@code null} for both
     * @param limit the maximum number of results, or {@code null} for the server's default
     */
    public List<FileEntry> find(String query, FileType type, Integer limit, String directory) {
        StringBuilder target = new StringBuilder(FS_PATH).append("/find?query=")
                .append(transport.enc(Objects.requireNonNull(query, "query")));
        if (type != null && type != FileType.UNKNOWN) {
            target.append("&type=").append(type.wire());
        }
        if (limit != null) {
            target.append("&limit=").append(limit);
        }
        return transport.send(
                HttpMethod.GET,
                transport.withLocation(target.toString(), directory),
                null,
                new TypeReference<List<FileEntry>>() { });
    }

    /**
     * Reads a file as raw bytes. The whole file is held in memory.
     *
     * @param path relative to the location, for example {@code src/Main.java}
     * @throws IllegalArgumentException if the path is absolute or contains {@code .} or {@code ..} segments;
     *         reads are confined to the location
     * @throws io.opencode4j.OpenCodeException with status {@code 404} if the file does not exist
     */
    public byte[] read(String path, String directory) {
        return transport.download(transport.withLocation(FS_PATH + "/read/" + readPath(path), directory));
    }

    /** Reads a file and decodes it as UTF-8. */
    public String readText(String path, String directory) {
        return new String(read(path, directory), StandardCharsets.UTF_8);
    }

    /**
     * Writes a file, creating missing parent directories and replacing existing content.
     *
     * <p>Experimental: the server may change this without notice. Unlike {@link #read}, the target is not
     * confined to the location, so an absolute path writes anywhere the server process can.</p>
     *
     * @param path an absolute path or one relative to the location
     * @return the absolute path that was written
     */
    public FileWriteResult write(String path, byte[] content, String directory) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        return transport.upload(
                HttpMethod.POST,
                transport.withLocation("/api/experimental/fs/write?path=" + transport.enc(path), directory),
                content,
                FileWriteResult.class);
    }

    /** Writes UTF-8 text; see {@link #write(String, byte[], String)}. */
    public FileWriteResult writeText(String path, String content, String directory) {
        return write(path, Objects.requireNonNull(content, "content").getBytes(StandardCharsets.UTF_8), directory);
    }

    /**
     * Validates a relative path and encodes it for the URL, one segment at a time so slashes survive.
     *
     * <p>{@code .} and {@code ..} are rejected because the HTTP client would collapse them before the request
     * is sent, silently turning a file read into a request for some other route.</p>
     */
    private String readPath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("Read paths must be relative to the location: " + path);
        }
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "Read paths must not contain empty, \".\" or \"..\" segments: " + path);
            }
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(transport.segment(segment));
        }
        return encoded.toString();
    }
}
