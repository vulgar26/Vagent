package com.vagent.orchestration.impl;

import com.vagent.orchestration.IntentResolutionService;
import com.vagent.orchestration.OrchestrationProperties;
import com.vagent.orchestration.model.ChatBranch;
import com.vagent.orchestration.model.IntentResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则版意图：优先匹配寒暄前缀 → 过短则澄清 → 否则 RAG。
 * <p>
 * 后续可替换为基于小模型或分类器的实现，只要实现 {@link IntentResolutionService} 即可接入。
 */
@Service
public class RuleBasedIntentResolutionService implements IntentResolutionService {

    private static final Pattern TOOL_ASSIGN_PATTERN =
            Pattern.compile("(?i)(?:^|\\s)tool\\s*=\\s*([a-zA-Z0-9_\\-]+)(?:\\s|$)");
    private static final Pattern TOOL_SLASH_PATTERN =
            Pattern.compile("(?i)(?:^|\\s)/tool\\s+([a-zA-Z0-9_\\-]+)(?:\\s|$)");

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

        // U7：显式“工具意图”——命中关键词则允许在 RAG 分支中调用 MCP 工具（实际是否调用仍需白名单+开关）。
        if (properties.isToolIntentEnabled()) {
            String toolName = parseExplicitToolName(t);
            if (toolName != null && !toolName.isBlank()) {
                return new IntentResult(ChatBranch.RAG, true, toolName);
            }
            for (String kw : splitPrefixes(properties.getToolIntentKeywords())) {
                if (kw.isEmpty()) {
                    continue;
                }
                if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                    return new IntentResult(ChatBranch.RAG, true, properties.getToolIntentDefaultToolName());
                }
            }
        }
        return new IntentResult(ChatBranch.RAG);
    }

    private static String parseExplicitToolName(String rawTrimmed) {
        Matcher m = TOOL_ASSIGN_PATTERN.matcher(rawTrimmed);
        if (m.find()) {
            return m.group(1);
        }
        Matcher m2 = TOOL_SLASH_PATTERN.matcher(rawTrimmed);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
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
