package com.vagent.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求；标题可选。
 */
public class CreateConversationRequest {

    @Size(max = 256, message = "标题最长 256 字符")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
