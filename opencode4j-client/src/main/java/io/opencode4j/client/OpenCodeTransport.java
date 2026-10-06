package io.opencode4j.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.OpenCodeException;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.model.DataEnvelope;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Shared HTTP transport engine for OpenCode sub-API facades.
 *
 * <p>Handles OkHttp request execution, authentication headers, query parameters,
 * and single-pass JSON deserialization including v2 {@code {"data": ...}} response envelopes.</p>
 */
public final class OpenCodeTransport {

    private static final MediaType JSON = MediaType.get(HttpHeaders.APPLICATION_JSON_UTF8);
    private static final MediaType OCTET_STREAM = MediaType.get(HttpHeaders.APPLICATION_OCTET_STREAM);

    private final OpenCodeConfig config;
    private final OkHttpClient httpClient;
    private final OkHttpClient streamingHttpClient;
    private final ObjectMapper mapper;
    private final String authHeader;

    public OpenCodeTransport(OkHttpClient httpClient, OpenCodeConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = buildHttpClient(
                httpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMs())),
                Duration.ofMillis(config.requestTimeoutMs()));
        this.streamingHttpClient = buildHttpClient(
                this.httpClient.newBuilder(),
                Duration.ZERO);
        this.authHeader = config.hasServePassword() ? toAuthHeader(config) : null;
    }

    private static OkHttpClient buildHttpClient(OkHttpClient.Builder builder, Duration timeout) {
        return builder.readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
    }

    private static String toAuthHeader(OpenCodeConfig config) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (config.serveUsername() + ":" + config.servePassword()).getBytes(StandardCharsets.UTF_8));
    }

    public OpenCodeConfig config() {
        return config;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Sends a standard typed HTTP request.
     */
    public <T, R> R send(String method, String path, T body, Class<R> responseType) {
        return execute(method, path, body, httpClient, mapper.constructType(responseType));
    }

    /**
     * Sends a request expecting a generic/parameterized response (e.g. {@code TypeReference<Map<String, McpStatus>>}).
     */
    public <T, R> R send(String method, String path, T body, TypeReference<R> typeRef) {
        return execute(method, path, body, httpClient, mapper.constructType(typeRef.getType()));
    }

    /**
     * Sends a request using the unbounded read-timeout client (for synchronous prompts blocking on LLM generation).
     */
    public <T, R> R sendWithoutTimeout(String method, String path, T body, Class<R> responseType) {
        return execute(method, path, body, streamingHttpClient, mapper.constructType(responseType));
    }

    /**
     * Downloads a response body as raw bytes, without interpreting it as JSON.
     *
     * <p>The whole body is held in memory. Use it for file content, whatever its media type.</p>
     */
    public byte[] download(String path) {
        Request request = baseRequest(path)
                .header(HttpHeaders.ACCEPT, HttpHeaders.ANY)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new OpenCodeException(
                        HttpMethod.GET + " " + path + " -> HTTP " + response.code() + ": "
                                + responseBody(response.body()),
                        response.code());
            }
            ResponseBody body = response.body();
            return body == null ? new byte[0] : body.bytes();
        } catch (IOException exception) {
            throw new OpenCodeException(
                    HttpMethod.GET + " " + path + " failed: " + exception.getMessage(), exception);
        }
    }

    /**
     * Sends raw bytes as {@code application/octet-stream} and reads a JSON response.
     */
    public <R> R upload(String method, String path, byte[] content, Class<R> responseType) {
        Request request = baseRequest(path)
                .header(HttpHeaders.ACCEPT, HttpHeaders.APPLICATION_JSON)
                .method(method, RequestBody.create(content, OCTET_STREAM))
                .build();
        return execute(request, method, path, httpClient, mapper.constructType(responseType));
    }

    /**
     * Opens an SSE event stream using the streaming client.
     */
    public SseStream openStream(String path) {
        Request request = buildRequest(HttpMethod.GET, path, null).newBuilder()
                .header(HttpHeaders.ACCEPT, HttpHeaders.TEXT_EVENT_STREAM)
                .build();

        try {
            Call call = streamingHttpClient.newCall(request);
            Response response = call.execute();
            if (!response.isSuccessful()) {
                int status = response.code();
                response.close();
                throw new OpenCodeException("Event stream open failed: HTTP " + status, status);
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new OpenCodeException("Event stream open failed: missing response body");
            }
            String contentType = response.header(HttpHeaders.CONTENT_TYPE);
            if (!isEventStream(contentType)) {
                int status = response.code();
                response.close();
                throw new OpenCodeException(
                        "Event stream open failed: expected " + HttpHeaders.TEXT_EVENT_STREAM
                                + " but the server answered " + contentType + " for " + path
                                + " (is this a server that serves /api/event?)",
                        status);
            }

            return new SseStream(call, response, body);
        } catch (IOException exception) {
            throw new OpenCodeException("Event stream connection failed: " + exception.getMessage(), exception);
        }
    }

    private static boolean isEventStream(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(HttpHeaders.TEXT_EVENT_STREAM);
    }

    public String withDirectory(String path, String directory) {
        if (directory == null || directory.isBlank()) {
            return path;
        }
        String delimiter = path.contains("?") ? "&" : "?";
        return path + delimiter + "directory=" + enc(directory);
    }

    /**
     * Appends the v2 {@code location[directory]} query parameter (OpenAPI {@code deepObject} style).
     *
     * <p>A blank directory leaves the path untouched so the server uses its default location.</p>
     */
    public String withLocation(String path, String directory) {
        if (directory == null || directory.isBlank()) {
            return path;
        }
        String delimiter = path.contains("?") ? "&" : "?";
        return path + delimiter + "location%5Bdirectory%5D=" + enc(directory);
    }

    /**
     * Percent-encodes one URL path segment.
     *
     * <p>Unlike {@link #enc(String)} this encodes a space as {@code %20} rather than {@code +}, which is
     * only valid inside query strings.</p>
     */
    public String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <R> R execute(String method, String path, Object body, OkHttpClient client, JavaType targetType) {
        return execute(buildRequest(method, path, body), method, path, client, targetType);
    }

    private <R> R execute(Request request, String method, String path, OkHttpClient client, JavaType targetType) {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new OpenCodeException(
                        method + " " + path + " -> HTTP " + response.code() + ": " + responseBody(response.body()),
                        response.code());
            }

            if (targetType == null || targetType.hasRawClass(Void.class) || response.body() == null) {
                return null;
            }

            ResponseBody responseBody = response.body();
            if (targetType.hasRawClass(Boolean.class)) {
                String text = responseBody.string();
                boolean value = text.isEmpty() || "true".equalsIgnoreCase(text);
                return mapper.convertValue(Boolean.valueOf(value), targetType);
            }

            byte[] bytes = responseBody.bytes();
            if (bytes.length == 0) {
                return null;
            }

            // 1. Try unwrapping v2 DataEnvelope<R>
            JavaType envelopeType = mapper.getTypeFactory().constructParametricType(DataEnvelope.class, targetType);
            try {
                DataEnvelope<R> envelope = mapper.readValue(bytes, envelopeType);
                if (envelope != null && envelope.data() != null) {
                    return envelope.data();
                }
            } catch (JsonProcessingException ignored) {
                // Not a {"data": ...} envelope; fall through to direct mapping
            }

            // 2. Direct mapping fallback (for non-enveloped endpoints or raw responses)
            return mapper.readValue(bytes, targetType);
        } catch (OpenCodeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OpenCodeException(method + " " + path + " failed: " + exception.getMessage(), exception);
        }
    }

    /** A request builder carrying the URL and credentials but no content negotiation headers. */
    private Request.Builder baseRequest(String path) {
        Request.Builder builder = new Request.Builder().url(config.baseUrl() + path);
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (config.authCookie() != null) {
            builder.header(HttpHeaders.COOKIE, config.authCookie());
        }
        return builder;
    }

    private Request buildRequest(String method, String path, Object body) {
        Request.Builder builder = new Request.Builder()
                .url(config.baseUrl() + path)
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, HttpHeaders.APPLICATION_JSON);

        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (config.authCookie() != null) {
            builder.header(HttpHeaders.COOKIE, config.authCookie());
        }

        return switch (method) {
            case HttpMethod.GET -> builder.get().build();
            case HttpMethod.HEAD -> builder.head().build();
            case HttpMethod.DELETE -> (body == null ? builder.delete() : builder.delete(jsonBody(body))).build();
            case HttpMethod.POST -> builder.post(jsonBody(body)).build();
            case HttpMethod.PUT -> builder.put(jsonBody(body)).build();
            case HttpMethod.PATCH -> builder.patch(jsonBody(body)).build();
            default -> builder.method(method, body == null ? null : jsonBody(body)).build();
        };
    }

    private RequestBody jsonBody(Object body) {
        if (body == null) {
            return RequestBody.create(new byte[0], JSON);
        }
        try {
            return RequestBody.create(mapper.writeValueAsBytes(body), JSON);
        } catch (Exception exception) {
            throw new OpenCodeException("Failed to serialize request body", exception);
        }
    }

    private String responseBody(ResponseBody body) throws IOException {
        return body == null ? "" : body.string();
    }
}
