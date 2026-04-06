package com.vagent.llm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.LlmStreamSink;
import com.vagent.llm.config.DashScopeProperties;
import com.vagent.llm.config.LlmProperties;
import com.vagent.llm.impl.dashscope.DashScopeOpenAiStreamParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * U1：调用阿里云通义千问 <b>OpenAI 兼容模式</b> 的 Chat Completions 流式接口，将增量文本写入 {@link LlmStreamSink}。
 * <p>
 * <b>流程概要：</b>
 * <ol>
 *   <li>将 {@link LlmChatRequest#messages()} 映射为 OpenAI 风格的 {@code messages} 数组；</li>
 *   <li>POST {@code .../chat/completions}，{@code stream:true}；</li>
 *   <li>按行读取 SSE，解析 {@code data:} 行 JSON，取出 {@code choices[0].delta.content} 多次 {@code onChunk}；</li>
 *   <li>遇到 {@code data: [DONE]} 或流结束则 {@code onComplete}；HTTP 非 2xx 或解析到 error 字段则 {@code onError}。</li>
 * </ol>
 * <p>
 * <b>取消：</b>在两次 chunk 之间检查 {@link LlmChatRequest#isCancelled()}，为 true 时停止读取并 {@code onComplete}（与
 * {@link com.vagent.chat.LlmSseStreamingBridge} 配合后，由注册表决定最终 SSE 为 cancelled 或 done）。
 */
public final class DashScopeCompatibleStreamingLlmClient implements LlmClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final LlmProperties llmProperties;
    private final DashScopeProperties dashScopeProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final DashScopeOpenAiStreamParser parser;

    public DashScopeCompatibleStreamingLlmClient(
            LlmProperties llmProperties,
            DashScopeProperties dashScopeProperties,
            ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.dashScopeProperties = dashScopeProperties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build();
        this.parser = new DashScopeOpenAiStreamParser(objectMapper);
    }

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamSink sink) {
        String apiKey = dashScopeProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            sink.onError(
                    new IllegalStateException(
                            "未配置通义千问 API Key：请设置环境变量 DASHSCOPE_API_KEY 或 vagent.llm.dashscope.api-key"));
            return;
        }
        String model = resolveModel();
        String url = buildCompletionsUrl(dashScopeProperties.getBaseUrl());
        String body;
        try {
            body = buildRequestBody(request, model);
        } catch (Exception e) {
            sink.onError(e);
            return;
        }

        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                sink.onError(new IOException("DashScope HTTP " + code + ": " + errBody));
                return;
            }
            try (InputStream in = response.body();
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (request.isCancelled()) {
                        sink.onComplete();
                        return;
                    }
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(payload)) {
                        sink.onComplete();
                        return;
                    }
                    try {
                        Optional<String> chunk = parser.parseDataLineJson(payload);
                        if (chunk.isPresent()) {
                            String text = chunk.get();
                            if (!text.isEmpty()) {
                                sink.onChunk(text);
                            }
                        }
                    } catch (IOException parseErr) {
                        sink.onError(parseErr);
                        return;
                    }
                }
                sink.onComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.onError(e);
        } catch (Exception e) {
            sink.onError(e);
        }
    }

    private String resolveModel() {
        String m = dashScopeProperties.getChatModel();
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        String d = llmProperties.getDefaultModel();
        if (d != null && !d.isBlank()) {
            return d.trim();
        }
        return "qwen-turbo";
    }

    private static String buildCompletionsUrl(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.isEmpty()) {
            b = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (b.endsWith("/")) {
            return b + "chat/completions";
        }
        return b + "/chat/completions";
    }

    private String buildRequestBody(LlmChatRequest request, String model) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", true);
        ArrayNode messages = root.putArray("messages");
        for (LlmMessage m : request.messages()) {
            ObjectNode one = messages.addObject();
            one.put("role", toOpenAiRole(m.role()));
            one.put("content", m.content());
        }
        return objectMapper.writeValueAsString(root);
    }

    private static String toOpenAiRole(LlmMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }
}
