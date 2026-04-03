package com.vagent.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 应用用户（登录主体），与会话、后续消息、检索租户隔离关联。
 * <p>
 * <b>持久化：</b> MyBatis-Plus + PostgreSQL；主键 {@link #id} 为 UUID 字符串，与 JWT subject、API 中 {@code userId} 一致。
 * <p>
 * <b>时间字段：</b> 使用 {@link LocalDateTime} 与 {@code TIMESTAMP(6)} 映射简单可靠；对外 DTO 再转为 {@link java.time.Instant} 如需。
 */
@TableName("users")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
