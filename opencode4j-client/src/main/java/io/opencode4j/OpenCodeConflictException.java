package io.opencode4j;

/** The request conflicts with the resource's current state (HTTP 409), for example a busy session. */
public class OpenCodeConflictException extends OpenCodeException {

    public OpenCodeConflictException(String message, int statusCode, String errorCode, String requestId, String responseBody) {
        super(message, statusCode, errorCode, requestId, responseBody, null);
    }
}
