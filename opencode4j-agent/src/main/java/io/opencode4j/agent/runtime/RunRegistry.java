package io.opencode4j.agent.runtime;

import io.opencode4j.agent.runtime.mcp.ToolCallResolver;
import io.opencode4j.agent.runtime.mcp.ToolCaller;
import io.opencode4j.agent.runtime.run.OpenCodeAgentRun;
import io.opencode4j.agent.runtime.run.RunIdentity;
import io.opencode4j.client.event.SessionTree;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The runs in progress, by root session, so an in-JVM tool call can be traced from the calling session (possibly
 * a sub-agent's child session) to its run and agent.
 */
final class RunRegistry implements ToolCallResolver {

    private final Map<String, OpenCodeAgentRun> byRootSession = new ConcurrentHashMap<>();
    private final Supplier<SessionTree> sessionTree;

    RunRegistry(Supplier<SessionTree> sessionTree) {
        this.sessionTree = Objects.requireNonNull(sessionTree, "sessionTree");
    }

    void register(OpenCodeAgentRun run) {
        byRootSession.put(run.sessionId(), run);
    }

    void unregister(OpenCodeAgentRun run) {
        byRootSession.remove(run.sessionId(), run);
    }

    int activeCount() {
        return byRootSession.size();
    }

    @Override
    public ToolCaller resolve(String sessionId) {
        String root = sessionTree.get().rootOf(sessionId);
        OpenCodeAgentRun run = root == null ? null : byRootSession.get(root);
        RunIdentity identity = run == null ? null : run.identityOf(sessionId);
        if (identity == null) {
            return new ToolCaller(run != null ? run.runId() : "", sessionId, root != null ? root : sessionId, "", "");
        }
        return new ToolCaller(identity.runId(), sessionId, identity.rootSessionId(), identity.agentName(),
                identity.agentPath());
    }
}
