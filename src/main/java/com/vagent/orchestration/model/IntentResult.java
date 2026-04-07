package com.vagent.orchestration.model;

import java.util.Map;
import java.util.Optional;

/**
 * 意图判定结果：分支 + 可选的澄清模板（仅在 {@link ChatBranch#CLARIFICATION} 时使用）。
 */
public record IntentResult(
        ChatBranch branch,
        String clarificationHint,
        boolean toolIntent,
        String toolName,
        Map<String, Object> toolArguments) {

    public IntentResult(ChatBranch branch) {
        this(branch, null, false, null, Map.of());
    }

    public IntentResult(ChatBranch branch, String clarificationHint) {
        this(branch, clarificationHint, false, null, Map.of());
    }

    public IntentResult(ChatBranch branch, boolean toolIntent, String toolName) {
        this(branch, null, toolIntent, toolName, Map.of());
    }

    public IntentResult(ChatBranch branch, boolean toolIntent, String toolName, Map<String, Object> toolArguments) {
        this(branch, null, toolIntent, toolName, toolArguments != null ? toolArguments : Map.of());
    }

    public Optional<String> optionalClarificationHint() {
        return Optional.ofNullable(clarificationHint).filter(s -> !s.isBlank());
    }

    public Optional<String> optionalToolName() {
        return Optional.ofNullable(toolName).filter(s -> !s.isBlank());
    }

    public Map<String, Object> safeToolArguments() {
        return toolArguments != null ? toolArguments : Map.of();
    }
}
