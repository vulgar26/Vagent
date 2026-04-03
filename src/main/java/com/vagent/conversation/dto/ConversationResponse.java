package com.vagent.conversation.dto;

import java.time.Instant;

/**
 * 会话列表/详情对外返回（不含 userId，由鉴权隐含）。
 */
public record ConversationResponse(
        String id,
        String title,
        Instant createdAt
) {
}
