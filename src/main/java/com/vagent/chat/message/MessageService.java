package com.vagent.chat.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话消息的读写服务：为 RAG 编排提供「最近 N 条历史」与「追加本轮 USER/ASSISTANT」。
 * <p>
 * <b>读路径（listRecentForConversation）：</b>
 * 先按 {@code created_at DESC} 取最多 {@code limit} 条，再反转为时间正序，保证交给 LLM 的列表是「从旧到新」，
 * 与 {@link com.vagent.llm.LlmChatRequest} 中 message 顺序一致。
 * <p>
 * <b>写路径：</b>
 * 用户一发消息就插入一条 USER；模型整段流式结束后插入一条 ASSISTANT（由 {@link com.vagent.chat.RagStreamChatService} 在 SSE 正常结束时调用）。
 */
@Service
public class MessageService {

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    /**
     * 读取某会话最近 {@code limit} 条消息，按时间从早到晚排序。
     *
     * @param conversationId 会话 id（无连字符或带连字符与表内存储一致即可，调用方应已校验归属）
     * @param limit            最大条数（≥1）
     */
    @Transactional(readOnly = true)
    public List<Message> listRecentForConversation(String conversationId, int limit) {
        int cap = Math.max(1, limit);
        List<Message> desc = messageMapper.selectList(
                Wrappers.lambdaQuery(Message.class)
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + cap));
        List<Message> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 持久化本轮用户输入（在发起检索与调用 LLM 之前写入，保证「当前提问」也进入历史边界定义：先落库再异步流式）。
     */
    @Transactional
    public void saveUserMessage(String conversationId, String userIdCompact, String content) {
        insert(conversationId, userIdCompact, Message.ROLE_USER, content);
    }

    /**
     * 持久化本轮助手完整回复（在 SSE 发出 {@code done} 且未取消时写入）。
     */
    @Transactional
    public void saveAssistantMessage(String conversationId, String userIdCompact, String content) {
        insert(conversationId, userIdCompact, Message.ROLE_ASSISTANT, content != null ? content : "");
    }

    private void insert(String conversationId, String userIdCompact, String role, String content) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setUserId(userIdCompact);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        messageMapper.insert(m);
    }
}
