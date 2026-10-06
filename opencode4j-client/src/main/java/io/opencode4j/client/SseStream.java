package io.opencode4j.client;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/** A closeable source of raw SSE lines. */
public class SseStream implements AutoCloseable {
    private final Call call;
    private final Response response;
    private final BufferedSource source;

    public SseStream(Call call, Response response, ResponseBody body) {
        this.call = call;
        this.response = response;
        this.source = body.source();
    }

    /** Reads the next raw SSE line, or {@code null} at EOF. */
    public String readLine() throws IOException {
        return source.readUtf8Line();
    }

    @Override
    public void close() {
        call.cancel();
        response.close();
    }
}
