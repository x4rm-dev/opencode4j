package io.opencode4j;

/** The request did not produce an HTTP response: connection refused, reset, or timed out. Always retryable. */
public class OpenCodeTransportException extends OpenCodeException {

    public OpenCodeTransportException(String message, Throwable cause) {
        super(message, NO_STATUS, cause);
    }

    @Override
    public boolean retryable() {
        return true;
    }
}
