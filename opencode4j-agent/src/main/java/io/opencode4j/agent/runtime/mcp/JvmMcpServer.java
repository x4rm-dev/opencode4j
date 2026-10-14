package io.opencode4j.agent.runtime.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opencode4j.config.LocalServerDefaults;
import io.opencode4j.runtime.spec.DynamicToolSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ResultSpec;
import io.opencode4j.runtime.media.MediaResult;
import io.opencode4j.runtime.tool.DynamicToolProvider;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight, in-JVM Model Context Protocol (MCP) server that exposes Java methods
 * and structured result submission tools to OpenCode over loopback HTTP.
 */
public final class JvmMcpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JvmMcpServer.class);

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
    private final String runId;
    private final HttpServer server;
    private final int port;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private final List<DynamicToolProvider> dynamicProviders = new ArrayList<>();
    private final Map<String, DynamicToolProvider> dynamicIndex = new ConcurrentHashMap<>();
    private final ResultSpec<?> resultSpec;
    private final AtomicReference<Object> submittedResult = new AtomicReference<>();
    private final Map<String, AtomicBoolean> activeCancellations = new ConcurrentHashMap<>();
    private final List<ToolInvocationRecord> invocationLog = Collections.synchronizedList(new ArrayList<>());

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
        this.runId = Objects.requireNonNull(runId, "runId");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.schemaGenerator = new JsonSchemaGenerator(mapper);
        this.token = UUID.randomUUID().toString();
        this.resultSpec = resultSpec;

        // Register tools from JavaMethodToolSpec instances
        if (toolSpecs != null) {
            for (JavaMethodToolSpec spec : toolSpecs) {
                registerJavaTools(spec.instance());
            }
        }

        if (dynamicToolSpecs != null) {
            dynamicToolSpecs.forEach(spec -> dynamicProviders.add(spec.provider()));
            refreshDynamicIndex();
        }

        // Register auto result submission tool if configured
        if (resultSpec != null) {
            registerResultTool(resultSpec);
        }

        // Bind server on loopback with an ephemeral port
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/mcp/" + runId, this::handleExchange);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
        this.port = this.server.getAddress().getPort();

        log.debug("Started in-JVM MCP server for runId {} on port {}", runId, port);
    }

    public int port() {
        return port;
    }

    public String token() {
        return token;
    }

    public String url() {
        return LocalServerDefaults.HTTP_URL_PREFIX + port + "/mcp/" + runId;
    }

    public Object submittedResult() {
        return submittedResult.get();
    }

    public List<ToolInvocationRecord> invocationLog() {
        synchronized (invocationLog) {
            return List.copyOf(invocationLog);
        }
    }

    /**
     * Converts this running in-JVM server into an {@link McpToolSpec} ready to write into workspace config.
     */
    public McpToolSpec asMcpToolSpec(String serverName) {
        return McpToolSpec.remote(
                serverName,
                "In-JVM Java tools and result collector",
                url(),
                Map.of("Authorization", "Bearer " + token)
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
        tools.put("submit_result", new RegisteredTool(
                "submit_result",
                "Submit the final structured review or result matching the required schema. Call exactly once.",
                schema,
                null
        ));
        log.debug("Registered in-JVM structured result tool 'submit_result' for type {}", spec.targetType().getSimpleName());
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (!("Bearer " + token).equals(auth)) {
            sendResponse(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Probe from OpenCode connection
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode rpc = mapper.readTree(requestBody);
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
            ObjectNode error = response.putObject("error");
            error.put("code", -32603);
            error.put("message", e.getMessage());
            sendResponse(exchange, 200, mapper.writeValueAsString(response));
        }
    }

    private JsonNode dispatchRpc(String method, JsonNode params, String rpcId) {
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
    private String renderOutput(Object output, ArrayNode content) {
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
                content.addObject().put("type", "text").put("text", media.text());
            }
            return (media.name() != null ? media.name() : "media")
                    + " (" + media.mimeType() + ", " + media.bytes().length + " bytes)";
        }
        String outputStr = output != null ? String.valueOf(output) : "null";
        content.addObject().put("type", "text").put("text", outputStr);
        return outputStr;
    }

    private JsonNode handleToolCall(JsonNode params, String rpcId) {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");
        String sessionId = params.path("_meta").path("ai.opencode/sessionID").asText("");

        RegisteredTool tool = tools.get(toolName);
        DynamicToolProvider dynamicProvider = tool == null ? findDynamicProvider(toolName) : null;
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");

        if (tool == null && dynamicProvider == null) {
            result.put("isError", true);
            content.addObject().put("type", "text").put("text", "Unknown tool: " + toolName);
            return result;
        }

        long t0 = System.currentTimeMillis();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeCancellations.put(rpcId, cancelled);

        try {
            if ("submit_result".equals(toolName) && resultSpec != null) {
                // Handle structured result submission
                try {
                    Object parsed = mapper.treeToValue(args, resultSpec.targetType());
                    submittedResult.set(parsed);
                    content.addObject().put("type", "text").put("text", "accepted");
                    recordInvocation(toolName, sessionId, args.toString(), "accepted", false, System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    result.put("isError", true);
                    String errorMsg = "INVALID: failed to parse structured output: " + e.getMessage();
                    content.addObject().put("type", "text").put("text", errorMsg);
                    recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
                }
                return result;
            }

            // Normal tool invocation (reflective Java method or dynamic provider)
            ToolContext toolContext = new ToolContext() {
                @Override public String runId() { return runId; }
                @Override public String sessionId() { return sessionId; }
                @Override public String agentName() { return ""; }
                @Override public String agentPath() { return ""; }
                @Override public boolean isCancelled() { return cancelled.get(); }
            };

            Object output = tool != null
                    ? tool.handler().invoke(args, toolContext)
                    : invokeDynamic(dynamicProvider, toolName, args, toolContext);
            String summary = renderOutput(output, content);
            recordInvocation(toolName, sessionId, args.toString(), summary, false, System.currentTimeMillis() - t0);
        } catch (InvocationTargetException e) {
            result.put("isError", true);
            String errorMsg = e.getCause() != null ? e.getCause().toString() : e.toString();
            content.addObject().put("type", "text").put("text", errorMsg);
            recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            result.put("isError", true);
            String errorMsg = "Tool error: " + e.getMessage();
            content.addObject().put("type", "text").put("text", errorMsg);
            recordInvocation(toolName, sessionId, args.toString(), errorMsg, true, System.currentTimeMillis() - t0);
        } finally {
            activeCancellations.remove(rpcId);
        }

        return result;
    }

    private void recordInvocation(String toolName, String sessionId, String args, String resultText, boolean isError, long duration) {
        invocationLog.add(new ToolInvocationRecord(toolName, sessionId, args, resultText, isError, duration));
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
        log.debug("Stopped in-JVM MCP server for runId {}", runId);
    }
}
