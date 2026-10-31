package io.opencode4j.agent.runtime.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opencode4j.config.LocalServerDefaults;
import io.opencode4j.runtime.media.MediaResult;
import io.opencode4j.runtime.spec.DynamicToolSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ResultSpec;
import io.opencode4j.runtime.tool.DynamicToolProvider;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight, in-JVM Model Context Protocol (MCP) server that exposes Java methods and structured result
 * submission tools to OpenCode over loopback HTTP.
 *
 * <p>A server can serve one session (the runtime creates one per session for {@code AgentSpec} tools) or be
 * shared by many sessions and workspaces for the life of the application. For a shared server, build it with a
 * fixed {@code port} and {@code token}, reference it from workspaces with
 * {@link #asMcpToolSpec(String, String, String)} so no secret is written to disk, and pass the runtime's
 * {@link ToolCallResolver} so each call's {@link ToolContext} names the right run, root session and agent.</p>
 *
 * <p>Requests must carry {@code Authorization: Bearer <token>} (compared in constant time). Bodies above
 * {@code maxRequestBytes} are rejected, at most {@code maxConcurrentCalls} tools run at once, and a tool that
 * exceeds {@code toolTimeout} is cancelled. Tool failures reach the model as their message only, without
 * exception class names or stack traces.</p>
 */
@Slf4j
public final class JvmMcpServer implements AutoCloseable {

    /** Default maximum request body: 1 MiB. */
    public static final int DEFAULT_MAX_REQUEST_BYTES = 1 << 20;
    /** Default number of tool calls that may run at once. */
    public static final int DEFAULT_MAX_CONCURRENT_CALLS = 32;
    /** Default number of recent invocations kept for inspection. */
    public static final int DEFAULT_INVOCATION_LOG_SIZE = 1_000;

    private static final String SUBMIT_RESULT = "submit_result";
    private static final String BEARER = "Bearer ";
    private static final Duration CALL_SLOT_WAIT = Duration.ofSeconds(30);

    /** Executes one tool call. Reflective failures surface as {@link InvocationTargetException}. */
    @FunctionalInterface
    private interface ToolHandler {
        Object invoke(JsonNode arguments, ToolContext context) throws Exception;
    }

    /** {@code handler} is {@code null} only for the built-in result submission tool. */
    private record RegisteredTool(
            String name,
            String description,
            ObjectNode inputSchema,
            ToolHandler handler
    ) {}

    private static final TypeReference<Map<String, Object>> ARGUMENT_MAP = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final JsonSchemaGenerator schemaGenerator;
    private final String token;
    private final byte[] expectedAuthorization;
    private final String serverId;
    private final String path;
    private final HttpServer server;
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final int port;
    private final int maxRequestBytes;
    private final Semaphore callSlots;
    private final Duration toolTimeout;
    private final ToolCallResolver resolver;
    private final ToolOutputPolicy outputPolicy;
    private final int invocationLogSize;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private final List<DynamicToolProvider> dynamicProviders = new ArrayList<>();
    private final Map<String, DynamicToolProvider> dynamicIndex = new ConcurrentHashMap<>();
    private final ResultSpec<?> resultSpec;
    private final Map<String, Object> submittedResults = new ConcurrentHashMap<>();
    private final AtomicReference<Object> lastSubmittedResult = new AtomicReference<>();
    private final Map<String, AtomicBoolean> activeCancellations = new ConcurrentHashMap<>();
    private final ArrayDeque<ToolInvocationRecord> invocationLog = new ArrayDeque<>();

    public record ToolInvocationRecord(
            String toolName,
            String sessionId,
            String argumentsJson,
            String resultText,
            boolean isError,
            long durationMs
    ) {}

    public JvmMcpServer(
            String runId,
            List<JavaMethodToolSpec> toolSpecs,
            ResultSpec<?> resultSpec,
            ObjectMapper mapper
    ) throws IOException {
        this(runId, toolSpecs, List.of(), resultSpec, mapper);
    }

    public JvmMcpServer(
            String runId,
            List<JavaMethodToolSpec> toolSpecs,
            List<DynamicToolSpec> dynamicToolSpecs,
            ResultSpec<?> resultSpec,
            ObjectMapper mapper
    ) throws IOException {
        this(runId, toolSpecs, dynamicToolSpecs, resultSpec, mapper, null, 0, null, null, null, null, null,
                null, null, null);
    }

    /**
     * Builds and starts a server. Every setting except {@code mapper} is optional.
     *
     * @param serverId           label for logs and the default path; random if {@code null}
     * @param host               address to bind; loopback by default
     * @param port               port to bind; {@code 0} (default) picks a free port
     * @param path               endpoint path; {@code /mcp/<serverId>} by default
     * @param token              bearer token callers must present; random by default
     * @param resolver           maps calling sessions to runs and agents; by default only the session is known
     * @param outputPolicy       rewrites text output before the model sees it; unchanged by default
     * @param toolTimeout        cancels a tool that runs longer; no limit by default
     * @param maxRequestBytes    rejects larger request bodies; {@link #DEFAULT_MAX_REQUEST_BYTES} by default
     * @param maxConcurrentCalls tool calls that may run at once; {@link #DEFAULT_MAX_CONCURRENT_CALLS} by default
     * @param invocationLogSize  recent invocations kept; {@link #DEFAULT_INVOCATION_LOG_SIZE} by default
     */
    @Builder(builderMethodName = "builder")
    private JvmMcpServer(
            String serverId,
            List<JavaMethodToolSpec> javaTools,
            List<DynamicToolSpec> dynamicTools,
            ResultSpec<?> resultSpec,
            ObjectMapper mapper,
            InetAddress host,
            int port,
            String path,
            String token,
            ToolCallResolver resolver,
            ToolOutputPolicy outputPolicy,
            Duration toolTimeout,
            Integer maxRequestBytes,
            Integer maxConcurrentCalls,
            Integer invocationLogSize
    ) throws IOException {
        this.serverId = serverId != null && !serverId.isBlank() ? serverId : "tools-" + UUID.randomUUID().toString().substring(0, 8);
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.schemaGenerator = new JsonSchemaGenerator(this.mapper);
        this.token = token != null && !token.isBlank() ? token : UUID.randomUUID().toString();
        this.expectedAuthorization = (BEARER + this.token).getBytes(StandardCharsets.UTF_8);
        this.path = path != null && !path.isBlank() ? path : "/mcp/" + this.serverId;
        this.resultSpec = resultSpec;
        this.resolver = resolver != null ? resolver : ToolCallResolver.unresolved(this.serverId);
        this.outputPolicy = outputPolicy != null ? outputPolicy : ToolOutputPolicy.unchanged();
        this.toolTimeout = toolTimeout != null && !toolTimeout.isZero() && !toolTimeout.isNegative() ? toolTimeout : null;
        this.maxRequestBytes = maxRequestBytes != null && maxRequestBytes > 0 ? maxRequestBytes : DEFAULT_MAX_REQUEST_BYTES;
        this.callSlots = new Semaphore(maxConcurrentCalls != null && maxConcurrentCalls > 0
                ? maxConcurrentCalls : DEFAULT_MAX_CONCURRENT_CALLS);
        this.invocationLogSize = invocationLogSize != null && invocationLogSize >= 0
                ? invocationLogSize : DEFAULT_INVOCATION_LOG_SIZE;

        if (javaTools != null) {
            for (JavaMethodToolSpec spec : javaTools) {
                registerJavaTools(spec.instance());
            }
        }
        if (dynamicTools != null) {
            dynamicTools.forEach(spec -> dynamicProviders.add(spec.provider()));
            refreshDynamicIndex();
        }
        if (resultSpec != null) {
            registerResultTool(resultSpec);
        }

        InetAddress bindAddress = host != null ? host : InetAddress.getLoopbackAddress();
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        this.server.createContext(this.path, this::handleExchange);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
        this.port = this.server.getAddress().getPort();

        log.debug("Started in-JVM MCP server {} on port {} at {}", this.serverId, this.port, this.path);
    }

    public int port() {
        return port;
    }

    public String token() {
        return token;
    }

    /** The endpoint path, for example {@code /mcp}. */
    public String path() {
        return path;
    }

    public String url() {
        return LocalServerDefaults.HTTP_URL_PREFIX + port + path;
    }

    /**
     * The most recent structured result submitted to this server by any session.
     *
     * @deprecated a shared server serves many sessions; use {@link #takeSubmittedResult(String)}
     */
    @Deprecated(since = "0.2.0")
    public Object submittedResult() {
        return lastSubmittedResult.get();
    }

    /**
     * Removes and returns the structured result submitted in {@code rootSessionId}'s session tree, or
     * {@code null} if none was submitted since the last call.
     */
    public Object takeSubmittedResult(String rootSessionId) {
        return rootSessionId == null ? null : submittedResults.remove(rootSessionId);
    }

    /** The most recent invocations, oldest first, bounded by {@code invocationLogSize}. */
    public List<ToolInvocationRecord> invocationLog() {
        synchronized (invocationLog) {
            return List.copyOf(invocationLog);
        }
    }

    /**
     * An {@link McpToolSpec} carrying this server's URL and token literally, for a server that lives only as
     * long as the workspace it is written into.
     */
    public McpToolSpec asMcpToolSpec(String serverName) {
        return McpToolSpec.remote(
                serverName,
                "In-JVM Java tools and result collector",
                url(),
                Map.of("Authorization", BEARER + token)
        );
    }

    /**
     * An {@link McpToolSpec} that references this server through environment variables of the OpenCode
     * process, so neither the port nor the token is written to disk. The OpenCode process must see
     * {@code portVariable} set to {@link #port()} and {@code tokenVariable} set to {@link #token()}.
     */
    public McpToolSpec asMcpToolSpec(String serverName, String portVariable, String tokenVariable) {
        Objects.requireNonNull(portVariable, "portVariable");
        Objects.requireNonNull(tokenVariable, "tokenVariable");
        return McpToolSpec.remote(
                serverName,
                "In-JVM Java tools",
                LocalServerDefaults.HTTP_URL_PREFIX + "{env:" + portVariable + "}" + path,
                Map.of("Authorization", BEARER + "{env:" + tokenVariable + "}")
        );
    }

    private void registerJavaTools(Object instance) {
        Objects.requireNonNull(instance, "instance");
        Class<?> clazz = instance instanceof Class<?> c ? c : instance.getClass();
        Object target = instance instanceof Class<?> ? null : instance;

        for (Method method : clazz.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            if (target == null && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String toolName = !tool.name().isBlank() ? tool.name() : method.getName();
            ObjectNode schema = schemaGenerator.schemaForMethod(method);
            tools.put(toolName, new RegisteredTool(toolName, tool.description(), schema,
                    (arguments, context) -> invokeReflective(target, method, arguments, context)));
            log.debug("Registered in-JVM tool '{}' from method {}.{}", toolName, clazz.getSimpleName(), method.getName());
        }
    }

    private <T> void registerResultTool(ResultSpec<T> spec) {
        ObjectNode schema = schemaGenerator.schemaForType(spec.targetType());
        tools.put(SUBMIT_RESULT, new RegisteredTool(
                SUBMIT_RESULT,
                "Submit the final structured review or result matching the required schema. Call exactly once.",
                schema,
                null
        ));
        log.debug("Registered in-JVM structured result tool 'submit_result' for type {}", spec.targetType().getSimpleName());
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !MessageDigest.isEqual(expectedAuthorization, auth.getBytes(StandardCharsets.UTF_8))) {
            sendResponse(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // GET is OpenCode's SSE probe; this server answers requests directly.
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] body = readBounded(exchange.getRequestBody());
        if (body == null) {
            sendResponse(exchange, 413, "{\"error\":\"request too large\"}");
            return;
        }
        JsonNode rpc;
        try {
            rpc = mapper.readTree(body);
        } catch (IOException e) {
            sendResponse(exchange, 400, "{\"error\":\"invalid JSON\"}");
            return;
        }
        JsonNode idNode = rpc.get("id");
        String method = rpc.path("method").asText("");

        if (idNode == null) {
            // Notification (e.g. notifications/initialized, notifications/cancelled)
            if ("notifications/cancelled".equals(method)) {
                String reqId = rpc.path("params").path("requestId").asText("");
                AtomicBoolean cancelled = activeCancellations.get(reqId);
                if (cancelled != null) {
                    cancelled.set(true);
                }
            }
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", idNode);

        try {
            JsonNode result = dispatchRpc(method, rpc.path("params"), idNode.asText());
            response.set("result", result);
            sendResponse(exchange, 200, mapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("MCP request {} failed on server {}", method, serverId, e);
            ObjectNode error = response.putObject("error");
            error.put("code", -32603);
            error.put("message", "Internal error");
            sendResponse(exchange, 200, mapper.writeValueAsString(response));
        }
    }

    /** Reads at most {@code maxRequestBytes}; returns {@code null} if the body is larger. */
    private byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(maxRequestBytes + 1);
        return bytes.length > maxRequestBytes ? null : bytes;
    }

    private JsonNode dispatchRpc(String method, JsonNode params, String rpcId) throws InterruptedException {
        return switch (method) {
            case "initialize" -> handleInitialize(params);
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolCall(params, rpcId);
            default -> mapper.createObjectNode();
        };
    }

    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", params.path("protocolVersion").asText("2024-11-05"));
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo").put("name", "opencode4j-jvm-tools").put("version", "1.0");
        return result;
    }

    private JsonNode handleToolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsArray = result.putArray("tools");
        for (RegisteredTool tool : tools.values()) {
            ObjectNode t = toolsArray.addObject();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema());
        }
        refreshDynamicIndex().forEach(definition -> {
            ObjectNode t = toolsArray.addObject();
            t.put("name", definition.name());
            t.put("description", definition.description());
            t.set("inputSchema", mapper.valueToTree(definition.inputSchema()));
        });
        return result;
    }

    /**
     * Re-reads every dynamic provider's tool list and rebuilds the name index. Names that collide with a
     * static tool (or an earlier provider) are skipped with a warning.
     *
     * @return the definitions that are actually exposed
     */
    private synchronized List<DynamicToolProvider.ToolDefinition> refreshDynamicIndex() {
        Map<String, DynamicToolProvider> fresh = new LinkedHashMap<>();
        List<DynamicToolProvider.ToolDefinition> exposed = new ArrayList<>();
        for (DynamicToolProvider provider : dynamicProviders) {
            for (DynamicToolProvider.ToolDefinition definition : provider.listTools()) {
                if (tools.containsKey(definition.name()) || fresh.containsKey(definition.name())) {
                    log.warn("Ignoring dynamic tool '{}': name already registered", definition.name());
                    continue;
                }
                fresh.put(definition.name(), provider);
                exposed.add(definition);
            }
        }
        dynamicIndex.clear();
        dynamicIndex.putAll(fresh);
        return exposed;
    }

    private DynamicToolProvider findDynamicProvider(String toolName) {
        DynamicToolProvider provider = dynamicIndex.get(toolName);
        if (provider == null && !dynamicProviders.isEmpty()) {
            refreshDynamicIndex();
            provider = dynamicIndex.get(toolName);
        }
        return provider;
    }

    private Object invokeReflective(Object target, Method method, JsonNode args, ToolContext toolContext)
            throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (ToolContext.class.isAssignableFrom(param.getType())) {
                arguments[i] = toolContext;
            } else {
                Param paramAnno = param.getAnnotation(Param.class);
                String paramName = paramAnno != null && !paramAnno.name().isBlank()
                        ? paramAnno.name() : param.getName();
                arguments[i] = mapper.treeToValue(args.path(paramName), param.getType());
            }
        }
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private Object invokeDynamic(DynamicToolProvider provider, String toolName, JsonNode args,
            ToolContext toolContext) throws InvocationTargetException {
        try {
            Map<String, Object> arguments = args == null || args.isMissingNode() || args.isNull()
                    ? Map.of() : mapper.convertValue(args, ARGUMENT_MAP);
            return provider.call(toolName, arguments, toolContext);
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        }
    }

    /** Renders a tool's return value into MCP content items and returns a short log summary. */
    private String renderOutput(String toolName, ToolCaller caller, Object output, ArrayNode content) {
        if (output instanceof MediaResult media) {
            if (media.mimeType().startsWith("image/")) {
                ObjectNode item = content.addObject();
                item.put("type", "image");
                item.put("data", media.base64());
                item.put("mimeType", media.mimeType());
            } else if (!media.mimeType().startsWith("text/") || media.bytes().length > 0) {
                ObjectNode item = content.addObject();
                item.put("type", "resource");
                ObjectNode res = item.putObject("resource");
                res.put("uri", "data:" + media.mimeType() + ";base64," + media.base64());
                res.put("mimeType", media.mimeType());
                if (media.name() != null) {
                    res.put("name", media.name());
                }
            }
            if (media.text() != null && !media.text().isBlank()) {
                content.addObject().put("type", "text").put("text", outputPolicy.apply(toolName, caller, media.text()));
            }
            return (media.name() != null ? media.name() : "media")
                    + " (" + media.mimeType() + ", " + media.bytes().length + " bytes)";
        }
        String outputStr = outputPolicy.apply(toolName, caller, output != null ? String.valueOf(output) : "null");
        content.addObject().put("type", "text").put("text", outputStr);
        return outputStr;
    }

    private JsonNode handleToolCall(JsonNode params, String rpcId) throws InterruptedException {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");
        String sessionId = params.path("_meta").path("ai.opencode/sessionID").asText("");
        ToolCaller caller = resolve(sessionId);

        RegisteredTool tool = tools.get(toolName);
        DynamicToolProvider dynamicProvider = tool == null ? findDynamicProvider(toolName) : null;
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");

        if (tool == null && dynamicProvider == null) {
            return error(result, content, "Unknown tool: " + toolName);
        }
        String missing = tool != null ? missingRequiredArgument(tool.inputSchema(), args) : null;
        if (missing != null) {
            return error(result, content, "Missing required argument: " + missing);
        }
        if (!callSlots.tryAcquire(CALL_SLOT_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
            return error(result, content, "Tool server is busy; try again");
        }

        long t0 = System.currentTimeMillis();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeCancellations.put(rpcId, cancelled);

        try {
            if (SUBMIT_RESULT.equals(toolName) && resultSpec != null) {
                return submitResult(caller, args, result, content, t0);
            }

            ToolContext toolContext = new CallContext(caller, cancelled);
            Object output = invokeWithTimeout(tool, dynamicProvider, toolName, args, toolContext, cancelled);
            String summary = renderOutput(toolName, caller, output, content);
            recordInvocation(toolName, sessionId, args.toString(), summary, false, System.currentTimeMillis() - t0);
        } catch (InvocationTargetException e) {
            String errorMsg = failureMessage(e.getCause() != null ? e.getCause() : e);
            error(result, content, errorMsg);
            recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
        } catch (TimeoutException e) {
            String errorMsg = "Tool timed out after " + toolTimeout.toMillis() + " ms";
            error(result, content, errorMsg);
            recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.warn("Tool '{}' failed on server {}", toolName, serverId, e);
            String errorMsg = "Tool error: " + failureMessage(e);
            error(result, content, errorMsg);
            recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
        } finally {
            activeCancellations.remove(rpcId);
            callSlots.release();
        }

        return result;
    }

    private JsonNode submitResult(ToolCaller caller, JsonNode args, ObjectNode result, ArrayNode content, long t0) {
        try {
            Object parsed = mapper.treeToValue(args, resultSpec.targetType());
            submittedResults.put(caller.rootSessionId(), parsed);
            lastSubmittedResult.set(parsed);
            content.addObject().put("type", "text").put("text", "accepted");
            recordInvocation(SUBMIT_RESULT, caller.sessionId(), args.toString(), "accepted", false,
                    System.currentTimeMillis() - t0);
        } catch (Exception e) {
            String errorMsg = "INVALID: failed to parse structured output: " + e.getMessage();
            error(result, content, errorMsg);
            recordInvocation(SUBMIT_RESULT, caller.sessionId(), args.toString(), errorMsg, true,
                    System.currentTimeMillis() - t0);
        }
        return result;
    }

    private Object invokeWithTimeout(RegisteredTool tool, DynamicToolProvider dynamicProvider, String toolName,
                                     JsonNode args, ToolContext toolContext, AtomicBoolean cancelled)
            throws Exception {
        if (toolTimeout == null) {
            return tool != null ? tool.handler().invoke(args, toolContext)
                    : invokeDynamic(dynamicProvider, toolName, args, toolContext);
        }
        Future<Object> call = toolExecutor.submit(() -> tool != null ? tool.handler().invoke(args, toolContext)
                : invokeDynamic(dynamicProvider, toolName, args, toolContext));
        try {
            return call.get(toolTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancelled.set(true);
            call.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new InvocationTargetException(cause);
        }
    }

    private ToolCaller resolve(String sessionId) {
        try {
            ToolCaller caller = resolver.resolve(sessionId);
            if (caller != null) {
                return caller;
            }
        } catch (RuntimeException e) {
            log.warn("Tool call resolver failed for session {}: {}", sessionId, e.toString());
        }
        return new ToolCaller(serverId, sessionId, sessionId, "", "");
    }

    /** The name of the first required argument absent from {@code args}, or {@code null}. */
    private static String missingRequiredArgument(ObjectNode schema, JsonNode args) {
        if (schema == null) {
            return null;
        }
        for (JsonNode required : schema.path("required")) {
            String name = required.asText();
            JsonNode value = args == null ? null : args.get(name);
            if (value == null || value.isNull()) {
                return name;
            }
        }
        return null;
    }

    /** The model sees a failure's message only, never exception class names or stack traces. */
    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Tool failed" : message;
    }

    private static ObjectNode error(ObjectNode result, ArrayNode content, String message) {
        result.put("isError", true);
        content.addObject().put("type", "text").put("text", message);
        return result;
    }

    private void recordInvocation(String toolName, String sessionId, String args, String resultText, boolean isError, long duration) {
        if (invocationLogSize == 0) {
            return;
        }
        synchronized (invocationLog) {
            if (invocationLog.size() >= invocationLogSize) {
                invocationLog.pollFirst();
            }
            invocationLog.addLast(new ToolInvocationRecord(toolName, sessionId, args, resultText, isError, duration));
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        toolExecutor.shutdownNow();
        log.debug("Stopped in-JVM MCP server {}", serverId);
    }

    /** The {@link ToolContext} of one call. */
    private record CallContext(ToolCaller caller, AtomicBoolean cancelled) implements ToolContext {

        @Override
        public String runId() {
            return caller.runId();
        }

        @Override
        public String sessionId() {
            return caller.sessionId();
        }

        @Override
        public String rootSessionId() {
            return caller.rootSessionId();
        }

        @Override
        public String agentName() {
            return caller.agentName();
        }

        @Override
        public String agentPath() {
            return caller.agentPath();
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
