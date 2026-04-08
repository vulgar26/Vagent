-- 核心业务表（与PostgreSQL 模式兼容，供 test profile 仅加载本文件）。
-- 主键改为 VARCHAR(36) 以避免 CHAR 右填充空格引发的 ID/JWT/路由问题；MyBatis-Plus ASSIGN_UUID。

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(256) NULL,
    created_at TIMESTAMP(6) NOT NULL
);

-- 会话内多轮消息（仅 USER/ASSISTANT 落库；SYSTEM 由编排层即时拼装，不存表）
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at);
