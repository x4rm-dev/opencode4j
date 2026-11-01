package io.opencode4j;

/** The server failed to handle a valid request (HTTP 5xx). Retryable for idempotent requests. */
public class OpenCodeServerException extends OpenCodeException {

    public OpenCodeServerException(String message, int statusCode, String errorCode, String requestId, String responseBody) {
        super(message, statusCode, errorCode, requestId, responseBody, null);
    }
}
