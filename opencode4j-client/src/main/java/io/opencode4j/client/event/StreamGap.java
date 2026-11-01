package io.opencode4j.client.event;

/**
 * Events may have been missed: the subscriber must reconcile from the session history
 * ({@code MessageApi}) instead of trusting its own accumulated view.
 *
 * <p>OpenCode does not replay events after a reconnect, so every reconnect is a gap.</p>
 *
 * @param reason          why events were missed
 * @param lostAtMillis    when the stream was last known good, epoch milliseconds
 * @param detectedAtMillis when the gap was detected, epoch milliseconds
 */
public record StreamGap(
        Reason reason,
        long lostAtMillis,
        long detectedAtMillis
) {

    /** Why a subscriber may have missed events. */
    public enum Reason {
        /** The connection dropped and was re-established. */
        RECONNECT,
        /** The subscriber fell behind and its queue overflowed, so queued events were discarded. */
        OVERFLOW
    }
}
