-- MCP 工具调用审计（最小版）：仅存元数据与归因，不落工具入参/出参正文。

CREATE TABLE mcp_tool_invocations (
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

CREATE INDEX idx_mcp_tool_invocations_user_created ON mcp_tool_invocations (user_id, created_at);
CREATE INDEX idx_mcp_tool_invocations_conv_created ON mcp_tool_invocations (conversation_id, created_at);
CREATE INDEX idx_mcp_tool_invocations_tool_created ON mcp_tool_invocations (tool_name, created_at);
