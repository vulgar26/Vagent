package com.vagent.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vagent.conversation.dto.ConversationResponse;
import com.vagent.conversation.dto.CreateConversationRequest;
import com.vagent.user.User;
import com.vagent.user.UserIdFormats;
import com.vagent.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话领域服务：按当前用户创建与列表查询（MyBatis-Plus）。
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;

    public ConversationService(ConversationMapper conversationMapper, UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
    }

    /**
     * 校验会话属于当前用户，供流式对话等接口使用。
     */
    @Transactional(readOnly = true)
    public Optional<Conversation> findOwnedByUser(String conversationId, UUID userId) {
        String uid = UserIdFormats.canonical(userId);
        String cid = conversationId == null ? "" : conversationId.trim();
        if (cid.isEmpty()) {
            return Optional.empty();
        }
        Conversation c = conversationMapper.selectById(cid);
        if (c == null || !uid.equals(c.getUserId())) {
            return Optional.empty();
        }
        return Optional.of(c);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(UUID userId) {
        String uid = UserIdFormats.canonical(userId);
        List<Conversation> rows = conversationMapper.selectList(
                Wrappers.lambdaQuery(Conversation.class)
                        .eq(Conversation::getUserId, uid)
                        .orderByDesc(Conversation::getCreatedAt));
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConversationResponse create(UUID userId, CreateConversationRequest request) {
        String uid = UserIdFormats.canonical(userId);
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new IllegalStateException("用户不存在: " + uid);
        }
        Conversation c = new Conversation();
        c.setUserId(uid);
        c.setTitle(normalizeTitle(request.getTitle()));
        c.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        conversationMapper.insert(c);
        return toResponse(c);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String t = title.trim();
        return t.isEmpty() ? null : t;
    }

    private ConversationResponse toResponse(Conversation c) {
        return new ConversationResponse(
                c.getId() != null ? c.getId().trim() : "",
                c.getTitle(),
                c.getCreatedAt().atOffset(ZoneOffset.UTC).toInstant());
    }
}
