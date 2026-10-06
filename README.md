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
                <version>0.1.0-alpha.1</version>
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
```

*(Each example automatically connects to `OPENCODE_SERVER_URL` if set, or auto-launches an isolated local server instance if unset.)*

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
│   ├── client.OpenCodeClient  (+ SseStream)
│   ├── event.SseEventParser
│   ├── event.OpenCodeEvent    (sealed low-level event hierarchy)
│   └── model.*                 (standalone API records)
├── opencode4j-agent           (OpenCode AgentRuntime, in-JVM MCP tool hosting, workspace management)
│   ├── OpenCode               (Unified facade: client(), runtime())
│   ├── agent.runtime.OpenCodeAgentRuntime, OpenCodeAgentSession
│   ├── agent.runtime.run     (OpenCodeAgentRun and package-private run helpers)
│   ├── agent.runtime.workspace (WorkspaceWriter, WorkspaceConfiguration, LocationManager)
│   ├── agent.runtime.mcp     (JvmMcpServer, JsonSchemaGenerator)
│   └── agent.runtime.server  (OpenCodeServerLauncher, OpenCodeServerInstance)
├── opencode4j-guice           (Optional Google Guice module)
│   └── guice.OpenCodeModule
└── opencode4j-examples        (Runnable examples: RunPrompt, RunAgentRuntimeExample)
```

## Build & test

```bash
mvn test          # Unit tests across all modules
mvn package       # Builds reactor jars
```

## For contributors

Lombok annotation processing is configured explicitly in Maven. Enable Lombok annotation processing in your IDE so generated accessors are visible during editing.
