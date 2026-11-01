package io.opencode4j.runtime;

/**
 * A prompt was sent to a session that is still working on a previous turn. Wait for the active run to finish,
 * or enable queueing on the session.
 */
public class SessionBusyException extends RuntimeException {

    private final String sessionId;

    public SessionBusyException(String sessionId, String message) {
        super(message);
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }
}
