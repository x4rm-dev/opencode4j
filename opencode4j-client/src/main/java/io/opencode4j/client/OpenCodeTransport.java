package io.opencode4j.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.OpenCodeException;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
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
 * <p>Handles OkHttp request execution, authentication headers, query parameters, single-pass JSON decoding
 * including v2 {@code {"data": ...}} response envelopes, and mapping failures onto the
 * {@link OpenCodeException} hierarchy.</p>
 */
public final class OpenCodeTransport {

    private static final MediaType JSON = MediaType.get(HttpHeaders.APPLICATION_JSON_UTF8);
    private static final MediaType OCTET_STREAM = MediaType.get(HttpHeaders.APPLICATION_OCTET_STREAM);

    private final OpenCodeConfig config;
    private final OkHttpClient httpClient;
    private final OkHttpClient streamingHttpClient;
    private final ObjectMapper mapper;
    private final EnvelopeCodec envelopeCodec;
    private final HttpErrorMapper errorMapper;
    private final String authHeader;
    private final RequestHeaders requestHeaders;
    private final ClientMetrics metrics;

    public OpenCodeTransport(OkHttpClient httpClient, OpenCodeConfig config, ObjectMapper mapper) {
        this(httpClient, config, mapper, RequestHeaders.none(), ClientMetrics.noop());
    }

    public OpenCodeTransport(OkHttpClient httpClient, OpenCodeConfig config, ObjectMapper mapper,
                             RequestHeaders requestHeaders, ClientMetrics metrics) {
        this.requestHeaders = requestHeaders == null ? RequestHeaders.none() : requestHeaders;
        this.metrics = metrics == null ? ClientMetrics.noop() : metrics;
        this.config = config;
        this.mapper = mapper;
        this.envelopeCodec = new EnvelopeCodec(mapper);
        this.errorMapper = new HttpErrorMapper(mapper);
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

    /** Where this transport reports measurements. */
    public ClientMetrics metrics() {
        return metrics;
    }

    /**
     * Sends a standard typed HTTP request.
     */
    public <T, R> R send(String method, String path, T body, Class<R> responseType) {
        return execute(buildRequest(method, path, body), method, path, mapper.constructType(responseType));
    }

    /**
     * Sends a request expecting a generic/parameterized response (e.g. {@code TypeReference<Map<String, McpStatus>>}).
     */
    public <T, R> R send(String method, String path, T body, TypeReference<R> typeRef) {
        return execute(buildRequest(method, path, body), method, path, mapper.constructType(typeRef.getType()));
    }

    /**
     * Sends a request and returns the whole parsed body, envelope included, for routes whose metadata
     * (such as a pagination {@code cursor}) matters.
     *
     * @return the body tree, or {@code null} for an empty body
     */
    public <T> JsonNode sendForTree(String method, String path, T body) {
        return execute(buildRequest(method, path, body), method, path, mapper.constructType(JsonNode.class), true);
    }

    /**
     * Maps a node returned by {@link #sendForTree} to a type, unwrapping a {@code data} envelope if present.
     */
    public <R> R decode(JsonNode root, TypeReference<R> typeRef) {
        try {
            return envelopeCodec.decode(root, mapper.constructType(typeRef.getType()));
        } catch (IOException exception) {
            throw new OpenCodeException("Failed to decode response: " + exception.getMessage(), exception);
        }
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
        String operation = HttpMethod.GET + " " + path;
        long started = System.nanoTime();
        int status = -1;
        try (Response response = httpClient.newCall(request).execute()) {
            status = response.code();
            if (!response.isSuccessful()) {
                throw errorMapper.fromResponse(operation, response);
            }
            ResponseBody body = response.body();
            return body == null ? new byte[0] : body.bytes();
        } catch (IOException exception) {
            throw errorMapper.fromIOException(operation, exception);
        } finally {
            record(HttpMethod.GET, path, status, started);
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
        return execute(request, method, path, mapper.constructType(responseType));
    }

    /**
     * Opens an SSE event stream using the streaming client.
     */
    public SseStream openStream(String path) {
        return openStream(path, null);
    }

    /**
     * Opens an SSE event stream, resuming after {@code lastEventId} when the server supports it.
     *
     * @param lastEventId id of the last event received on a previous connection, or {@code null}
     */
    public SseStream openStream(String path, String lastEventId) {
        Request.Builder builder = baseRequest(path)
                .header(HttpHeaders.ACCEPT, HttpHeaders.TEXT_EVENT_STREAM)
                .get();
        if (lastEventId != null && !lastEventId.isBlank()) {
            builder.header(HttpHeaders.LAST_EVENT_ID, lastEventId);
        }
        String operation = "Event stream " + path;
        Response response = null;
        long started = System.nanoTime();
        int status = -1;
        try {
            Call call = streamingHttpClient.newCall(builder.build());
            response = call.execute();
            status = response.code();
            if (!response.isSuccessful()) {
                throw errorMapper.fromResponse(operation, response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new OpenCodeException(operation + " failed: missing response body");
            }
            String contentType = response.header(HttpHeaders.CONTENT_TYPE);
            if (!isEventStream(contentType)) {
                throw new OpenCodeException(
                        "Event stream open failed: expected " + HttpHeaders.TEXT_EVENT_STREAM
                                + " but the server answered " + contentType + " for " + path
                                + " (is this a server that serves /api/event?)",
                        response.code());
            }
            SseStream stream = new SseStream(call, response, body);
            response = null;
            return stream;
        } catch (IOException exception) {
            throw errorMapper.fromIOException(operation, exception);
        } finally {
            record(HttpMethod.GET, path, status, started);
            if (response != null) {
                response.close();
            }
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

    private <R> R execute(Request request, String method, String path, JavaType targetType) {
        return execute(request, method, path, targetType, false);
    }

    @SuppressWarnings("unchecked")
    private <R> R execute(Request request, String method, String path, JavaType targetType, boolean rawTree) {
        String operation = method + " " + path;
        long started = System.nanoTime();
        int status = -1;
        try (Response response = httpClient.newCall(request).execute()) {
            status = response.code();
            if (!response.isSuccessful()) {
                throw errorMapper.fromResponse(operation, response);
            }

            ResponseBody responseBody = response.body();
            if (targetType == null || targetType.hasRawClass(Void.class) || responseBody == null) {
                return null;
            }

            if (targetType.hasRawClass(Boolean.class)) {
                String text = responseBody.string();
                boolean value = text.isEmpty() || "true".equalsIgnoreCase(text);
                return mapper.convertValue(Boolean.valueOf(value), targetType);
            }

            byte[] bytes = responseBody.bytes();
            if (bytes.length == 0) {
                return null;
            }
            JsonNode root = envelopeCodec.readTree(bytes);
            return rawTree ? (R) root : envelopeCodec.decode(root, targetType);
        } catch (OpenCodeException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new OpenCodeException(operation + " returned an undecodable body: "
                    + exception.getOriginalMessage(), exception);
        } catch (IOException exception) {
            throw errorMapper.fromIOException(operation, exception);
        } finally {
            record(method, path, status, started);
        }
    }

    private void record(String method, String path, int status, long startedNanos) {
        try {
            metrics.httpRequest(method, HttpRoutes.template(path), status, System.nanoTime() - startedNanos);
        } catch (RuntimeException ignored) {
            // Metrics must never break a request.
        }
    }

    /** A request builder carrying the URL and credentials but no content negotiation headers. */
    private Request.Builder baseRequest(String path) {
        Request.Builder builder = new Request.Builder().url(config.baseUrl() + path);
        Map<String, String> extra = requestHeaders.headers();
        if (extra != null) {
            extra.forEach((name, value) -> {
                if (value != null && !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                        && !HttpHeaders.COOKIE.equalsIgnoreCase(name)) {
                    builder.header(name, value);
                }
            });
        }
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (config.authCookie() != null) {
            builder.header(HttpHeaders.COOKIE, config.authCookie());
        }
        return builder;
    }

    private Request buildRequest(String method, String path, Object body) {
        Request.Builder builder = baseRequest(path)
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, HttpHeaders.APPLICATION_JSON);

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
}
