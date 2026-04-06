package com.vagent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * U2：DashScope <b>OpenAI 兼容模式</b>下的文本嵌入配置，前缀 {@code vagent.embedding.dashscope}。
 * <p>
 * 实际输出维度由 {@link EmbeddingProperties#getDimensions()} 决定，请求体中携带同名 {@code dimensions} 字段，
 * 须与 {@code schema-vector.sql} 中 {@code vector(N)} 一致（当前默认 1024）。
 */
@ConfigurationProperties(prefix = "vagent.embedding.dashscope")
public class EmbeddingDashScopeProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 与聊天共用环境变量即可；勿写入仓库。 */
    private String apiKey = "";

    /** 向量模型名，如 {@code text-embedding-v3}（以控制台为准）。 */
    private String model = "text-embedding-v3";

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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
