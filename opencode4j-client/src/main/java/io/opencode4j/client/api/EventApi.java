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
     * work whose events you need, read frames with {@link SseStream#nextFrame()} and decode them with
     * {@link io.opencode4j.event.SseEventParser#parseEnvelope(io.opencode4j.event.SseFrame)}. The server sends
     * {@code server.connected} first and a {@code : heartbeat} comment every 15 seconds. It does not support
     * {@code Last-Event-ID} resume. The caller must close the stream.</p>
     *
     * @throws io.opencode4j.OpenCodeException if the server rejects the request or does not answer with
     *         {@code text/event-stream}
     */
    public SseStream open() {
        return transport.openStream(EVENT_PATH);
    }

    /**
     * Opens the server-sent event stream with a location query parameter.
     *
     * <p>OpenCode 2.2 ignores the location on this route and streams events for every location; filter on
     * {@link io.opencode4j.event.EventEnvelope#directory()} instead.</p>
     *
     * @param directory workspace location directory, or {@code null} for server default
     * @return the connected SSE stream
     */
    public SseStream open(String directory) {
        return transport.openStream(transport.withLocation(EVENT_PATH, directory));
    }
}
