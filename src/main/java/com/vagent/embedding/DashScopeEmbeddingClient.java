package com.vagent.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vagent.embedding.dashscope.DashScopeEmbeddingsResponseParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * U2：调用阿里云通义千问兼容模式 {@code POST /v1/embeddings}，返回与 {@link EmbeddingProperties#getDimensions()} 等长的 float 向量。
 * <p>
 * <b>流程：</b>构造 JSON（{@code model}、{@code input}、{@code dimensions}、{@code encoding_format: float}）→ 同步 HTTP →
 * 解析 {@code data[0].embedding}；HTTP 非 2xx 或 {@code error} 字段则抛异常。
 * <p>
 * <b>密钥：</b>未配置时 {@link #embed(String)} 抛 {@link IllegalStateException}，与 {@link com.vagent.llm.impl.DashScopeCompatibleStreamingLlmClient} 行为一致。
 */
public final class DashScopeEmbeddingClient implements EmbeddingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingDashScopeProperties dashScopeProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeEmbeddingClient(
            EmbeddingProperties embeddingProperties,
            EmbeddingDashScopeProperties dashScopeProperties,
            ObjectMapper objectMapper) {
        this.embeddingProperties = embeddingProperties;
        this.dashScopeProperties = dashScopeProperties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public float[] embed(String text) {
        String apiKey = dashScopeProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置嵌入 API Key：请设置环境变量 DASHSCOPE_API_KEY 或 vagent.embedding.dashscope.api-key");
        }
        int dimensions = embeddingProperties.getDimensions();
        if (dimensions <= 0) {
            throw new IllegalStateException("vagent.embedding.dimensions must be positive");
        }

        String url = buildEmbeddingsUrl(dashScopeProperties.getBaseUrl());
        String body;
        try {
            body = buildRequestBody(text, dimensions);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build embeddings request", e);
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("DashScope embeddings HTTP " + code + ": " + response.body());
            }
            return DashScopeEmbeddingsResponseParser.parseEmbeddingArray(
                    objectMapper, response.body(), dimensions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String buildRequestBody(String text, int dimensions) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", dashScopeProperties.getModel().trim());
        root.put("input", text == null ? "" : text);
        root.put("dimensions", dimensions);
        root.put("encoding_format", "float");
        return objectMapper.writeValueAsString(root);
    }

    private static String buildEmbeddingsUrl(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.isEmpty()) {
            b = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (b.endsWith("/")) {
            return b + "embeddings";
        }
        return b + "/embeddings";
    }
}
