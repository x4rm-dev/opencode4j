package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Creation and expiry timestamps of an attempt, in epoch milliseconds.
 *
 * <p>The wire schema also permits the strings {@code "Infinity"}, {@code "-Infinity"} and
 * {@code "NaN"}; Jackson maps these to the corresponding {@code double} values.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttemptTime(
        double created,
        double expires
) {
}
