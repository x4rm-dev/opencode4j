package io.opencode4j.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.OpenCodeAuthException;
import io.opencode4j.OpenCodeConflictException;
import io.opencode4j.OpenCodeException;
import io.opencode4j.OpenCodeNotFoundException;
import io.opencode4j.OpenCodeServerException;
import io.opencode4j.OpenCodeTransportException;
import io.opencode4j.OpenCodeValidationException;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Turns failed HTTP exchanges into the {@link OpenCodeException} hierarchy.
 *
 * <p>Messages carry the method, path, status and the server's error tag, never the body. The body is kept,
 * truncated, on the exception for callers that need it.</p>
 */
final class HttpErrorMapper {

    static final int MAX_BODY_CHARS = 2_048;

    private static final List<String> ERROR_CODE_FIELDS = List.of("_tag", "name", "kind", "code", "type");
    private static final List<String> NESTED_ERROR_FIELDS = List.of("error", "data");

    private final ObjectMapper mapper;

    HttpErrorMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Builds the exception for a non-2xx response. Reads, but does not close, the response body. */
    OpenCodeException fromResponse(String operation, Response response) {
        int status = response.code();
        String body = readBodyQuietly(response);
        String errorCode = errorCode(body);
        String requestId = response.header(HttpHeaders.REQUEST_ID);
        String message = operation + " -> HTTP " + status + (errorCode != null ? " [" + errorCode + "]" : "");
        return switch (status) {
            case 401, 403 -> new OpenCodeAuthException(message, status, errorCode, requestId, body);
            case 404 -> new OpenCodeNotFoundException(message, status, errorCode, requestId, body);
            case 409 -> new OpenCodeConflictException(message, status, errorCode, requestId, body);
            case 400, 422 -> new OpenCodeValidationException(message, status, errorCode, requestId, body);
            default -> status >= 500
                    ? new OpenCodeServerException(message, status, errorCode, requestId, body)
                    : new OpenCodeException(message, status, errorCode, requestId, body, null);
        };
    }

    /** Builds the exception for a request that produced no HTTP response. */
    OpenCodeTransportException fromIOException(String operation, IOException exception) {
        return new OpenCodeTransportException(operation + " failed: " + exception.getMessage(), exception);
    }

    private String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        String direct = firstText(root);
        if (direct != null) {
            return direct;
        }
        for (String nested : NESTED_ERROR_FIELDS) {
            JsonNode child = root.get(nested);
            if (child != null && child.isObject()) {
                String code = firstText(child);
                if (code != null) {
                    return code;
                }
            }
        }
        return null;
    }

    private static String firstText(JsonNode node) {
        for (String field : ERROR_CODE_FIELDS) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String readBodyQuietly(Response response) {
        if (response.body() == null) {
            return null;
        }
        // Peek a bounded prefix so a huge error body is never buffered whole.
        try (ResponseBody prefix = response.peekBody(MAX_BODY_CHARS * 4L)) {
            String text = prefix.string();
            return text.length() > MAX_BODY_CHARS ? text.substring(0, MAX_BODY_CHARS) : text;
        } catch (IOException e) {
            return null;
        }
    }
}
