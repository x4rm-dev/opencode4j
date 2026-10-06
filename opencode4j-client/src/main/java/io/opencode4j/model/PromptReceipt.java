package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Acknowledgement that the server accepted a prompt into the session inbox. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptReceipt(
        String id,
        String sessionID,
        Delivery delivery
) {
}
