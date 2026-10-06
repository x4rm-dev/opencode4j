package io.opencode4j.client.api;

import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.client.SseStream;

/** Opens the server's event stream. */
public final class EventApi {

    private static final String EVENT_PATH = "/api/event";

    private final OpenCodeTransport transport;

    public EventApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Opens the server-sent event stream covering every location on the server.
     *
     * <p>The stream is volatile by the server's contract: events emitted while no stream is connected are
     * not replayed, and a consumer that falls behind is disconnected. Open it <em>before</em> starting the
     * work whose events you need, and feed each raw line to
     * {@link io.opencode4j.event.SseEventParser#parseLine(String)}. The caller must close the stream.</p>
     *
     * @throws io.opencode4j.OpenCodeException if the server rejects the request or does not answer with
     *         {@code text/event-stream}
     */
    public SseStream open() {
        return transport.openStream(EVENT_PATH);
    }

    /**
     * Opens the server-sent event stream filtered to a specific workspace location directory.
     *
     * @param directory workspace location directory, or {@code null} for server default
     * @return the connected SSE stream
     */
    public SseStream open(String directory) {
        return transport.openStream(transport.withLocation(EVENT_PATH, directory));
    }
}
