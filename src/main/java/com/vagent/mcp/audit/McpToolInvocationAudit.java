package com.vagent.mcp.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.UUID;

@TableName("mcp_tool_invocations")
public class McpToolInvocationAudit {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String channel;

    @TableField("user_id")
    private UUID userId;

    @TableField("conversation_id")
    private UUID conversationId;

    @TableField("correlation_id")
    private String correlationId;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_version")
    private String toolVersion;

    @TableField("tool_schema_hash")
    private String toolSchemaHash;

    private String outcome;

    @TableField("error_code")
    private String errorCode;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("arg_schema_validated")
    private Boolean argSchemaValidated;

    @TableField("result_schema_validated")
    private Boolean resultSchemaValidated;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getToolSchemaHash() {
        return toolSchemaHash;
    }

    public void setToolSchemaHash(String toolSchemaHash) {
        this.toolSchemaHash = toolSchemaHash;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Boolean getArgSchemaValidated() {
        return argSchemaValidated;
    }

    public void setArgSchemaValidated(Boolean argSchemaValidated) {
        this.argSchemaValidated = argSchemaValidated;
    }

    public Boolean getResultSchemaValidated() {
        return resultSchemaValidated;
    }

    public void setResultSchemaValidated(Boolean resultSchemaValidated) {
        this.resultSchemaValidated = resultSchemaValidated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
