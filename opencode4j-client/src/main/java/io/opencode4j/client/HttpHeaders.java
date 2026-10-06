package io.opencode4j.client;

/** Standard HTTP header names and common media types. */
public final class HttpHeaders {
    public static final String AUTHORIZATION = "Authorization";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String COOKIE = "Cookie";

    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_JSON_UTF8 = "application/json; charset=utf-8";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String TEXT_EVENT_STREAM = "text/event-stream";
    public static final String ANY = "*/*";

    private HttpHeaders() { }
}
