package io.opencode4j.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Decodes OpenCode v2 response bodies, which wrap their payload as {@code {"data": ...}} with optional
 * metadata such as {@code location} or {@code cursor}, while some routes answer unwrapped.
 *
 * <p>The body is parsed into a tree once. A non-null {@code data} is mapped to the target type; if that
 * fails, the whole body is mapped instead, for unwrapped payloads that happen to have a {@code data}
 * field. A {@code data: null} envelope decodes to {@code null} rather than being mapped as the payload.</p>
 */
final class EnvelopeCodec {

    static final String DATA_FIELD = "data";
    private static final Set<String> ENVELOPE_METADATA_FIELDS = Set.of(DATA_FIELD, "location", "cursor");

    private final ObjectMapper mapper;

    EnvelopeCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Parses a non-empty body into a tree. */
    JsonNode readTree(byte[] body) throws IOException {
        return mapper.readTree(body);
    }

    /** Maps a parsed body to {@code targetType}, unwrapping a {@code data} envelope if present. */
    <R> R decode(JsonNode root, JavaType targetType) throws IOException {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        if (root.isObject() && root.has(DATA_FIELD)) {
            JsonNode data = root.get(DATA_FIELD);
            if (data.isNull()) {
                if (isPureEnvelope(root)) {
                    return null;
                }
            } else {
                try {
                    return mapper.treeToValue(data, targetType);
                } catch (IOException | IllegalArgumentException ignored) {
                    // Not an envelope for this type; map the body itself below.
                }
            }
        }
        return mapper.treeToValue(root, targetType);
    }

    /** The {@code data} node of an envelope, or the root itself when the body is not enveloped. */
    JsonNode payload(JsonNode root) {
        if (root != null && root.isObject() && root.has(DATA_FIELD)) {
            return root.get(DATA_FIELD);
        }
        return root;
    }

    private static boolean isPureEnvelope(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!ENVELOPE_METADATA_FIELDS.contains(names.next())) {
                return false;
            }
        }
        return true;
    }
}
