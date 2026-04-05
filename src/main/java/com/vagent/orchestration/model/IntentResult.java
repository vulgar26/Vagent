package com.vagent.orchestration.model;

import java.util.Optional;

/**
 * 意图判定结果：分支 + 可选的澄清模板（仅在 {@link ChatBranch#CLARIFICATION} 时使用）。
 */
public record IntentResult(ChatBranch branch, String clarificationHint) {

    public IntentResult(ChatBranch branch) {
        this(branch, null);
    }

    public Optional<String> optionalClarificationHint() {
        return Optional.ofNullable(clarificationHint).filter(s -> !s.isBlank());
    }
}
