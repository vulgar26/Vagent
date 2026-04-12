package com.vagent.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

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
}

