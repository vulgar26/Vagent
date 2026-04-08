package com.vagent.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vagent.mybatis.typehandler.UuidStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 对话会话（{@code conversationId} 载体）。
 * <p>
 * <b>与 User 关系：</b> 仅存 {@link #userId} 外键，不嵌套实体，便于 MyBatis 映射与查询。
 */
@TableName(value = "conversations", autoResultMap = true)
public class Conversation {

    @TableId(value = "id", type = IdType.ASSIGN_UUID, typeHandler = UuidStringTypeHandler.class)
    private String id;

    @TableField(value = "user_id", typeHandler = UuidStringTypeHandler.class)
    private String userId;

    private String title;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
