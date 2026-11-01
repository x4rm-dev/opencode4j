package io.opencode4j;

/** The server rejected the credentials (HTTP 401) or the caller lacks access (HTTP 403). */
public class OpenCodeAuthException extends OpenCodeException {

    public OpenCodeAuthException(String message, int statusCode, String errorCode, String requestId, String responseBody) {
        super(message, statusCode, errorCode, requestId, responseBody, null);
    }
}
