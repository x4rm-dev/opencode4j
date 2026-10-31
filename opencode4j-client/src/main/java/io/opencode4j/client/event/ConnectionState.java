package io.opencode4j.client.event;

/** Lifecycle of the shared event stream. */
public enum ConnectionState {
    /** No subscribers, so no connection is held. */
    IDLE,
    /** Opening the first connection. */
    CONNECTING,
    /** Connected; the server has sent {@code server.connected}. */
    LIVE,
    /** The connection dropped; reconnecting with backoff. Events emitted meanwhile are lost. */
    RECONNECTING,
    /** Reconnect attempts are exhausted or the server rejected the credentials. */
    FAILED,
    /** The bus was closed. */
    CLOSED
}
