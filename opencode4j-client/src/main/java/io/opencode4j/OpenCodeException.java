package io.opencode4j;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Thrown when an OpenCode API call fails (non-2xx response, transport error, or protocol violation).
 *
 * <p>HTTP failures are raised as a subtype that names the failure class: {@link OpenCodeAuthException},
 * {@link OpenCodeNotFoundException}, {@link OpenCodeConflictException}, {@link OpenCodeValidationException},
 * {@link OpenCodeServerException}; I/O failures as {@link OpenCodeTransportException}. Catch this type to
 * handle all of them.</p>
 *
 * <p>The message never contains the response body, because a body can echo prompt or log content. The
 * body is kept, truncated, in {@link #responseBody()}; redact it before logging.</p>
 */
@Getter
@Accessors(fluent = true)
public class OpenCodeException extends RuntimeException {

    /** Status code used when the failure was not an HTTP response. */
    public static final int NO_STATUS = -1;

    /** HTTP status code, or {@value #NO_STATUS} if the failure was not an HTTP response. */
    private final int statusCode;
    /** Machine-readable error tag from the server body (for example {@code "NotFoundError"}), or {@code null}. */
    private final String errorCode;
    /** Server request id from the response headers, or {@code null}. */
    private final String requestId;
    /** Response body truncated to a bounded size, or {@code null}. May contain sensitive content. */
    private final String responseBody;

    public OpenCodeException(String message) {
        this(message, NO_STATUS, null);
    }

    public OpenCodeException(String message, Throwable cause) {
        this(message, NO_STATUS, cause);
    }

    public OpenCodeException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public OpenCodeException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, null, null, null, cause);
    }

    public OpenCodeException(String message, int statusCode, String errorCode, String requestId,
                             String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.requestId = requestId;
        this.responseBody = responseBody;
    }

    /**
     * Whether repeating the same request may succeed: request timeouts (408), rate limiting (429), server
     * errors (5xx) and, via {@link OpenCodeTransportException}, I/O failures. Callers must still only retry
     * idempotent requests.
     */
    public boolean retryable() {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
