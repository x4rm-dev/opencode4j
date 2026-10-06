package io.opencode4j.agent.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opencode4j.config.LocalServerDefaults;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ResultSpec;
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

    private record RegisteredTool(
            String name,
            String description,
            ObjectNode inputSchema,
            Object targetInstance,
            Method method
    ) {}

    private final ObjectMapper mapper;
    private final JsonSchemaGenerator schemaGenerator;
    private final String token;
    private final String runId;
    private final HttpServer server;
    private final int port;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
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
            tools.put(toolName, new RegisteredTool(toolName, tool.description(), schema, target, method));
            log.debug("Registered in-JVM tool '{}' from method {}.{}", toolName, clazz.getSimpleName(), method.getName());
        }
    }

    private <T> void registerResultTool(ResultSpec<T> spec) {
        ObjectNode schema = schemaGenerator.schemaForType(spec.targetType());
        tools.put("submit_result", new RegisteredTool(
                "submit_result",
                "Submit the final structured review or result matching the required schema. Call exactly once.",
                schema,
                null,
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
        return result;
    }

    private JsonNode handleToolCall(JsonNode params, String rpcId) {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");
        String sessionId = params.path("_meta").path("ai.opencode/sessionID").asText("");

        RegisteredTool tool = tools.get(toolName);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");

        if (tool == null) {
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

            // Normal Java tool invocation
            Method method = tool.method();
            Parameter[] parameters = method.getParameters();
            Object[] arguments = new Object[parameters.length];

            ToolContext toolContext = new ToolContext() {
                @Override public String runId() { return runId; }
                @Override public String sessionId() { return sessionId; }
                @Override public String agentName() { return ""; }
                @Override public String agentPath() { return ""; }
                @Override public boolean isCancelled() { return cancelled.get(); }
            };

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                if (ToolContext.class.isAssignableFrom(param.getType())) {
                    arguments[i] = toolContext;
                } else {
                    Param paramAnno = param.getAnnotation(Param.class);
                    String paramName = paramAnno != null && !paramAnno.name().isBlank() ? paramAnno.name() : param.getName();
                    JsonNode val = args.path(paramName);
                    arguments[i] = mapper.treeToValue(val, param.getType());
                }
            }

            method.setAccessible(true);
            Object output = method.invoke(tool.targetInstance(), arguments);
            if (output instanceof io.opencode4j.runtime.media.MediaResult media) {
                if (media.mimeType().startsWith("image/")) {
                    ObjectNode item = content.addObject();
                    item.put("type", "image");
                    item.put("data", media.base64());
                    item.put("mimeType", media.mimeType());
                } else {
                    ObjectNode item = content.addObject();
                    item.put("type", "resource");
                    ObjectNode res = item.putObject("resource");
                    String dataUri = "data:" + media.mimeType() + ";base64," + media.base64();
                    res.put("uri", dataUri);
                    res.put("mimeType", media.mimeType());
                    if (media.name() != null) {
                        res.put("name", media.name());
                    }
                }
                if (media.text() != null && !media.text().isBlank()) {
                    content.addObject().put("type", "text").put("text", media.text());
                }
                String summary = (media.name() != null ? media.name() : "media")
                        + " (" + media.mimeType() + ", " + media.bytes().length + " bytes)";
                recordInvocation(toolName, sessionId, args.toString(), summary, false, System.currentTimeMillis() - t0);
            } else {
                String outputStr = output != null ? String.valueOf(output) : "null";
                content.addObject().put("type", "text").put("text", outputStr);
                recordInvocation(toolName, sessionId, args.toString(), outputStr, false, System.currentTimeMillis() - t0);
            }
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
