package io.opencode4j.model.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileModelsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsEntriesAsTheServerReturnedThem() throws Exception {
        // Given: copied from a live OpenCode 2.0.20 listing
        String json = "[{\"path\":\"docs/\",\"type\":\"directory\"},{\"path\":\"pom.xml\",\"type\":\"file\"}]";

        // When
        List<FileEntry> entries = mapper.readValue(json, new TypeReference<>() { });

        // Then
        assertEquals(new FileEntry("docs/", FileType.DIRECTORY), entries.get(0));
        assertEquals(new FileEntry("pom.xml", FileType.FILE), entries.get(1));
    }

    @Test
    void mapsFutureEntryTypesToUnknown() throws Exception {
        assertEquals(FileType.UNKNOWN, mapper.readValue("\"symlink\"", FileType.class));
        assertEquals("\"directory\"", mapper.writeValueAsString(FileType.DIRECTORY));
    }

    @Test
    void readsTheWriteResult() throws Exception {
        assertEquals(
                "/work/out.txt",
                mapper.readValue("{\"path\":\"/work/out.txt\"}", FileWriteResult.class).path());
    }

    @Test
    void createsDataUriFromFileBytesAndPath(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        byte[] fakeImage = new byte[] { (byte) 0x89, 'P', 'N', 'G' };
        io.opencode4j.model.FileAttachment attachment = io.opencode4j.model.FileAttachment.fromBytes(
                fakeImage, "image/png", "chart.png", "System latency chart");

        org.junit.jupiter.api.Assertions.assertTrue(attachment.uri().startsWith("data:image/png;base64,"));
        assertEquals("chart.png", attachment.name());
        assertEquals("System latency chart", attachment.description());

        java.nio.file.Path file = tempDir.resolve("sample.txt");
        java.nio.file.Files.writeString(file, "hello file");
        io.opencode4j.model.FileAttachment fromFile = io.opencode4j.model.FileAttachment.fromPath(file, "Sample text file");
        org.junit.jupiter.api.Assertions.assertTrue(fromFile.uri().startsWith("data:"));
        assertEquals("sample.txt", fromFile.name());
    }
}
