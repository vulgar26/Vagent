package com.vagent.orchestration.impl;

import com.vagent.orchestration.IntentResolutionService;
import com.vagent.orchestration.OrchestrationProperties;
import com.vagent.orchestration.model.ChatBranch;
import com.vagent.orchestration.model.IntentResult;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 规则版意图：优先匹配寒暄前缀 → 过短则澄清 → 否则 RAG。
 * <p>
 * 后续可替换为基于小模型或分类器的实现，只要实现 {@link IntentResolutionService} 即可接入。
 */
@Service
public class RuleBasedIntentResolutionService implements IntentResolutionService {

    private final OrchestrationProperties properties;

    public RuleBasedIntentResolutionService(OrchestrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public IntentResult resolve(String currentUserMessage) {
        String raw = currentUserMessage == null ? "" : currentUserMessage;
        String t = raw.trim();
        if (t.isEmpty()) {
            return new IntentResult(ChatBranch.CLARIFICATION, properties.getClarificationTemplate());
        }
        String lower = t.toLowerCase(Locale.ROOT);
        for (String prefix : splitPrefixes(properties.getSystemDialogPrefixes())) {
            if (prefix.isEmpty()) {
                continue;
            }
            if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return new IntentResult(ChatBranch.SYSTEM_DIALOG);
            }
        }
        if (t.length() < properties.getClarificationMinChars()) {
            return new IntentResult(ChatBranch.CLARIFICATION, properties.getClarificationTemplate());
        }
        return new IntentResult(ChatBranch.RAG);
    }

    private static String[] splitPrefixes(String csv) {
        if (csv == null || csv.isBlank()) {
            return new String[0];
        }
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
