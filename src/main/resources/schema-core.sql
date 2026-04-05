-- 核心业务表（与 H2 PostgreSQL 模式兼容，供 test profile 仅加载本文件）。
-- 主键 CHAR(36)，MyBatis-Plus ASSIGN_UUID。

CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS conversations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(256) NULL,
    created_at TIMESTAMP(6) NOT NULL
);
