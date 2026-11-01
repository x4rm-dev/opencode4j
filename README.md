# opencode4j

A modular Java 21 client library for driving an [`opencode serve`](https://opencode.ai/docs/server) instance programmatically and running a **full agentic loop** — send a prompt, stream the agent's output, answer its questions and permission requests, watch it call tools, and get a final result.

Built with clean separation of concerns:
- **`opencode4j-runtime`**: Transport-agnostic agent contracts, specs, sealed domain events, and in-JVM tool abstractions.
- **`opencode4j-client`**: Pure HTTP/1.1 transport and SSE event parser on OkHttp + Jackson. Zero DI container required!
- **`opencode4j-agent`**: Production `AgentRuntime` engine, in-JVM loopback MCP tool hosting, workspace management, and turnkey `OpenCode` facade.
- **`opencode4j-guice`**: Optional Google Guice integration module for teams using Guice.
- **`opencode4j-bom`**: Bill of Materials to pin all versions.

## Why this exists

OpenCode ships an official TypeScript SDK but no Java one. The one community Java SDK (`hiwepy/opencode-java-sdk`) targets the older v1.x API and provides only an untyped `{type, properties}` event wrapper with no agentic-loop runner. This library provides:

- a **typed HTTP client** covering the full server surface (sessions, prompts, questions/permissions, MCP, provider auth, files, shell, config, sync),
- a **tolerant SSE event parser** that normalizes the live `sync`-wrapped event stream into a typed event model,
- an **`AgentRuntime`** that orchestrates single, hierarchical, and parallel agent execution trees, in-JVM tools, and aggregated results.

## Maven Dependency

Import the BOM:
```xml
<dependencyManagement>
    <dependencies>
            <dependency>
                <groupId>dev.x4rm.opencode4j</groupId>
                <artifactId>opencode4j-bom</artifactId>
                <version>0.1.0-alpha.2</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
    </dependencies>
</dependencyManagement>
```

Add dependencies based on your needs:

**For full turnkey agent execution:**
```xml
<dependency>
    <groupId>dev.x4rm.opencode4j</groupId>
    <artifactId>opencode4j-agent</artifactId>
</dependency>
```

**For embedding only the typed HTTP client & SSE streaming in your own orchestrator (e.g., LangChain4j, Spring AI):**
```xml
<dependency>
    <groupId>dev.x4rm.opencode4j</groupId>
    <artifactId>opencode4j-client</artifactId>
</dependency>
```

**For Google Guice injection:**
```xml
<dependency>
    <groupId>dev.x4rm.opencode4j</groupId>
    <artifactId>opencode4j-guice</artifactId>
</dependency>
```

## Quick start

### 1. Start a server

```bash
opencode serve --port 4096
# optional: OPENCODE_SERVER_PASSWORD=secret opencode serve --port 4096
```

This library targets the **OpenCode v2 API** (`/api/*`), verified against OpenCode 2.0.20. Older servers that
only serve the v1 routes are not supported.

### 2. Run an agent with `AgentRuntime`

```java
OpenCodeConfig config = OpenCodeConfig.builder("http://localhost:4096")
        .servePassword(System.getenv("OPENCODE_SERVER_PASSWORD"))   // if the server has one
        .defaultModel("github-copilot/gpt-6.1-sol")                 // "provider/model"
        .defaultAgent("build")
        .build();

OpenCode openCode = OpenCode.create(config);

AgentSpec spec = AgentSpec.builder("fixer")
        .instruction("Fix the failing test in src/Foo.java")
        .model("github-copilot/gpt-6.1-sol")
        .permission(PermissionRule.allow("*", "*"))
        .build();

try (AgentSession session = openCode.runtime().open(spec)) {
    AgentRun run = session.send("Fix the failing test", event -> {
        if (event instanceof TextDeltaEvent delta) {
            System.out.print(delta.delta());
        }
    });

    AgentResult result = run.await();
    System.out.println(result.isSuccess() + ": " + result.text());
    System.out.println(result.tokenUsage());
}
```

Name a model explicitly. A server's default model may be one your account cannot use, which shows up as
`Provider request failed with HTTP 403`.

In v2 the agent and model belong to the **session**, not to each prompt. `openCode.runtime().open(spec)` creates the session with
them in an isolated workspace. To change them later use `client.sessions().switchAgent(...)` / `switchModel(...)`.

### 3. Or use the client directly

`OpenCodeClient` exposes one small API object per area of the server:

```java
OpenCodeClient client = OpenCodeClient.create(config);

ServerInfo info = client.server().info();                       // version, pid, urls
Session session = client.sessions().create(new CreateSessionRequest(
        "my session", "build", ModelRef.parse("github-copilot/gpt-6.1-sol"), null));

client.prompts().send(session.id(), PromptRequest.of("Hello"));  // returns once accepted

client.interactions().replyPermission(session.id(), requestId, PermissionDecision.ONCE);
client.interactions().replyForm(session.id(), formId, Map.of("q0", FormValue.of("A")));

client.sessions().interrupt(session.id());
client.sessions().delete(session.id());
```

The agent's output arrives on the event stream, which is **volatile**: events emitted while no stream is
connected are not replayed. Open the stream *before* sending the prompt:

```java
try (SseStream stream = client.events().open()) {
    client.prompts().send(session.id(), PromptRequest.of("Hello"));
    String line;
    while ((line = stream.readLine()) != null) {
        OpenCodeEvent event = SseEventParser.parseLine(line);
        if (event instanceof TextDelta delta) {
            System.out.print(delta.delta());
        } else if (event instanceof ExecutionEnded) {
            break;
        }
    }
}
```

### 4. Production Multi-Agent Runtime (`AgentRuntime`)

For production multi-agent systems, use the transport-agnostic `AgentRuntime`. Define agent hierarchies,
skills, permissions, and expose native Java methods directly as tools over an in-JVM loopback MCP server:

```java
// 1. Expose any Java method as an agent tool
public class InventoryTools {
    @Tool(description = "Checks current inventory quantity by SKU")
    public int checkStock(@Param(name = "sku") String sku, ToolContext ctx) {
        return "SKU-APPLE".equalsIgnoreCase(sku) ? 42 : 0;
    }
}

// 2. Build declarative agent tree (root + sub-agents + skills + Java tools)
AgentSpec worker = AgentSpec.builder("worker")
        .description("Word processing specialist")
        .instruction("Reverse any text given to you.")
        .build();

AgentSpec boss = AgentSpec.builder("coordinator")
        .description("Store coordinator")
        .instruction("Answer store questions with checkStock tool; delegate text tasks to worker.")
        .javaTools(new InventoryTools())
        .skill(SkillSpec.always("store-policy", "Store policies", "Always end with 'HAVE A NICE DAY!'"))
        .subAgent(worker)
        .permission(PermissionRule.allow("*", "*"))
        .build();

// 3. Open session and stream path-tagged domain events
OpenCode openCode = OpenCode.create(config);
try (AgentSession session = openCode.runtime().open(boss)) {
    AgentRun run = session.send("Check stock for SKU-APPLE and reverse 'KOTLIN'", event -> {
        if (event instanceof TextDeltaEvent delta) {
            System.out.print(delta.delta());
        } else if (event instanceof ToolCallCompletedEvent tc) {
            System.out.println("Tool executed: " + tc.toolName() + " by " + tc.agentPath());
        } else if (event instanceof DelegationStartedEvent ds) {
            System.out.println("Delegating to " + ds.targetAgentName());
        }
    });

    AgentResult result = run.await();
    System.out.println("Success: " + result.isSuccess());
    System.out.println("Cost: $" + result.totalCost());
    System.out.println("Tokens: " + result.tokenUsage().total());
}
```

Each session runs in its own isolated workspace with generated configuration, automatic location warmup,
derived subagent depth, and automatic MCP tool unmasking.

### 4. Try the bundled examples

The `opencode4j-examples` module provides ready-to-run examples demonstrating various agent architectures:

```bash
# 1. Simple inference (single agent streaming text deltas)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.SimpleInferenceExample"

# 2. Native Java tools (in-JVM loopback MCP server with @Tool and @Param)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.AgentWithToolsExample"

# 3. Hierarchical delegation (director -> lead_analyst -> bullet_summarizer with auto-derived depth)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.HierarchicalAgentExample"

# 4. Multi-turn conversation (retaining context across sequential turns on the same session)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.MultiTurnChatExample"

# 5. Strongly-typed structured results (auto-deployed submit_result schema binding to a Java record)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.StructuredResultExample"

# 6. Skills, resource limits, and human-in-the-loop approval policies
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.AgentWithSkillsAndLimitsExample"

# 7. Multimodal prompts and media tools (analyzing images and tools returning MediaResult)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.MultimodalPromptExample"

# 8. A read-only service agent in a shared release workspace (validation, shared tool server, slow listener)
mvn -pl opencode4j-examples compile exec:java -Dexec.mainClass="io.opencode4j.examples.ReleaseWorkspaceExample"
```

*(Each example automatically connects to `OPENCODE_SERVER_URL` if set, or auto-launches an isolated local server instance if unset.)*

## Running opencode4j in a service

The single-session `runtime().open(AgentSpec)` above writes a private workspace per session. A long-running
service (for example a Slack bot serving many threads) should instead materialize one workspace per release of
its agent configuration and open every conversation in it. Everything below was verified against opencode 2.2.0.

### Release workspaces

```java
OpenCodeClient client = OpenCodeClient.builder(OpenCodeConfig.builder("http://127.0.0.1:4096")
                .servePassword(System.getenv("OPENCODE_SERVER_PASSWORD")).build())
        .metrics(clientMetrics)                  // optional, see Observability
        .build();
OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.builder(client)
        .baseWorkspacesDir(Path.of("/data/workspaces"))
        .build();

// One long-lived tool server for the whole application, reached through {env:} references.
JvmMcpServer tools = JvmMcpServer.builder()
        .serverId("alfred").path("/mcp")
        .port(Integer.parseInt(System.getenv("ALFRED_MCP_PORT")))
        .token(System.getenv("ALFRED_MCP_TOKEN"))
        .javaTools(List.of(JavaMethodToolSpec.of(new CatalogTools())))
        .resolver(runtime.toolCallResolver())    // ToolContext then names run, root session and agent
        .build();

ProviderSpec godric = ProviderSpec.builder("godric")
        .baseUrl("http://127.0.0.1:{env:GODRIC_GATEWAY_PORT}/v1")
        .apiKey(SecretRef.env("GODRIC_GATEWAY_TOKEN"))
        .model(ProviderSpec.ModelSpec.toolCalling("global:LLM_ROOT", 200_000, 32_000).withCost(0.5, 1.5))
        .build();

AgentSpec debugger = AgentSpec.builder("sre-debugger")
        .model("godric", "global:LLM_ROOT")
        .instruction("{file:.opencode/prompts/sre-debugger.md}")
        .permission(PermissionRule.allow("alfred_*", "*"))
        .permission(PermissionRule.allow("victorialogs_*", "*"))
        .build();

Workspace release = runtime.materialize(WorkspaceSpec.builder("debugging/rel_0042", debugger)
        .provider(godric)
        .mcpServer(tools.asMcpToolSpec("alfred", "ALFRED_MCP_PORT", "ALFRED_MCP_TOKEN"))
        .mcpServer(McpToolSpec.remote("victorialogs", "logs", "{env:VL_MCP_URL}",
                Map.of("Authorization", "{env:VL_MCP_AUTH}")))
        .file(".opencode/prompts/sre-debugger.md", prompt)
        .instruction("timezone.md", "Times are in IST unless stated otherwise.")
        .smallModel("godric/global:LLM_SMALL")
        .policy(PolicyRule.denyTool("shell"))
        .enabledProvider("godric")
        .build());
runtime.validate(release).requireValid();       // before routing traffic to the release

try (AgentSession conversation = runtime.open(release, SessionOptions.builder()
        .title("Theo timeouts").metadata("conversationId", conversationId).build())) {
    AgentResult result = conversation.send("Any Aerospike timeouts on Theo between 15:30 and 15:40?").await();
}
```

- `materialize` writes the files once (content-hashed: an identical spec is a no-op), with owner-only permissions,
  and waits until the agents are registered and the MCP servers connected. A changed spec under an id that still
  has open sessions is rejected; publish it under a new id.
- `close()` detaches: the server session survives and `runtime.resume(release, sessionId, options)` reattaches,
  for example after a restart. `delete()` removes it. Nothing evicts the location until `release.retire()`.
- A session runs one prompt at a time (`SessionBusyException` otherwise). After a cancelled turn the next run first
  waits for the server to finish it.

### Permissions are deny-by-default

A `WorkspaceSpec` starts every agent's rules with `"*": "deny"`, so an agent gets only what it allows. opencode
applies workspace-level rules after agent rules, so opencode4j places the workspace rules at the start of each
agent's rule list instead of at the root. Tools a rule denies outright are not offered to the model at all. MCP
tools are checked as `<server>_<tool>`. opencode normally reaches them through its `execute` tool (Code Mode);
when `execute` is denied, as it is under deny-all, allowed MCP tools are offered to the model directly.
`runtime.validate(...)` reads the effective rules back and reports any agent that could use `shell`, `execute`,
`edit`, `write`, `webfetch`, `websearch` or `question` without allowing it explicitly.

### Secrets

Provider keys, provider headers and MCP headers are written as `{env:NAME}` / `{file:path}` references
(`SecretRef.env(...)`, `SecretRef.file(...)`) that opencode resolves; a `WorkspaceSpec` refuses literal credentials
unless `allowInlineSecrets(true)` is set for local development. opencode returns the *resolved* values from
`/api/provider`, `/api/model` and `/api/config`, so the server password protects every secret: keep opencode on
loopback (the client refuses to send its password over plain HTTP to another host unless
`allowInsecureRemote(true)`), and never log those responses.

### The event stream

`client.eventBus()` holds one `/api/event` connection for all runs and routes events by session tree. Each
subscriber has its own bounded queue and thread, so a slow consumer (say, a rate-limited chat update) delays
only itself; under pressure text deltas are merged or shed, never lifecycle events. opencode does not replay
events after a reconnect, so every reconnect is a gap: runs then read the session history to find out whether the
turn ended and what the answer was. The final answer of a successful run is the last assistant step from history,
without the narration of intermediate steps.

| opencode event | `AgentEvent` |
|---|---|
| `session.text.delta` / `session.reasoning.delta` | `TextDeltaEvent` / `ReasoningDeltaEvent` |
| `session.step.started` / `session.step.ended`, `session.step.failed` | `StepStartedEvent` / `StepEndedEvent` |
| `session.tool.called` / `session.tool.success` / `session.tool.failed` | `ToolCallStartedEvent` / `ToolCallCompletedEvent` / `ToolCallFailedEvent` |
| `subagent` tool calls | `DelegationStartedEvent` / `DelegationCompletedEvent` |
| `permission.asked` | `PermissionRequestedEvent` |
| `session.execution.succeeded` / `failed` / `interrupted` of the root session | `RunCompletedEvent` / `RunFailedEvent` / `RunInterruptedEvent` |

### Run outcomes

`AgentResult.status()` is `SUCCEEDED`, `FAILED`, `CANCELLED` (the caller cancelled) or `PARTIAL` (a budget or the
wall-clock timeout stopped the run; text and accounting so far are kept). Failures carry a `FailureCategory`
derived from the server's error type: `LLM_AUTH`, `LLM_RATE_LIMITED` (with `retryAfterMs`), `LLM_QUOTA`,
`LLM_CONTEXT_OVERFLOW`, `LLM_PROVIDER`, `PERMISSION_DENIED`, `EXECUTION`, `TRANSPORT`, `SESSION_BUSY`.

### Observability

- `ClientMetrics` (on `OpenCodeClient.builder`): HTTP requests by route, stream state, gaps, unknown event types
  (protocol drift), shed events. `RequestHeaders` adds headers such as `traceparent` to every request.
- `RuntimeMetrics` (on `OpenCodeAgentRuntime.builder` and `JvmMcpServer.builder`): a `RunSummary` per run (status,
  category, duration, tokens, cost, steps, tool calls) and every in-JVM tool call.
- Log lines written while a run's events or tool calls are handled carry `ocRunId` and `ocSessionId` in the MDC.
- A `TextRedactor` on the runtime builder masks event payloads and result text before they leave the runtime.

All of them are plain interfaces with no-op defaults; bind them to Micrometer, Dropwizard Metrics or your logger.

### Compatibility and testing

opencode4j is tested against opencode `>=2.2.0 <2.3.0` (`ServerCompatibility`). The runtime checks the server
version before its first workspace and logs a warning outside that range; `versionPolicy(FAIL)` refuses instead.
`opencode4j-testkit` provides `OpenCodeTestServer` (a real `opencode serve` in a clean room) and `FakeLlmServer`
(a scriptable OpenAI-compatible model) for contract tests of your own agents.

### Troubleshooting

- *The first turn has no MCP tools*: the MCP server was not reachable when the workspace was materialized;
  `runtime.validate(...)` lists it under warnings. Check that the opencode process sees the `{env:}` variables.
- *An allowed MCP tool is missing*: rules match `<server>_<tool>`, for example `alfred_*` for server `alfred`.
- *Literal credential rejected*: pass a `SecretRef`, or `allowInlineSecrets(true)` for local development.
- *Run ended `FAILED` with `TRANSPORT`*: the event stream could not reconnect within `sseMaxRetries`; the session
  history still holds the outcome.

## Files, MCP servers and configuration

Each takes an optional `directory` (the *location*); `null` uses the server's default location.

```java
// Files: paths are relative to the location. Read returns raw bytes, so binary files are safe.
List<FileEntry> entries = client.files().list("src", null);                  // directories end with "/"
List<FileEntry> hits    = client.files().find("EventApi", FileType.FILE, 10, null);
byte[] bytes            = client.files().read("pom.xml", null);
String text             = client.files().readText("README.md", null);
client.files().writeText("out/notes.txt", "hello", null);                    // experimental; creates parents
```

Reads are confined to the location, so absolute paths and `.` / `..` segments are rejected before anything is
sent. **Writes are not confined**: an absolute path writes anywhere the server process can.

```java
// MCP servers
for (McpServer server : client.mcp().list(null)) {
    switch (server.status()) {
        case McpConnected c -> System.out.println(server.name() + " ready");
        case McpFailed f    -> System.out.println(server.name() + " failed: " + f.error());
        default             -> System.out.println(server.name() + " " + server.status());
    }
}
client.mcp().put("docs", McpServerConfig.remote("https://mcp.example.com"), null);   // experimental
client.mcp().connect("docs", null);

// Configuration is read-mostly in v2
for (ConfigEntry entry : client.config().entries(null)) {
    if (entry instanceof ConfigDocument document) {
        System.out.println(document.path() + " default agent: " + document.info().defaultAgent());
    }
}
client.config().setShell("/bin/zsh");   // the only setting the API can change; experimental
```

`ConfigInfo` exposes the commonly used settings (model, default agent, agents, commands, permissions, MCP
servers and so on). Settings it does not model, such as provider and model definitions, are ignored. In v2 you
connect providers through `client.integrations()`; the API cannot edit provider configuration.

## Server authentication

If the server was started with `OPENCODE_SERVER_PASSWORD`, every request needs HTTP basic auth:

```java
OpenCodeConfig config = OpenCodeConfig.builder("http://localhost:4096")
        .servePassword(System.getenv("OPENCODE_SERVER_PASSWORD"))
        .serveUsername(System.getenv("OPENCODE_SERVER_USERNAME"))   // optional, defaults to "opencode"
        .build();
```

## Integrations and credentials (v2)

`client.integrations()` lists integrations and connects them; `client.credentials()` manages the stored
credentials. Every integration operation takes an optional `directory` (sent as `location[directory]`);
credentials are global and take none.

```java
OpenCodeClient client = OpenCodeClient.create(config);

// Discover integrations and how each can be connected
for (Integration integration : client.integrations().list(null)) {
    System.out.println(integration.id() + " methods=" + integration.methods());
}

// API key
client.integrations().connectKey("github", ConnectKeyRequest.of("ghp_..."), null);

// OAuth: open attempt.url() for the user, then complete (code mode) or poll (auto mode)
OAuthAttempt attempt = client.integrations()
        .connectOAuth("github", ConnectOAuthRequest.of("oauth"), null);
if (attempt.mode() == OAuthMode.CODE) {
    client.integrations().completeOAuth("github", attempt.attemptID(), CompleteOAuthRequest.of(pastedCode), null);
} else {
    AttemptStatus status = client.integrations().oauthStatus("github", attempt.attemptID(), null);
}

// Stored credentials (IDs come from CredentialConnection.id() on an integration)
client.credentials().rename(credentialId, RenameCredentialRequest.of("Work account"));
client.credentials().activate(credentialId);
client.credentials().remove(credentialId);
```

Method, connection and form-field types are sealed interfaces with an `Unknown*` variant, so a newer server
adding a type never breaks deserialization.

> **Status:** these endpoints are implemented from the OpenAPI spec and verified against a mock server.
> They have not yet been exercised against a live authenticated server.

## Layout

```
opencode4j
├── opencode4j-bom             (BOM dependency management)
├── opencode4j-runtime         (Transport-agnostic specs, sealed events, tools, and runtime contracts)
│   ├── spec.AgentSpec         (Declarative hierarchical agent tree, limits, permissions)
│   ├── event.AgentEvent       (Sealed path-tagged event model: TextDelta, ToolCall, Delegation, Step)
│   ├── tool.*                 (@Tool, @Param, ToolContext)
│   └── AgentRuntime, AgentSession, AgentRun, AgentResult
├── opencode4j-client          (HTTP/1.1 transport, SSE parser, models, config)
│   ├── OpenCodeException
│   ├── config.OpenCodeConfig
│   ├── client.OpenCodeClient  (+ SseStream, RequestHeaders, ClientMetrics, ServerCompatibility)
│   ├── client.event           (EventBus: shared, self-healing event stream)
│   ├── event.SseEventParser
│   ├── event.OpenCodeEvent    (sealed low-level event hierarchy)
│   └── model.*                 (standalone API records)
├── opencode4j-agent           (OpenCode AgentRuntime, in-JVM MCP tool hosting, workspace management)
│   ├── OpenCode               (Unified facade: client(), runtime())
│   ├── agent.runtime.OpenCodeAgentRuntime, OpenCodeAgentSession
│   ├── agent.runtime.run     (OpenCodeAgentRun and package-private run helpers)
│   ├── agent.runtime.workspace (WorkspaceWriter, WorkspaceConfiguration, LocationManager)
│   ├── agent.runtime.mcp     (JvmMcpServer, ToolCallResolver, ToolOutputPolicy, JsonSchemaGenerator)
│   ├── agent.runtime.observe (RuntimeMetrics, RunSummary)
│   └── agent.runtime.server  (OpenCodeServerLauncher, OpenCodeServerInstance)
├── opencode4j-guice           (Optional Google Guice module)
│   └── guice.OpenCodeModule
├── opencode4j-testkit         (OpenCodeTestServer, FakeLlmServer, and the opencode contract suite)
└── opencode4j-examples        (Runnable examples: RunPrompt, RunAgentRuntimeExample)
```

## Build & test

```bash
mvn test                             # Unit tests, plus the contract suite when an opencode binary is installed
mvn test -DexcludedGroups=contract   # Unit tests only
mvn package                          # Builds reactor jars
```

The contract suite (`opencode4j-testkit`, tag `contract`) launches the `opencode` binary from `OPENCODE_BIN` or
the `PATH` in a clean room and drives it with a scripted model; it needs no network or credentials.

## For contributors

Lombok annotation processing is configured explicitly in Maven. Enable Lombok annotation processing in your IDE so generated accessors are visible during editing.
