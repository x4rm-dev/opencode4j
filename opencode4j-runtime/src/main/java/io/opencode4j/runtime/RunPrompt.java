package io.opencode4j.runtime;

import io.opencode4j.runtime.media.MediaAttachment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt turn input sent to an {@link AgentSession}.
 *
 * @param text        prompt text sent to the agent
 * @param attachments multimodal attachments (images, audio, documents)
 * @param useSkills   specific registered skills to activate for this turn (via prompt attachments)
 * @param delegateTo  specific registered sub-agent to steer delegation to (via prompt attachments)
 * @param metadata    optional correlation metadata (e.g. run id, tenant)
 */
public record RunPrompt(
        String text,
        List<MediaAttachment> attachments,
        List<String> useSkills,
        String delegateTo,
        Map<String, Object> metadata
) {

    public RunPrompt {
        Objects.requireNonNull(text, "text");
        attachments = attachments == null || attachments.isEmpty() ? List.of() : List.copyOf(attachments);
        useSkills = useSkills == null || useSkills.isEmpty() ? List.of() : List.copyOf(useSkills);
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    /** Backward-compatible 4-argument constructor without attachments. */
    public RunPrompt(
            String text,
            List<String> useSkills,
            String delegateTo,
            Map<String, Object> metadata
    ) {
        this(text, List.of(), useSkills, delegateTo, metadata);
    }

    public static RunPrompt of(String text) {
        return new RunPrompt(text, List.of(), List.of(), null, Map.of());
    }

    public static RunPrompt of(String text, List<String> useSkills) {
        return new RunPrompt(text, List.of(), useSkills, null, Map.of());
    }

    public static RunPrompt of(String text, String delegateTo) {
        return new RunPrompt(text, List.of(), List.of(), delegateTo, Map.of());
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private final String text;
        private final List<MediaAttachment> attachments = new ArrayList<>();
        private final List<String> useSkills = new ArrayList<>();
        private String delegateTo;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        public Builder attach(MediaAttachment attachment) {
            if (attachment != null) {
                this.attachments.add(attachment);
            }
            return this;
        }

        public Builder attachments(List<MediaAttachment> attachments) {
            if (attachments != null) {
                this.attachments.addAll(attachments);
            }
            return this;
        }

        public Builder useSkill(String skillName) {
            if (skillName != null && !skillName.isBlank()) {
                this.useSkills.add(skillName);
            }
            return this;
        }

        public Builder useSkills(List<String> skills) {
            if (skills != null) {
                this.useSkills.addAll(skills);
            }
            return this;
        }

        public Builder delegateTo(String subAgentName) {
            this.delegateTo = subAgentName;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> meta) {
            if (meta != null) {
                this.metadata.putAll(meta);
            }
            return this;
        }

        public RunPrompt build() {
            return new RunPrompt(text, attachments, useSkills, delegateTo, metadata);
        }
    }
}
