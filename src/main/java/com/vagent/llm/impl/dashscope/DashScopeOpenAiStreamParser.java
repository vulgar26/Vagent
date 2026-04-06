package com.vagent.llm.impl.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;

/**
 * 解析 DashScope 兼容模式下 SSE 中单行 {@code data:} 后的 JSON 负载（OpenAI 流式格式）。
 * <p>
 * 正常增量为 {@code choices[0].delta.content}；结束行为 {@code data: [DONE]}，由调用方识别，不经过本类。
 */
public final class DashScopeOpenAiStreamParser {

    private final ObjectMapper objectMapper;

    public DashScopeOpenAiStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return 本行若包含可展示的文本增量则返回（含空串，调用方可忽略空）；错误负载则抛出 {@link IOException} 包装业务信息
     */
    public Optional<String> parseDataLineJson(String jsonPayload) throws IOException {
        JsonNode root = objectMapper.readTree(jsonPayload);
        if (root.has("error")) {
            String msg =
                    root.path("error").path("message").asText(
                            root.path("error").toString());
            throw new IOException("DashScope API error: " + msg);
        }
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return Optional.empty();
        }
        JsonNode delta = choices.get(0).get("delta");
        if (delta == null) {
            return Optional.empty();
        }
        JsonNode content = delta.get("content");
        if (content == null || content.isNull()) {
            return Optional.empty();
        }
        return Optional.of(content.asText(""));
    }
}
