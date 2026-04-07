package com.vagent.orchestration.model;

import java.util.Optional;

/**
 * 意图判定结果：分支 + 可选的澄清模板（仅在 {@link ChatBranch#CLARIFICATION} 时使用）。
 */
public record IntentResult(ChatBranch branch, String clarificationHint, boolean toolIntent, String toolName) {

    public IntentResult(ChatBranch branch) {
        this(branch, null, false, null);
    }

    public IntentResult(ChatBranch branch, String clarificationHint) {
        this(branch, clarificationHint, false, null);
    }

    public IntentResult(ChatBranch branch, boolean toolIntent, String toolName) {
        this(branch, null, toolIntent, toolName);
    }

    public Optional<String> optionalClarificationHint() {
        return Optional.ofNullable(clarificationHint).filter(s -> !s.isBlank());
    }

    public Optional<String> optionalToolName() {
        return Optional.ofNullable(toolName).filter(s -> !s.isBlank());
    }
}
