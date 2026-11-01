package io.opencode4j;

/** The addressed resource does not exist (HTTP 404), for example an unknown session id. */
public class OpenCodeNotFoundException extends OpenCodeException {

    public OpenCodeNotFoundException(String message, int statusCode, String errorCode, String requestId, String responseBody) {
        super(message, statusCode, errorCode, requestId, responseBody, null);
    }
}
