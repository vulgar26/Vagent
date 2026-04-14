package com.vagent.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识库文档（一篇用户可见的标题 + 若干分块）。主键与外键列为 PostgreSQL {@code uuid}，Java 使用 {@link UUID}。
 */
@TableName(value = "kb_documents", autoResultMap = true)
public class KbDocument {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    private String title;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
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
