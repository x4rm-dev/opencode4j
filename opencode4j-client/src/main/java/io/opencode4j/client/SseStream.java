package io.opencode4j.client;

import io.opencode4j.event.SseFrame;
import io.opencode4j.event.SseFrameDecoder;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * A closeable server-sent event stream.
 *
 * <p>Read either raw lines with {@link #readLine()} or assembled frames with {@link #nextFrame()}; do not
 * mix the two on one stream, because lines consumed by {@code readLine} never reach the frame decoder.</p>
 */
public class SseStream implements AutoCloseable {
    private final Call call;
    private final Response response;
    private final BufferedSource source;
    private final SseFrameDecoder decoder = new SseFrameDecoder();

    public SseStream(Call call, Response response, ResponseBody body) {
        this.call = call;
        this.response = response;
        this.source = body.source();
    }

    /** Reads the next raw SSE line, or {@code null} at EOF. */
    public String readLine() throws IOException {
        return source.readUtf8Line();
    }

    /**
     * Reads lines until the next frame is dispatched.
     *
     * @return the next frame, or {@code null} at end of stream (a partially received frame is discarded)
     */
    public SseFrame nextFrame() throws IOException {
        String line;
        while ((line = source.readUtf8Line()) != null) {
            SseFrame frame = decoder.accept(line);
            if (frame != null) {
                return frame;
            }
        }
        decoder.reset();
        return null;
    }

    /** The last event id received through {@link #nextFrame()}, or {@code null} if none. */
    public String lastEventId() {
        return decoder.lastEventId();
    }

    @Override
    public void close() {
        call.cancel();
        response.close();
    }
}
