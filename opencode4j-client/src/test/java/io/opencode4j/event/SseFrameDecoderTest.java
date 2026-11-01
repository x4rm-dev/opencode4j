package io.opencode4j.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SseFrameDecoderTest {

    @Test
    void dispatchesOnBlankLine() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        assertNull(decoder.accept("data: {\"type\":\"x\"}"));
        SseFrame frame = decoder.accept("");

        // Then
        assertEquals(new SseFrame(null, null, "{\"type\":\"x\"}", null), frame);
    }

    @Test
    void joinsMultipleDataLinesWithNewline() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        SseFrame frame = feed(decoder, "data: {\"type\":", "data:\"x\"}", "").getFirst();

        // Then
        assertEquals("{\"type\":\n\"x\"}", frame.data());
        assertEquals("x", ((UnknownEvent) SseEventParser.parse(frame)).type());
    }

    @Test
    void readsIdEventAndRetryFields() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        SseFrame frame = feed(decoder, "id: 42", "event: message", "retry: 1500", "data: payload", "").getFirst();

        // Then
        assertEquals(new SseFrame("42", "message", "payload", 1500L), frame);
        assertEquals("42", decoder.lastEventId());
    }

    @Test
    void keepsLastEventIdAcrossFrames() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        List<SseFrame> frames = feed(decoder, "id: 7", "data: a", "", "data: b", "");

        // Then
        assertEquals("7", frames.get(0).id());
        assertEquals("7", frames.get(1).id());
        assertNull(frames.get(1).event());
    }

    @Test
    void emptyIdClearsLastEventIdAndNulIdIsIgnored() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        feed(decoder, "id: 1", "data: a", "", "id: bad\0id", "data: b", "");
        String afterNul = decoder.lastEventId();
        feed(decoder, "id:", "data: c", "");

        // Then
        assertEquals("1", afterNul);
        assertNull(decoder.lastEventId());
    }

    @Test
    void ignoresCommentsUnknownFieldsAndInvalidRetry() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        List<SseFrame> frames = feed(decoder, ": keep-alive", "foo: bar", "retry: soon", "data: x", "");

        // Then
        assertEquals(List.of(new SseFrame(null, null, "x", null)), frames);
    }

    @Test
    void blankLineWithoutDataDispatchesNothing() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        List<SseFrame> frames = feed(decoder, "event: ping", "", "", "data: x", "");

        // Then: the event name of the empty frame does not leak into the next one
        assertEquals(List.of(new SseFrame(null, null, "x", null)), frames);
    }

    @Test
    void fieldWithoutColonHasEmptyValueAndOnlyOneLeadingSpaceIsStripped() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();

        // When
        List<SseFrame> frames = feed(decoder, "data", "data:  two spaces", "");

        // Then
        assertEquals("\n two spaces", frames.getFirst().data());
    }

    @Test
    void resetDropsPartialFrameButKeepsId() {
        // Given
        SseFrameDecoder decoder = new SseFrameDecoder();
        feed(decoder, "id: 3", "data: partial");

        // When
        decoder.reset();
        List<SseFrame> frames = feed(decoder, "data: next", "");

        // Then
        assertEquals(List.of(new SseFrame("3", null, "next", null)), frames);
    }

    @Test
    void randomFrameContentsRoundTrip() {
        // Given
        Random random = new Random(20261031L);
        for (int iteration = 0; iteration < 200; iteration++) {
            List<String> lines = new ArrayList<>();
            List<String> expected = new ArrayList<>();
            int frameCount = 1 + random.nextInt(5);
            for (int f = 0; f < frameCount; f++) {
                int dataLines = 1 + random.nextInt(3);
                List<String> parts = new ArrayList<>();
                for (int d = 0; d < dataLines; d++) {
                    String part = "v" + random.nextInt(1000);
                    parts.add(part);
                    lines.add("data: " + part);
                    if (random.nextBoolean()) {
                        lines.add(": comment " + d);
                    }
                }
                lines.add("");
                expected.add(String.join("\n", parts));
            }

            // When
            List<String> actual = feed(new SseFrameDecoder(), lines.toArray(String[]::new)).stream()
                    .map(SseFrame::data).toList();

            // Then
            assertEquals(expected, actual);
        }
    }

    private static List<SseFrame> feed(SseFrameDecoder decoder, String... lines) {
        List<SseFrame> frames = new ArrayList<>();
        for (String line : lines) {
            SseFrame frame = decoder.accept(line);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }
}
