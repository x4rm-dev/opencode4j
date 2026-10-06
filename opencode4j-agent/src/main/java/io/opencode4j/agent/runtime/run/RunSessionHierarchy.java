package io.opencode4j.agent.runtime.run;

import io.opencode4j.event.SessionCreated;
import java.util.HashMap;
import java.util.Map;

/** Session identities belonging to one run; accessed under the run's state lock. */
final class RunSessionHierarchy {
    record Identity(String name, String path) { }

    private final Map<String, Identity> sessions = new HashMap<>();

    RunSessionHierarchy(String rootSessionId, String agentName) {
        sessions.put(rootSessionId, new Identity(agentName, agentName));
    }

    void register(SessionCreated created) {
        if (created.sessionId() == null || sessions.containsKey(created.sessionId())) {
            return;
        }
        Identity parent = sessions.get(created.parentId());
        if (parent != null) {
            String name = created.title() != null && !created.title().isBlank() ? created.title() : "subagent";
            sessions.put(created.sessionId(), new Identity(name, parent.path() + "/" + name));
        }
    }

    Identity get(String sessionId) {
        return sessions.get(sessionId);
    }
}
