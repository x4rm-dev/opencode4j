package io.opencode4j.event;

/** The event stream is connected and live; events after this one are delivered. */
public record ServerConnected() implements OpenCodeEvent {

    @Override
    public String type() {
        return "server.connected";
    }
}
