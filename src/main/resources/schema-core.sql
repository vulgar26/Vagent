-- 核心业务表（test profile / H2 MODE=PostgreSQL；列类型 uuid 与 Flyway V3+ 对齐）。
-- 权威迁移：db/migration/V1 + V3（PostgreSQL）。

CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS conversations (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(256) NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id UUID NOT NULL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id),
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at);

-- MCP 工具调用审计（与 Flyway V5 对齐；test profile / H2 使用本文件建表）
CREATE TABLE IF NOT EXISTS mcp_tool_invocations (
    id UUID NOT NULL PRIMARY KEY,
    channel VARCHAR(16) NOT NULL,
    user_id UUID NULL,
    conversation_id UUID NULL,
    correlation_id VARCHAR(128) NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_version VARCHAR(32) NULL,
    tool_schema_hash VARCHAR(128) NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    latency_ms BIGINT NULL,
    arg_schema_validated BOOLEAN NULL,
    result_schema_validated BOOLEAN NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mcp_tool_invocations_user_created ON mcp_tool_invocations (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mcp_tool_invocations_conv_created ON mcp_tool_invocations (conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mcp_tool_invocations_tool_created ON mcp_tool_invocations (tool_name, created_at);
