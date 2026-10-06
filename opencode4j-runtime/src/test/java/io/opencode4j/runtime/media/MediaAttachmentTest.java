package io.opencode4j.runtime.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.runtime.RunPrompt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaAttachmentTest {

    @Test
    void createsImageAttachmentAndFormatsDataUri() {
        byte[] fakeBytes = new byte[] { 1, 2, 3, 4 };
        ImageAttachment image = MediaAttachment.image(fakeBytes, "image/png", "diagram.png", "System diagram", ImageDetail.HIGH);

        assertEquals("diagram.png", image.name());
        assertEquals("image/png", image.mimeType());
        assertEquals("System diagram", image.description());
        assertEquals(ImageDetail.HIGH, image.detail());
        assertEquals("high", image.detail().wireValue());
        assertTrue(image.dataUri().startsWith("data:image/png;base64,"));
        assertEquals(4, image.bytes().length);

        ImageAttachment defaultImage = MediaAttachment.image(fakeBytes, "image/png");
        assertEquals(ImageDetail.AUTO, defaultImage.detail());
    }

    @Test
    void readsMediaFromFilePath(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("voice.wav");
        Files.write(file, new byte[] { 'R', 'I', 'F', 'F' });

        AudioAttachment audio = MediaAttachment.audio(file, "Voice memo");
        assertNotNull(audio.dataUri());
        assertTrue(audio.dataUri().startsWith("data:"));
        assertEquals("voice.wav", audio.name());
        assertEquals("Voice memo", audio.description());
    }

    @Test
    void attachesMediaToRunPrompt() {
        byte[] pdfBytes = new byte[] { '%', 'P', 'D', 'F' };
        DocumentAttachment doc = MediaAttachment.document(pdfBytes, "application/pdf", "report.pdf", "Quarterly report");

        RunPrompt prompt = RunPrompt.builder("Summarize this document")
                .attach(doc)
                .useSkill("summarizer")
                .build();

        assertEquals("Summarize this document", prompt.text());
        assertEquals(1, prompt.attachments().size());
        assertEquals("report.pdf", prompt.attachments().get(0).name());
        assertEquals(1, prompt.useSkills().size());
    }

    @Test
    void generatesMediaResultForTools() {
        byte[] png = new byte[] { 0x10, 0x20 };
        MediaResult result = MediaResult.image(png, "image/png", "chart.png", "CPU chart");

        assertEquals("image/png", result.mimeType());
        assertEquals("chart.png", result.name());
        assertEquals("CPU chart", result.text());
        assertNotNull(result.base64());
    }
}
