package com.vagent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话内流式对话请求体：当前仅单轮用户消息（多轮历史在后续里程碑可扩展）。
 */
public class StreamChatRequest {

    @NotBlank
    @Size(max = 32_000)
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
