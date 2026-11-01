package io.opencode4j.runtime.spec;

/** Validation of {@code provider/model} references. */
final class ModelRefs {

    private ModelRefs() {
    }

    /**
     * Checks that {@code value}, when present, is {@code provider/model}: everything before the first slash is the
     * provider id, everything after it the model id, which may contain further slashes and colons, as in
     * {@code godric/global:LLM_GLOBAL_X}.
     */
    static void requireValid(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            throw new IllegalArgumentException(field + " must be \"provider/model\" but was: " + value);
        }
    }
}
