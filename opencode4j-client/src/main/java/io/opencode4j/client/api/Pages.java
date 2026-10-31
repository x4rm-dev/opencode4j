package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.Cursor;
import io.opencode4j.model.Page;
import io.opencode4j.model.PageRequest;
import java.util.List;

/** Reads {@code {"data": [...], "cursor": {"next", "previous"}}} listings. */
final class Pages {

    private Pages() {
    }

    /** Appends {@code limit}, {@code order} and {@code cursor} query parameters for {@code request}. */
    static String withPage(OpenCodeTransport transport, String path, PageRequest request) {
        if (request == null) {
            return path;
        }
        StringBuilder url = new StringBuilder(path);
        if (request.limit() != null) {
            append(url, "limit", String.valueOf(request.limit()), transport);
        }
        if (request.order() != null) {
            append(url, "order", request.order().wire(), transport);
        }
        if (request.cursor() != null) {
            append(url, "cursor", request.cursor().value(), transport);
        }
        return url.toString();
    }

    static void append(StringBuilder url, String name, String value, OpenCodeTransport transport) {
        url.append(url.indexOf("?") < 0 ? '?' : '&').append(name).append('=').append(transport.enc(value));
    }

    /** GETs {@code path} and decodes a page of {@code itemType}. */
    static <T> Page<T> get(OpenCodeTransport transport, String path, TypeReference<List<T>> itemType) {
        JsonNode root = transport.sendForTree(HttpMethod.GET, path, null);
        if (root == null) {
            return new Page<>(List.of(), null, null);
        }
        List<T> items = transport.decode(root, itemType);
        JsonNode cursor = root.path("cursor");
        return new Page<>(items,
                Cursor.ofNullable(text(cursor, "next")),
                Cursor.ofNullable(text(cursor, "previous")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
