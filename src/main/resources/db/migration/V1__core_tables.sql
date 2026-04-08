-- 核心业务表（PostgreSQL）；与 schema-core.sql 语义一致，由 Flyway 版本化管理。
-- 初版为 VARCHAR；随后由 V3 升级为原生 uuid（Java 侧规范字符串 + TypeHandler）。

CREATE TABLE users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE conversations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(256) NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE messages (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
