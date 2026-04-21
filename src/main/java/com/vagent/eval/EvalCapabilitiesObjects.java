package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.guardrails.GuardrailsProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 评测 HTTP 与主链路 SSE {@code meta} 共用的能力对象构造（避免 guardrails 字段漂移）。
 */
public final class EvalCapabilitiesObjects {

    private EvalCapabilitiesObjects() {}

    /**
     * 与 {@link com.vagent.eval.EvalChatController#capabilitiesEffective} 中 {@link EvalChatResponse.GuardrailsFlag}
     * 语义一致：{@code evidence_map} 在 Vagent eval 路径恒为可声明能力（与现有契约对齐）。
     */
    public static EvalChatResponse.GuardrailsFlag guardrailsFromProperties(GuardrailsProperties guardrailsProperties) {
        if (guardrailsProperties == null) {
            return new EvalChatResponse.GuardrailsFlag(false, true, false, null, null);
        }
        boolean quoteOnlyOn = guardrailsProperties.getQuoteOnly() != null && guardrailsProperties.getQuoteOnly().isEnabled();
        boolean reflectionOn =
                guardrailsProperties.getReflection() != null && guardrailsProperties.getReflection().isEnabled();
        String quoteOnlyScopeCap = null;
        List<String> quoteOnlyScopesSupported = null;
        if (quoteOnlyOn && guardrailsProperties.getQuoteOnly() != null) {
            String rawScope = guardrailsProperties.getQuoteOnly().getScope();
            quoteOnlyScopeCap =
                    rawScope != null && !rawScope.isBlank()
                            ? rawScope.trim().toLowerCase(Locale.ROOT).replace('-', '_')
                            : "digits_plus_tokens";
            quoteOnlyScopesSupported = EvalQuoteOnlyGuard.supportedScopeConfigNames();
        }
        return new EvalChatResponse.GuardrailsFlag(
                quoteOnlyOn, true, reflectionOn, quoteOnlyScopeCap, quoteOnlyScopesSupported);
    }

    /**
     * 将 {@link EvalChatResponse.GuardrailsFlag} 转为 SSE / 动态 {@code meta} 使用的 snake_case Map（与评测 JSON 对齐）。
     */
    public static Map<String, Object> guardrailsToSnakeCaseMap(EvalChatResponse.GuardrailsFlag g) {
        if (g == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quote_only", g.isQuoteOnly());
        m.put("evidence_map", g.isEvidenceMap());
        m.put("reflection", g.isReflection());
        if (g.getQuoteOnlyScope() != null) {
            m.put("quote_only_scope", g.getQuoteOnlyScope());
        }
        if (g.getQuoteOnlyScopesSupported() != null && !g.getQuoteOnlyScopesSupported().isEmpty()) {
            m.put("quote_only_scopes_supported", List.copyOf(g.getQuoteOnlyScopesSupported()));
        }
        return m;
    }

    /**
     * 写入 {@code meta.capabilities.guardrails}，供 SSE 首条 {@code type=meta} 与 eval 同源声明 quote-only / scope。
     */
    public static void putGuardrailsCapabilitiesSlice(Map<String, Object> metaExtra, GuardrailsProperties guardrailsProperties) {
        if (metaExtra == null) {
            return;
        }
        if (metaExtra.containsKey("capabilities")) {
            return;
        }
        EvalChatResponse.GuardrailsFlag g = guardrailsFromProperties(guardrailsProperties);
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("guardrails", guardrailsToSnakeCaseMap(g));
        metaExtra.put("capabilities", caps);
    }
}
