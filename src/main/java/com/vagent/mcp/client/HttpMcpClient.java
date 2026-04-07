package com.vagent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vagent.mcp.config.McpProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * U6：MCP HTTP Client（JSON 响应模式）。
 * <p>
 * 说明：MCP Streamable HTTP 传输既支持 SSE，也支持 JSON-only 响应；本实现以 JSON-only 为主，便于在 Vagent 内同步调用。
 */
public final class HttpMcpClient implements McpClient {

    private static final String JSONRPC = "2.0";

    private final McpProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HttpClient httpClient;
    private final AtomicLong ids = new AtomicLong(1);

    private volatile boolean initialized = false;

    public HttpMcpClient(McpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(nonNull(properties.getConnectTimeout(), Duration.ofSeconds(5)))
                        .build();
    }

    @Override
    public List<Map<String, Object>> listTools() {
        ensureInitialized();
        JsonNode result = call("tools/list", objectMapper.createObjectNode(), null);
        JsonNode tools = result.get("tools");
        List<Map<String, Object>> out = new ArrayList<>();
        if (tools != null && tools.isArray()) {
            for (JsonNode t : tools) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = objectMapper.convertValue(t, Map.class);
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        ensureInitialized();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", name);
        if (arguments != null) {
            params.set("arguments", objectMapper.valueToTree(arguments));
        }
        JsonNode result = call("tools/call", params, name);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = objectMapper.convertValue(result, Map.class);
        return m;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            doInitialize();
            initialized = true;
        }
    }

    private void doInitialize() {
        String protocolVersion = properties.getProtocolVersion();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", protocolVersion != null && !protocolVersion.isBlank() ? protocolVersion : "2025-03-26");
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "vagent");
        clientInfo.put("version", "dev");
        params.set("clientInfo", clientInfo);

        call("initialize", params, "__initialize__");

        // Lifecycle: client must notify initialized after initialize response.
        notify("notifications/initialized", objectMapper.createObjectNode());
    }

    private JsonNode call(String method, ObjectNode params, String toolTag) {
        long startNs = System.nanoTime();
        String outcome = "success";
        try {
            long id = ids.getAndIncrement();
            ObjectNode req = objectMapper.createObjectNode();
            req.put("jsonrpc", JSONRPC);
            req.put("id", id);
            req.put("method", method);
            if (params != null && !params.isEmpty()) {
                req.set("params", params);
            }
            JsonNode response = postJsonRpc(req, true);
            JsonNode error = response.get("error");
            if (error != null && !error.isNull()) {
                throw new IllegalStateException("MCP error: " + error.toString());
            }
            JsonNode result = response.get("result");
            if (result == null || result.isNull()) {
                throw new IllegalStateException("MCP response missing result for method=" + method);
            }
            return result;
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            recordTimer("vagent.mcp.call", toolTag != null ? toolTag : method, outcome, startNs);
        }
    }

    private void notify(String method, ObjectNode params) {
        long startNs = System.nanoTime();
        String outcome = "success";
        try {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("jsonrpc", JSONRPC);
            req.put("method", method);
            if (params != null && !params.isEmpty()) {
                req.set("params", params);
            }
            postJsonRpc(req, false);
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            recordTimer("vagent.mcp.notify", method, outcome, startNs);
        }
    }

    private JsonNode postJsonRpc(ObjectNode payload, boolean expectJsonResponse) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("vagent.mcp.base-url is blank");
        }

        String token = properties.getToken();
        if (token == null) {
            token = "";
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON-RPC payload", e);
        }

        HttpRequest.Builder b =
                HttpRequest.newBuilder()
                        .uri(URI.create(trimTrailingSlash(baseUrl)))
                        .timeout(nonNull(properties.getRequestTimeout(), Duration.ofSeconds(30)))
                        .header("Content-Type", "application/json")
                        .header("Accept", expectJsonResponse ? "application/json" : "*/*")
                        .header("mcp-protocol-version", safeProtocolVersion())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (!token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
        } else if (properties.isEnabled()) {
            // 在 enabled=true 的情况下，token 为空通常意味着忘配；明确抛错更易排障。
            throw new IllegalStateException("未配置 MCP token：请设置 vagent.mcp.token（建议环境变量注入）");
        }

        HttpRequest req = b.build();
        try {
            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (expectJsonResponse) {
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("MCP HTTP " + code + ": " + resp.body());
                }
                return objectMapper.readTree(resp.body());
            } else {
                // Notification: server commonly returns 202 Accepted with empty body.
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("MCP notify HTTP " + code + ": " + resp.body());
                }
                return objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            throw new IllegalStateException("MCP HTTP request failed", e);
        }
    }

    private void recordTimer(String name, String tool, String outcome, long startNs) {
        long nanos = System.nanoTime() - startNs;
        Timer.builder(name)
                .tag("tool", tool)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private String safeProtocolVersion() {
        String v = properties.getProtocolVersion();
        return v != null && !v.isBlank() ? v.trim() : "2025-03-26";
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static <T> T nonNull(T v, T fallback) {
        return v != null ? v : fallback;
    }
}

