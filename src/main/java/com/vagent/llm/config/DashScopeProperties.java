package com.vagent.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云通义千问（DashScope）<b>OpenAI 兼容模式</b>相关配置，前缀 {@code vagent.llm.dashscope}。
 * <p>
 * <b>官方文档要点：</b>兼容模式基址一般为 {@code https://dashscope.aliyuncs.com/compatible-mode/v1}，
 * 与 OpenAI Chat Completions 类似的请求体，流式响应为 SSE（{@code data:} 行）。
 * <p>
 * <b>密钥：</b>禁止写入仓库；生产环境用环境变量 {@code DASHSCOPE_API_KEY}，在 yml 中用 {@code ${DASHSCOPE_API_KEY:}} 引用。
 */
@ConfigurationProperties(prefix = "vagent.llm.dashscope")
public class DashScopeProperties {

    /**
     * 兼容模式 API 根路径（不含 {@code /chat/completions}，实现类会拼接）。
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * API Key；空字符串时表示未配置，{@link com.vagent.llm.impl.DashScopeCompatibleStreamingLlmClient} 会在请求时失败并回调 {@code onError}。
     */
    private String apiKey = "";

    /**
     * 对话模型名（如 {@code qwen-turbo}、{@code qwen-plus}）；若为空则回退 {@link LlmProperties#getDefaultModel()}，再回退 {@code qwen-turbo}。
     */
    private String chatModel = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }
}
