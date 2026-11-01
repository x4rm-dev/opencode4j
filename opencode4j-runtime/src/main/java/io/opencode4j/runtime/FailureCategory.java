package io.opencode4j.runtime;

/** Why a run did not succeed, coarse enough to choose a user-facing reply or a retry policy. */
public enum FailureCategory {
    /** The LLM provider rejected the credentials ({@code provider.auth}). */
    LLM_AUTH,
    /** The provider rate-limited the request ({@code provider.rate-limit}); see {@link AgentResult#retryAfterMs()}. */
    LLM_RATE_LIMITED,
    /** A usage quota or spend limit is exhausted ({@code provider.quota} and similar). */
    LLM_QUOTA,
    /** The conversation no longer fits the model's context window. */
    LLM_CONTEXT_OVERFLOW,
    /** Any other provider failure: timeout, transport, invalid output, content filter. */
    LLM_PROVIDER,
    /** A permission request was rejected, which ends the whole execution. */
    PERMISSION_DENIED,
    /** The agent's execution failed for another reason reported by the server. */
    EXECUTION,
    /** The event stream or HTTP calls to the server failed. */
    TRANSPORT,
    /** A token or cost budget was exhausted. */
    BUDGET,
    /** The wall-clock timeout elapsed. */
    TIMEOUT,
    /** The server was still running a previous turn of the session. */
    SESSION_BUSY;

    /**
     * Maps a server error type such as {@code provider.rate-limit} to a category.
     *
     * @param type the {@code Session.StructuredError} type, or {@code null}
     */
    public static FailureCategory fromErrorType(String type, Integer httpStatus) {
        String value = type == null ? "" : type;
        if (value.equals("provider.auth") || (value.startsWith("provider.")
                && httpStatus != null && (httpStatus == 401 || httpStatus == 403))) {
            return LLM_AUTH;
        }
        if (value.equals("provider.rate-limit") || (httpStatus != null && httpStatus == 429)) {
            return LLM_RATE_LIMITED;
        }
        if (value.equals("provider.quota") || (value.startsWith("provider.") && value.endsWith("-limit"))) {
            return LLM_QUOTA;
        }
        if (value.contains("context")) {
            return LLM_CONTEXT_OVERFLOW;
        }
        if (value.startsWith("provider.")) {
            return LLM_PROVIDER;
        }
        if (value.equals("permission.rejected")) {
            return PERMISSION_DENIED;
        }
        return EXECUTION;
    }
}
