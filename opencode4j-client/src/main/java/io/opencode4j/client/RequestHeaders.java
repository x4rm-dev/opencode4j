package io.opencode4j.client;

import java.util.Map;

/**
 * Extra headers for every request to the server, evaluated per request on the calling thread, so it can read the
 * current trace or MDC context, for example to propagate {@code traceparent}.
 *
 * <p>{@code Authorization} and {@code Cookie} are owned by the client and cannot be replaced.</p>
 */
@FunctionalInterface
public interface RequestHeaders {

    /** Headers for the request about to be sent; never {@code null}. */
    Map<String, String> headers();

    static RequestHeaders none() {
        return Map::of;
    }
}
