package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpHeaders;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.file.FileEntry;
import io.opencode4j.model.file.FileType;
import io.opencode4j.model.file.FileWriteResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.Test;

class FileApiTest extends MockServerTest {

    private static final String LOCATION = "location%5Bdirectory%5D=%2Fwork";

    @Test
    void listsTheLocationRootWhenNoPathIsGiven() throws Exception {
        // Given
        server.enqueue(json("{\"location\":{\"directory\":\"/work\"},\"data\":["
                + "{\"path\":\"docs/\",\"type\":\"directory\"},{\"path\":\"pom.xml\",\"type\":\"file\"}]}"));

        // When
        List<FileEntry> entries = client.files().list(null, "/work");

        // Then
        assertEquals(new FileEntry("docs/", FileType.DIRECTORY), entries.get(0));
        assertEquals(new FileEntry("pom.xml", FileType.FILE), entries.get(1));
        assertRequest(HttpMethod.GET, "/api/fs/list?" + LOCATION, "");
    }

    @Test
    void listsASubdirectoryEncodingThePathAsAQueryValue() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[]}"));

        // When
        client.files().list("src/my dir", null);

        // Then
        assertRequest(HttpMethod.GET, "/api/fs/list?path=src%2Fmy+dir", "");
    }

    @Test
    void findsEntriesWithTypeAndLimitFilters() throws Exception {
        // Given
        server.enqueue(json("{\"data\":[{\"path\":\"a/EventApi.java\",\"type\":\"file\"}]}"));
        server.enqueue(json("{\"data\":[]}"));
        server.enqueue(json("{\"data\":[]}"));

        // When
        List<FileEntry> found = client.files().find("Event Api", FileType.FILE, 5, "/work");
        client.files().find("x", null, null, null);
        client.files().find("x", FileType.UNKNOWN, null, null);

        // Then
        assertEquals("a/EventApi.java", found.getFirst().path());
        assertRequest(HttpMethod.GET, "/api/fs/find?query=Event+Api&type=file&limit=5&" + LOCATION, "");
        assertRequest(HttpMethod.GET, "/api/fs/find?query=x", "");
        assertRequest(HttpMethod.GET, "/api/fs/find?query=x", "");
    }

    @Test
    void readsFileContentAsRawBytesWhateverTheMediaType() throws Exception {
        // Given: content that is not valid UTF-8, served with a guessed media type like the real server does
        byte[] binary = {0x00, (byte) 0xFF, (byte) 0xFE, 0x7F, (byte) 0x80, 0x0A};
        server.enqueue(new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                .setBody(new Buffer().write(binary)));

        // When
        byte[] content = client.files().read("opencode4j-client/pom.xml", "/work");

        // Then
        assertArrayEquals(binary, content);
        RecordedRequest request = takeRequest();
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("/api/fs/read/opencode4j-client/pom.xml?" + LOCATION, request.getPath());
        assertEquals(HttpHeaders.ANY, request.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void readsTextAsUtf8() throws Exception {
        // Given
        server.enqueue(new MockResponse().setBody(new Buffer().writeUtf8("héllo ✓")));

        // When / Then
        assertEquals("héllo ✓", client.files().readText("a.txt", null));
    }

    @Test
    void encodesEachPathSegmentButKeepsTheSlashes() throws Exception {
        // Given
        server.enqueue(new MockResponse().setBody("x"));

        // When
        client.files().read("my dir/a&b#c.txt", null);

        // Then
        assertEquals("/api/fs/read/my%20dir/a%26b%23c.txt", takeRequest().getPath());
    }

    @Test
    void rejectsPathsTheServerCannotOrShouldNotServe() {
        // Then
        for (String bad : List.of("/etc/hosts", "../secret", "a/../b", "./a", "a//b", "a/", "")) {
            assertThrows(IllegalArgumentException.class, () -> client.files().read(bad, null), bad);
        }
        assertThrows(NullPointerException.class, () -> client.files().read(null, null));
        assertEquals(0, server.getRequestCount(), "nothing may be sent for an invalid path");
    }

    @Test
    void reportsAMissingFileWithItsStatusAndServerMessage() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"_tag\":\"FileNotFoundError\",\"path\":\"nope.txt\",\"message\":\"File not found\"}"));

        // When
        OpenCodeException failure = assertThrows(
                OpenCodeException.class, () -> client.files().read("nope.txt", null));

        // Then
        assertEquals(404, failure.statusCode());
        assertEquals(true, failure.getMessage().contains("FileNotFoundError"), failure.getMessage());
    }

    @Test
    void writesRawBytesAsOctetStreamAndReturnsTheResolvedPath() throws Exception {
        // Given
        byte[] content = {1, 2, 3, (byte) 0xFF};
        server.enqueue(json("{\"location\":{\"directory\":\"/work\"},\"data\":{\"path\":\"/work/out dir/a.bin\"}}"));

        // When
        FileWriteResult result = client.files().write("out dir/a.bin", content, "/work");

        // Then
        assertEquals("/work/out dir/a.bin", result.path());
        RecordedRequest request = takeRequest();
        assertEquals(HttpMethod.POST, request.getMethod());
        assertEquals("/api/experimental/fs/write?path=out+dir%2Fa.bin&" + LOCATION, request.getPath());
        assertEquals(HttpHeaders.APPLICATION_OCTET_STREAM, request.getHeader(HttpHeaders.CONTENT_TYPE));
        assertArrayEquals(content, request.getBody().readByteArray());
    }

    @Test
    void writesTextAsUtf8() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"path\":\"/a.txt\"}}"));

        // When
        client.files().writeText("a.txt", "héllo", null);

        // Then
        RecordedRequest request = takeRequest();
        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), request.getBody().readByteArray());
        assertNull(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void rejectsMissingWriteArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.files().write(null, new byte[0], null));
        assertThrows(NullPointerException.class, () -> client.files().write("a", null, null));
        assertThrows(NullPointerException.class, () -> client.files().writeText("a", null, null));
        assertThrows(NullPointerException.class, () -> client.files().find(null, null, null, null));
        assertEquals(0, server.getRequestCount());
    }
}
