package com.vagent.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatRequest {

    @NotBlank
    private String query;

    private String mode;

    private String conversationId;

    /**
     * 与 eval dataset 对齐：为 true 时要求 {@code sources[].id} 均属本次检索候选集（前 N）。
     */
    private Boolean requiresCitations;

    /** 与题集对齐：{@code answer|clarify|deny|tool} 等。 */
    private String expectedBehavior;

    /** {@code stub|disabled|real}，与题集 {@code tool_policy} 对齐。 */
    private String toolPolicy;

    /**
     * 可选：{@code tool_policy=stub} 时为桩场景标识；{@code tool_policy=real} 时为 MCP 工具名（须在 {@code vagent.mcp.allowed-tools} 内）。
     */
    private String toolStubId;

    /**
     * 为 true 且服务端 {@code vagent.guardrails.quote-only.enabled=true} 时：对 {@code behavior=answer} 做 quote-only
     * 子串核对（见 {@code plans/quote-only-guardrails.md}）。
     */
    private Boolean quoteOnly;

    /**
     * 可选：与 {@code tool_policy=real} 联用时作为 MCP {@code arguments}（不经 {@code query} 自动映射）。
     * 仅评测接口（token 保护）使用；主链路仍由意图/服务端收敛参数。
     */
    private Map<String, Object> mcpToolArguments;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Boolean getRequiresCitations() {
        return requiresCitations;
    }

    public void setRequiresCitations(Boolean requiresCitations) {
        this.requiresCitations = requiresCitations;
    }

    public String getExpectedBehavior() {
        return expectedBehavior;
    }

    public void setExpectedBehavior(String expectedBehavior) {
        this.expectedBehavior = expectedBehavior;
    }

    public String getToolPolicy() {
        return toolPolicy;
    }

    public void setToolPolicy(String toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    public String getToolStubId() {
        return toolStubId;
    }

    public void setToolStubId(String toolStubId) {
        this.toolStubId = toolStubId;
    }

    public Boolean getQuoteOnly() {
        return quoteOnly;
    }

    public void setQuoteOnly(Boolean quoteOnly) {
        this.quoteOnly = quoteOnly;
    }

    public Map<String, Object> getMcpToolArguments() {
        return mcpToolArguments;
    }

    public void setMcpToolArguments(Map<String, Object> mcpToolArguments) {
        this.mcpToolArguments = mcpToolArguments;
    }
}

