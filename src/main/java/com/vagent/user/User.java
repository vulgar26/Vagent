package com.vagent.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应用用户（登录主体），与会话、后续消息、检索租户隔离关联。
 * <p>
 * 主键列类型为 PostgreSQL {@code uuid}：Java 侧使用 {@link UUID}，避免 {@code selectById(String)} 与 {@code uuid} 比较报错。
 */
@TableName(value = "users", autoResultMap = true)
public class User {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
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
