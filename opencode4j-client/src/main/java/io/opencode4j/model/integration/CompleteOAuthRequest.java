package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to complete an OAuth attempt.
 *
 * <p>{@code code} is the code the user pasted for {@link OAuthMode#CODE} attempts and is absent
 * for {@link OAuthMode#AUTO} attempts, which serialize to an empty JSON object.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompleteOAuthRequest(
        String code
) {

    public static CompleteOAuthRequest of(String code) {
        return new CompleteOAuthRequest(code);
    }

    public static CompleteOAuthRequest withoutCode() {
        return new CompleteOAuthRequest(null);
    }
}
