package com.adapteraspring.testsupport;

import com.adapteraspring.json.CanonicalJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulator of Adapter B's documented behavior; this is not Adapter B.
 */
public final class FakeBServer {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<String, Claim> claims = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> effects = new ConcurrentHashMap<>();
    private final AtomicInteger workerCalls = new AtomicInteger();
    private final int staleSeconds;
    private final int requestedPort;
    private volatile HttpServer server;

    public FakeBServer() {
        this(1, 0);
    }

    public FakeBServer(int staleSeconds) {
        this(staleSeconds, 0);
    }

    public FakeBServer(int staleSeconds, int port) {
        this.staleSeconds = staleSeconds;
        this.requestedPort = port;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("localhost", requestedPort), 0);
        server.createContext("/v1/requests", this::handleRequest);
        server.createContext("/healthz", this::handleHealth);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public synchronized void restart() throws IOException {
        stop();
        start();
    }

    public String baseUrl() {
        HttpServer current = server;
        if (current == null) {
            throw new IllegalStateException("fake B is not running");
        }
        return "http://localhost:" + current.getAddress().getPort();
    }

    public int effectsCount(UUID dialogId, UUID requestId) {
        return count(effects, key(dialogId, requestId));
    }

    public int claimedCount(UUID dialogId, UUID requestId) {
        Claim claim = claims.get(key(dialogId, requestId));
        return claim == null ? 0 : claim.claims.get();
    }

    public int workerCalls() {
        return workerCalls.get();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        send(exchange, 200, "{\"status\":\"healthy\"}");
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "");
            return;
        }
        String fault = exchange.getRequestHeaders().getFirst("X-Fault");
        if (fault != null && !fault.equals("drop-response")
                && !fault.equals("crash-before") && !fault.equals("crash-during")
                && !fault.startsWith("delay=")) {
            send(exchange, 400, "{\"detail\":\"invalid fault\"}");
            return;
        }
        JsonNode request;
        try {
            request = mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            send(exchange, 422, "{\"detail\":\"invalid json\"}");
            return;
        }
        if (!validRequest(request)) {
            send(exchange, 422, "{\"detail\":\"invalid request\"}");
            return;
        }

        String dialogId = request.path("dialog_id").textValue();
        String requestId = request.path("request_id").textValue();
        String key = dialogId + "|" + requestId;
        String hash = CanonicalJson.sha256Hex(CanonicalJson.canonicalize(request.get("payload")));
        Claim claim = claims.computeIfAbsent(key, ignored -> new Claim(hash));
        synchronized (claim) {
            if (!claim.hash.equals(hash)) {
                send(exchange, 409, "{\"detail\":{\"status\":\"FAILED\",\"message\":\"payload_mismatch\"}}");
                return;
            }
            if (claim.status == Status.COMPLETED) {
                send(exchange, 200, response(request, claim, true));
                return;
            }
            if (claim.status == Status.FAILED) {
                send(exchange, 200, response(request, claim, true));
                return;
            }
            if (claim.status == Status.PROCESSING
                    && claim.claimedAt + staleSeconds * 1000L > System.currentTimeMillis()) {
                send(exchange, 200, response(request, claim, false));
                return;
            }
            claim.status = Status.PROCESSING;
            claim.claimedAt = System.currentTimeMillis();
            claim.claims.incrementAndGet();
        }

        if (fault != null && fault.equals("crash-before")) {
            close(exchange);
            return;
        }
        if (fault != null && fault.startsWith("delay=")) {
            sleep(Long.parseLong(fault.substring("delay=".length())));
        }
        if (fault != null && fault.equals("crash-during")) {
            close(exchange);
            return;
        }

        workerCalls.incrementAndGet();
        var result = mapper.createObjectNode();
        result.put("worker", "stub");
        result.set("echo", request.get("payload"));
        claim.result = result;
        claim.status = Status.COMPLETED;
        effects.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        String body = response(request, claim, false);
        if ("drop-response".equals(fault)) {
            send(exchange, 503, "");
        } else {
            send(exchange, 200, body);
        }
    }

    private boolean validRequest(JsonNode request) {
        return request != null && request.isObject() && request.size() == 4
                && request.hasNonNull("dialog_id") && request.path("dialog_id").isTextual()
                && request.path("dialog_id").textValue().length() > 0
                && request.hasNonNull("task_id") && request.path("task_id").isTextual()
                && request.hasNonNull("request_id") && request.path("request_id").isTextual()
                && request.has("payload") && request.get("payload").isObject();
    }

    private String response(JsonNode request, Claim claim, boolean duplicate) {
        var object = mapper.createObjectNode();
        object.put("dialog_id", request.path("dialog_id").textValue());
        object.put("task_id", request.path("task_id").textValue());
        object.put("request_id", request.path("request_id").textValue());
        object.put("status", claim.status.name());
        object.set("result", claim.result);
        object.put("duplicate", duplicate);
        object.put("message", claim.status == Status.COMPLETED
                ? (duplicate ? "Request already completed." : "Request processed successfully.")
                : "Request is already being processed.");
        return object.toString();
    }

    private static String key(UUID dialogId, UUID requestId) {
        return dialogId + "|" + requestId;
    }

    private static int count(Map<String, AtomicInteger> map, String key) {
        AtomicInteger value = map.get(key);
        return value == null ? 0 : value.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void close(HttpExchange exchange) throws IOException {
        exchange.getResponseBody().close();
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private enum Status { PROCESSING, COMPLETED, FAILED }

    private static final class Claim {
        private final String hash;
        private final AtomicInteger claims = new AtomicInteger();
        private volatile Status status;
        private volatile long claimedAt = System.currentTimeMillis();
        private volatile JsonNode result;

        private Claim(String hash) {
            this.hash = hash;
        }
    }
}
