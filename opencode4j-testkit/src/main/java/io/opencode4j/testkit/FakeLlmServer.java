package io.opencode4j.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * A scriptable OpenAI-compatible chat model on loopback, for driving a real OpenCode server in tests.
 *
 * <p>Requests that offer tools are agent steps: each takes the next scripted {@link Reply}, or answers
 * {@link #DEFAULT_ANSWER} when the script is empty. Requests without tools (session titles, compaction) answer
 * plain text and do not consume the script. Every request is recorded, including the tool names it offered.</p>
 */
@Slf4j
public final class FakeLlmServer implements AutoCloseable {

    /** The answer given when nothing is scripted. */
    public static final String DEFAULT_ANSWER = "pong";
    /** The only model this server serves. */
    public static final String MODEL_ID = "fake-1";

    /** One scripted response. */
    public sealed interface Reply permits Text, ToolCall, Failure, Delayed {
    }

    /** Streams {@code text} and stops. */
    public record Text(String text) implements Reply {
    }

    /** Calls the first offered tool whose name contains {@code toolNameContains}, with JSON {@code arguments}. */
    public record ToolCall(String toolNameContains, String arguments) implements Reply {
    }

    /** Fails the request with an HTTP error, optionally asking to retry after {@code retryAfterSeconds}. */
    public record Failure(int status, String body, Integer retryAfterSeconds) implements Reply {
    }

    /** Waits {@code delay} before giving {@code reply}. */
    public record Delayed(Duration delay, Reply reply) implements Reply {
    }

    /**
     * A recorded request.
     *
     * @param tools names of the tools offered to the model, in order
     */
    public record Request(String model, List<String> tools, int messageCount, String lastUserText) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final ConcurrentLinkedQueue<Reply> script = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger completionIds = new AtomicInteger();

    private FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", this::completions);
        server.createContext("/v1/models", this::models);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public static FakeLlmServer start() throws IOException {
        return new FakeLlmServer();
    }

    /** Base URL including {@code /v1}, as an OpenAI-compatible provider expects it. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** Appends replies to the script. */
    public FakeLlmServer enqueue(Reply... replies) {
        script.addAll(List.of(replies));
        return this;
    }

    /** Clears the script and the recorded requests. */
    public void reset() {
        script.clear();
        requests.clear();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** The tools offered by the most recent agent step, or an empty list if there was none. */
    public List<String> lastOfferedTools() {
        for (int i = requests.size() - 1; i >= 0; i--) {
            if (!requests.get(i).tools().isEmpty()) {
                return requests.get(i).tools();
            }
        }
        return List.of();
    }

    /** Number of agent steps (requests offering tools) received so far. */
    public long agentSteps() {
        return requests.stream().filter(request -> !request.tools().isEmpty()).count();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void models(HttpExchange exchange) throws IOException {
        byte[] body = ("{\"object\":\"list\",\"data\":[{\"id\":\"" + MODEL_ID + "\",\"object\":\"model\"}]}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void completions(HttpExchange exchange) throws IOException {
        JsonNode request = mapper.readTree(exchange.getRequestBody());
        List<String> tools = new ArrayList<>();
        request.path("tools").forEach(tool -> tools.add(tool.path("function").path("name").asText()));
        JsonNode messages = request.path("messages");
        requests.add(new Request(request.path("model").asText(), List.copyOf(tools), messages.size(),
                lastUserText(messages)));
        boolean agentStep = !tools.isEmpty();
        boolean afterToolResult = messages.size() > 0
                && "tool".equals(messages.get(messages.size() - 1).path("role").asText());
        Reply reply = agentStep && !afterToolResult ? script.poll() : null;
        try {
            respond(exchange, reply == null ? new Text(agentStep ? DEFAULT_ANSWER : "Title") : reply, tools);
        } catch (IOException e) {
            // The client went away, for example because the run was cancelled.
            log.debug("Fake LLM response aborted: {}", e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange, Reply reply, List<String> tools) throws IOException {
        switch (reply) {
            case Delayed delayed -> {
                try {
                    Thread.sleep(delayed.delay().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                respond(exchange, delayed.reply(), tools);
            }
            case Failure failure -> {
                byte[] body = failure.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (failure.retryAfterSeconds() != null) {
                    exchange.getResponseHeaders().set("Retry-After", String.valueOf(failure.retryAfterSeconds()));
                }
                exchange.sendResponseHeaders(failure.status(), body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            case Text text -> stream(exchange, List.of(delta(text.text())), "stop");
            case ToolCall call -> {
                String target = tools.stream().filter(name -> name.contains(call.toolNameContains())).findFirst()
                        .orElse(null);
                if (target == null) {
                    stream(exchange, List.of(delta("No tool matching " + call.toolNameContains() + " was offered")),
                            "stop");
                    return;
                }
                ObjectNode toolDelta = mapper.createObjectNode();
                ArrayNode calls = toolDelta.putArray("tool_calls");
                ObjectNode first = calls.addObject();
                first.put("index", 0);
                first.put("id", "call_" + completionIds.incrementAndGet());
                first.put("type", "function");
                first.putObject("function").put("name", target).put("arguments", call.arguments());
                stream(exchange, List.of(toolDelta), "tool_calls");
            }
        }
    }

    private ObjectNode delta(String text) {
        ObjectNode delta = mapper.createObjectNode();
        delta.put("content", text);
        return delta;
    }

    private void stream(HttpExchange exchange, List<ObjectNode> deltas, String finishReason) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            String id = "chatcmpl-" + completionIds.incrementAndGet();
            ObjectNode role = mapper.createObjectNode().put("role", "assistant").put("content", "");
            write(out, chunk(id, role, null));
            for (ObjectNode delta : deltas) {
                write(out, chunk(id, delta, null));
            }
            write(out, chunk(id, mapper.createObjectNode(), finishReason));
            ObjectNode usage = mapper.createObjectNode();
            usage.put("id", id).put("object", "chat.completion.chunk").put("model", MODEL_ID);
            usage.putArray("choices");
            usage.putObject("usage").put("prompt_tokens", 11).put("completion_tokens", 3).put("total_tokens", 14);
            write(out, usage);
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private ObjectNode chunk(String id, ObjectNode delta, String finishReason) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", id).put("object", "chat.completion.chunk").put("model", MODEL_ID);
        ObjectNode choice = chunk.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("delta", delta);
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }
        return chunk;
    }

    private void write(OutputStream out, JsonNode chunk) throws IOException {
        out.write(("data: " + mapper.writeValueAsString(chunk) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String lastUserText(JsonNode messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode message = messages.get(i);
            if ("user".equals(message.path("role").asText())) {
                JsonNode content = message.path("content");
                if (content.isTextual()) {
                    return content.asText();
                }
                StringBuilder text = new StringBuilder();
                content.forEach(part -> text.append(part.path("text").asText("")));
                return text.toString();
            }
        }
        return "";
    }
}
