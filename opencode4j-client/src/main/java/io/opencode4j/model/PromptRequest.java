package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A prompt to add to a session.
 *
 * <p>The agent and model are not part of a prompt in the v2 API; set them when creating the session or
 * switch them with {@code SessionApi.switchAgent} / {@code switchModel}.</p>
 *
 * @param delivery how to deliver it if the session is busy; {@code null} lets the server decide
 * @param resume   {@code false} stores the prompt without starting a turn; {@code null} lets the server decide
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptRequest(
        String id,
        String text,
        List<FileAttachment> files,
        List<PromptAgentAttachment> agents,
        List<PromptSkillAttachment> skills,
        Map<String, Object> metadata,
        Delivery delivery,
        Boolean resume
) {

    public PromptRequest {
        Objects.requireNonNull(text, "text");
        files = files == null || files.isEmpty() ? null : List.copyOf(files);
        agents = agents == null || agents.isEmpty() ? null : List.copyOf(agents);
        skills = skills == null || skills.isEmpty() ? null : List.copyOf(skills);
        metadata = metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata);
    }

    /** Backward-compatible 4-argument constructor. */
    public PromptRequest(
            String text,
            List<FileAttachment> files,
            Delivery delivery,
            Boolean resume
    ) {
        this(null, text, files, null, null, null, delivery, resume);
    }

    public static PromptRequest of(String text) {
        return new PromptRequest(null, text, null, null, null, null, null, null);
    }

    public static PromptRequest of(String text, List<FileAttachment> files) {
        return new PromptRequest(null, text, files, null, null, null, null, null);
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private String id;
        private final String text;
        private List<FileAttachment> files;
        private List<PromptAgentAttachment> agents;
        private List<PromptSkillAttachment> skills;
        private Map<String, Object> metadata;
        private Delivery delivery;
        private Boolean resume;

        public Builder(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder files(List<FileAttachment> files) {
            this.files = files == null ? null : new ArrayList<>(files);
            return this;
        }

        public Builder agents(List<PromptAgentAttachment> agents) {
            this.agents = agents == null ? null : new ArrayList<>(agents);
            return this;
        }

        public Builder skills(List<PromptSkillAttachment> skills) {
            this.skills = skills == null ? null : new ArrayList<>(skills);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? null : new LinkedHashMap<>(metadata);
            return this;
        }

        public Builder delivery(Delivery delivery) {
            this.delivery = delivery;
            return this;
        }

        public Builder resume(Boolean resume) {
            this.resume = resume;
            return this;
        }

        public PromptRequest build() {
            return new PromptRequest(id, text, files, agents, skills, metadata, delivery, resume);
        }
    }
}
