package io.opencode4j.agent.runtime.run;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.message.AssistantMessage;
import io.opencode4j.model.message.IdleMessage;
import io.opencode4j.model.message.Message;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out how a run ended from the session history, for when stream events may have been missed.
 *
 * <p>Message ids are time-ordered, so entries created after the run's prompt sort after the prompt's inbox
 * id. The newest {@code idle} entry after the prompt records the outcome; the newest assistant text after it
 * is the final answer.</p>
 */
@Slf4j
final class RunReconciler {

    /** Enough history to cover one turn with many steps; older entries belong to earlier turns. */
    static final int PAGE_SIZE = 50;

    /**
     * The recovered end of a run.
     *
     * @param finalText the last assistant text after the prompt, or {@code null} if there is none
     */
    record Outcome(RunEnd end, String finalText) {
    }

    private final OpenCodeClient client;
    private final String rootSessionId;

    RunReconciler(OpenCodeClient client, String rootSessionId) {
        this.client = Objects.requireNonNull(client, "client");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
    }

    /**
     * Reads the session state after {@code promptMessageId}.
     *
     * @return the outcome if the session has finished processing the prompt, or {@code null} if it is still
     *         working, has not started, or the history could not be read (the caller keeps listening)
     */
    Outcome reconcile(String promptMessageId) {
        if (promptMessageId == null) {
            // The prompt has not been sent yet, so no event of this run can have been missed.
            return null;
        }
        try {
            if (client.sessions().activeSessionIds().contains(rootSessionId)) {
                log.debug("Session {} is still running; waiting for live events", rootSessionId);
                return null;
            }
            List<Message> newestFirst = client.messages().list(rootSessionId, PageRequest.first(PAGE_SIZE)).items();
            IdleMessage idle = newestFirst.stream()
                    .filter(IdleMessage.class::isInstance)
                    .map(IdleMessage.class::cast)
                    .filter(message -> after(message, promptMessageId))
                    .findFirst()
                    .orElse(null);
            if (idle == null) {
                return null;
            }
            return new Outcome(RunEnd.of(idle), lastAnswer(newestFirst, promptMessageId));
        } catch (OpenCodeException e) {
            log.warn("Could not read history of session {}: {}", rootSessionId, e.getMessage());
            return null;
        }
    }

    /**
     * The root agent's final answer: the text of the newest assistant step after the prompt. Unlike the
     * streamed text it excludes the narration of intermediate steps ("Let me check the logs...").
     *
     * @return the answer, or {@code null} if there is none or the history cannot be read
     */
    String finalText(String promptMessageId) {
        if (promptMessageId == null) {
            return null;
        }
        try {
            return lastAnswer(client.messages().list(rootSessionId, PageRequest.first(PAGE_SIZE)).items(),
                    promptMessageId);
        } catch (OpenCodeException e) {
            log.debug("Could not read the final answer of session {}: {}", rootSessionId, e.getMessage());
            return null;
        }
    }

    private static String lastAnswer(List<Message> newestFirst, String promptMessageId) {
        return newestFirst.stream()
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .filter(message -> after(message, promptMessageId) && !message.text().isBlank())
                .map(AssistantMessage::text)
                .findFirst()
                .orElse(null);
    }

    private static boolean after(Message message, String promptMessageId) {
        return message.id() != null && message.id().compareTo(promptMessageId) > 0;
    }
}
