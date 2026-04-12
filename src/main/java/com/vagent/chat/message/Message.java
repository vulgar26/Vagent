package com.vagent.chat.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vagent.mybatis.typehandler.UuidStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 会话内一条落库消息（仅 USER / ASSISTANT）。
 * <p>
 * <b>为什么要有这张表：</b>
 * 把「多轮对话」持久化，后续 RAG 编排才能按时间顺序取出历史，拼进 {@link com.vagent.llm.LlmMessage}。
 * SYSTEM 提示（含动态检索到的知识）通常不落库：它是每次请求现场拼出来的，避免与「用户/助手真实发言」混在一起。
 * <p>
 * <b>与 conversations 的关系：</b>
 * {@link #conversationId} 指向会话主表；同一用户下的多条消息通过会话聚合。删除会话时由数据库 {@code ON DELETE CASCADE}
 * 级联删除消息，避免孤儿行。
 * <p>
 * <b>为什么同时存 user_id：</b>
 * 与项目里其它表一致，便于审计、按用户排查问题；检索历史时仍以 conversation_id 为主键过滤。
 */
@TableName(value = "messages", autoResultMap = true)
public class Message {

    /** 与 OpenAI Chat 中 user/assistant 字符串对齐，便于代码阅读与排查。 */
    public static final String ROLE_USER = "USER";

    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(value = "id", typeHandler = UuidStringTypeHandler.class)
    private String id;

    @TableField(value = "conversation_id", typeHandler = UuidStringTypeHandler.class)
    private String conversationId;

    @TableField(value = "user_id", typeHandler = UuidStringTypeHandler.class)
    private String userId;

    /**
     * {@link #ROLE_USER} 或 {@link #ROLE_ASSISTANT}；不设枚举是为了与 DB 字符串列直接对应，减少映射层转换。
     */
    private String role;

    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
