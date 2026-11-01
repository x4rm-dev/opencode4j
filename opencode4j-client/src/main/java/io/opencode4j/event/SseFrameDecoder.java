package io.opencode4j.event;

/**
 * Assembles raw server-sent-event lines into {@link SseFrame}s.
 *
 * <p>Follows the event stream interpretation rules: {@code data:} lines accumulate and are joined with
 * {@code \n}, a blank line dispatches the frame, lines starting with {@code :} are comments, a single space
 * after the colon is stripped, and unknown fields are ignored. The last event id persists across frames, as
 * the spec requires, so it can be sent back as {@code Last-Event-ID} on reconnect.</p>
 *
 * <p>Not thread-safe: one decoder belongs to one stream.</p>
 */
public final class SseFrameDecoder {

    private static final String FIELD_DATA = "data";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_ID = "id";
    private static final String FIELD_RETRY = "retry";

    private final StringBuilder data = new StringBuilder();
    private boolean hasData;
    private String event;
    private Long retryMs;
    private String lastEventId;

    /**
     * Feeds one line, without its line terminator.
     *
     * @return the dispatched frame when {@code line} is blank and the frame carried data, otherwise {@code null}
     */
    public SseFrame accept(String line) {
        if (line == null) {
            return null;
        }
        if (line.isEmpty()) {
            return dispatch();
        }
        if (line.charAt(0) == ':') {
            return null;
        }
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : stripLeadingSpace(line.substring(colon + 1));
        applyField(field, value);
        return null;
    }

    /** The last event id received on this stream, or {@code null} if the server has sent none. */
    public String lastEventId() {
        return lastEventId;
    }

    /** Discards a partially assembled frame, for example at end of stream. The last event id is kept. */
    public void reset() {
        data.setLength(0);
        hasData = false;
        event = null;
        retryMs = null;
    }

    private void applyField(String field, String value) {
        switch (field) {
            case FIELD_DATA -> {
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
            }
            case FIELD_EVENT -> event = value;
            case FIELD_ID -> {
                // The spec ignores ids containing NUL.
                if (value.indexOf('\0') < 0) {
                    lastEventId = value.isEmpty() ? null : value;
                }
            }
            case FIELD_RETRY -> retryMs = parseRetry(value);
            default -> {
                // Unknown fields are ignored.
            }
        }
    }

    private SseFrame dispatch() {
        if (!hasData) {
            reset();
            return null;
        }
        SseFrame frame = new SseFrame(lastEventId, event, data.toString(), retryMs);
        reset();
        return frame;
    }

    private Long parseRetry(String value) {
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            return retryMs;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return retryMs;
        }
    }

    private static String stripLeadingSpace(String value) {
        return !value.isEmpty() && value.charAt(0) == ' ' ? value.substring(1) : value;
    }
}
