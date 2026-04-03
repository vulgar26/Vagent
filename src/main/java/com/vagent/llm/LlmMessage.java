package com.vagent.llm;

import java.util.Objects;

/**
 * 单条聊天消息（角色 + 文本），用于拼装 {@link LlmChatRequest}。
 * <p>
 * <b>这个类是干什么的：</b>
 * 对应常见 Chat API 中的 message 结构：谁说的（角色）+ 说了什么（内容）。
 * <p>
 * <b>为什么要有 SYSTEM / USER / ASSISTANT：</b>
 * 与主流大模型对话协议一致，便于后续拼 Prompt、做 RAG 时在 system 里放知识、在 user 里放用户问题。
 * <p>
 * <b>扩展说明：</b>
 * 若某厂商需要额外字段（如 name、tool_calls），可在后续增加子类或包装类型，不必破坏现有调用方。
 */
public final class LlmMessage {

    /** 消息角色，与 OpenAI 风格 Chat 兼容的常见三种。 */
    public enum Role {
        /** 系统提示词、人设、知识注入等 */
        SYSTEM,
        /** 用户输入 */
        USER,
        /** 模型历史回复 */
        ASSISTANT
    }

    private final Role role;
    private final String content;

    public LlmMessage(Role role, String content) {
        this.role = Objects.requireNonNull(role, "role");
        this.content = Objects.requireNonNull(content, "content");
    }

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }
}
