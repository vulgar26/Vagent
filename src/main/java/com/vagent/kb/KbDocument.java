package com.vagent.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vagent.mybatis.typehandler.UuidStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 知识库文档（一篇用户可见的标题 + 若干分块）。
 */
@TableName(value = "kb_documents", autoResultMap = true)
public class KbDocument {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(value = "id", typeHandler = UuidStringTypeHandler.class)
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
