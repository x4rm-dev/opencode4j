package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import java.util.Objects;

/** Request body for answering a form; {@code answer} maps each field key to its value. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormReplyRequest(
        Map<String, FormValue> answer
) {

    public FormReplyRequest {
        Objects.requireNonNull(answer, "answer");
    }
}
