package com.vagent.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 与 LLM 相关的可配置项，从 {@code application.yml} 中以 {@code vagent.llm} 为前缀读取。
 * <p>
 * <b>这个类是干什么的：</b>
 * 把「用哪家实现、默认模型名」等从代码里挪到配置文件，换环境（开发/测试/生产）或换厂商时改 yml/env 即可，无需改代码。
 * <p>
 * <b>为什么用 ConfigurationProperties：</b>
 * Spring Boot 官方推荐方式，支持类型安全、IDE 提示，且可与 {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}
 * 配合，按 {@code provider} 装配不同的 {@link com.vagent.llm.LlmClient} Bean。
 * <p>
 * <b>与 LlmClientConfiguration 的关系：</b>
 * {@link LlmClientConfiguration} 根据 {@link #getProvider()} 决定注册哪一个具体实现类为 Bean。
 */
@ConfigurationProperties(prefix = "vagent.llm")
public class LlmProperties {

    /**
     * 选用哪一种 {@link com.vagent.llm.LlmClient} 实现。
     * <ul>
     *   <li>{@code noop}：空实现，不调外部接口, 用于本地无密钥时的占位</li>
     *   <li>后续可增 {@code dashscope}、{@code openai-compatible} 等，与新增实现类一一对应</li>
     * </ul>
     */
    private String provider = "noop";

    /**
     * 当请求里未指定模型或指定为空时，实现类可回退使用该默认模型 id（具体是否使用由实现类决定）。
     */
    private String defaultModel = "";

    /**
     * {@code fake-stream} 时每段字符数（≥1）。
     */
    private int fakeStreamChunkSize = 4;

    /**
     * {@code fake-stream} 时每段之间延迟（毫秒），0 表示不 sleep。
     */
    private int fakeStreamChunkDelayMs = 25;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getFakeStreamChunkSize() {
        return fakeStreamChunkSize;
    }

    public void setFakeStreamChunkSize(int fakeStreamChunkSize) {
        this.fakeStreamChunkSize = fakeStreamChunkSize;
    }

    public int getFakeStreamChunkDelayMs() {
        return fakeStreamChunkDelayMs;
    }

    public void setFakeStreamChunkDelayMs(int fakeStreamChunkDelayMs) {
        this.fakeStreamChunkDelayMs = fakeStreamChunkDelayMs;
    }
}
