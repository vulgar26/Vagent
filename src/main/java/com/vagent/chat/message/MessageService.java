package com.vagent.chat.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vagent.user.UserIdFormats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 会话消息的读写服务：为 RAG 编排提供「最近 N 条历史」与「追加本轮 USER/ASSISTANT」。
 */
@Service
public class MessageService {

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Transactional(readOnly = true)
    public List<Message> listRecentForConversation(String conversationId, int limit) {
        int cap = Math.max(1, limit);
        UUID convId = UserIdFormats.parseUuid(conversationId);
        List<Message> desc = messageMapper.selectList(
                Wrappers.lambdaQuery(Message.class)
                        .eq(Message::getConversationId, convId)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + cap));
        List<Message> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    @Transactional
    public void saveUserMessage(String conversationId, UUID userId, String content) {
        insert(conversationId, userId, Message.ROLE_USER, content);
    }

    @Transactional
    public void saveAssistantMessage(String conversationId, UUID userId, String content) {
        insert(conversationId, userId, Message.ROLE_ASSISTANT, content != null ? content : "");
    }

    private void insert(String conversationId, UUID userId, String role, String content) {
        Message m = new Message();
        m.setId(UUID.randomUUID());
        m.setConversationId(UserIdFormats.parseUuid(conversationId));
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        messageMapper.insert(m);
    }
}
