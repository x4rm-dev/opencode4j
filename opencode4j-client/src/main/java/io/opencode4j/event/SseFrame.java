package io.opencode4j.event;

/**
 * One dispatched server-sent event, assembled from the field lines that precede a blank line.
 *
 * <p>See the <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation">
 * event stream interpretation rules</a>.</p>
 *
 * @param id      the last event id seen on the stream when this frame was dispatched, or {@code null} if none
 * @param event   the {@code event:} field, or {@code null} for the default {@code message} type
 * @param data    the {@code data:} lines joined with {@code \n}
 * @param retryMs the reconnection delay the server asked for in this frame, or {@code null} if it sent none
 */
public record SseFrame(
        String id,
        String event,
        String data,
        Long retryMs
) {
}
