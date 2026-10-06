package io.opencode4j;

/**
 * Thrown when an OpenCode API call fails (non-2xx response, transport error, or protocol violation).
 */
public class OpenCodeException extends RuntimeException {

    private final int statusCode;   // -1 when not an HTTP failure

    public OpenCodeException(String message) {
        this(message, -1, null);
    }

    public OpenCodeException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public OpenCodeException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public OpenCodeException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code, or -1 if the failure was not an HTTP response. */
    public int statusCode() { return statusCode; }
}
