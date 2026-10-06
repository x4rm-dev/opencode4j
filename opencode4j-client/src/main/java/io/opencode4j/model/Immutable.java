package io.opencode4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Null-safe immutable copies for the collection components of response records. */
public final class Immutable {

    private Immutable() {
    }

    /** An unmodifiable copy of {@code values}, or an empty list when it is {@code null}. */
    public static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * An unmodifiable, insertion-ordered copy of {@code values}, or an empty map when it is {@code null}.
     *
     * <p>Unlike {@link Map#copyOf(Map)} this tolerates {@code null} values, which a JSON object may contain.</p>
     */
    public static <K, V> Map<K, V> map(Map<K, V> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
