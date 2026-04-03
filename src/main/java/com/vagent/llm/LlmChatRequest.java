package com.vagent.llm;

import java.util.List;
import java.util.Objects;

/**
 * 一次「流式聊天请求」携带的数据（当前为最小字段集，后续可扩展）。
 * <p>
 * <b>这个类是干什么的：</b>
 * 把调用大模型时需要的信息打包成一个不可变对象，主要包含：多轮 {@link LlmMessage} 列表、以及选用的 {@code model} 标识。
 * <p>
 * <b>为什么单独成类而不是到处传 List + String：</b>
 * 后续 M3+ 会加入温度、topP、是否深度思考、工具调用等参数；集中在一个请求对象里便于演进且类型清晰。
 * <p>
 * <b>为什么现在字段很少：</b>
 * M0 只搭骨架，避免过早绑定某厂商的请求体格式；真正对接时在实现类里把本对象映射为厂商 API 的 JSON 即可。
 */
public final class LlmChatRequest {

    /** 按时间顺序排列的对话消息（通常含 system/user/assistant）。 */
    private final List<LlmMessage> messages;

    /**
     * 模型标识（厂商侧的 model 名称或 id）。
     * 可为空字符串时使用配置里的默认模型，具体规则由各 {@link LlmClient} 实现决定。
     */
    private final String model;

    public LlmChatRequest(List<LlmMessage> messages, String model) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.model = model;
    }

    public List<LlmMessage> messages() {
        return messages;
    }

    public String model() {
        return model;
    }
}
