package io.opencode4j;

/** The server rejected the request as invalid (HTTP 400 or 422); {@link #errorCode()} carries the server's tag. */
public class OpenCodeValidationException extends OpenCodeException {

    public OpenCodeValidationException(String message, int statusCode, String errorCode, String requestId, String responseBody) {
        super(message, statusCode, errorCode, requestId, responseBody, null);
    }
}
