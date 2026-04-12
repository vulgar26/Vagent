package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Day7：一次性门控（无循环、无 LLM 解析）；结论可写入 {@code meta.guardrail_triggered} / {@code reflection_*} / 顶层 {@code error_code}。
 */
public final class EvalReflectionOneShotGuard {

    private EvalReflectionOneShotGuard() {}

    /**
     * @param reflectionEnabled   {@link com.vagent.guardrails.GuardrailsProperties.Reflection#isEnabled()}
     * @param maxAnswerCharsWhenLowConfidence 低置信时 answer 长度上限
     * @param requiresCitations   来自 eval case；true 时要求 {@code sources[]} 非空且 id 均属 allowedHitIds
     * @param hitCount            检索总命中数（与 meta.retrieve_hit_count 一致口径）
     * @param allowedHitIds       本次候选集（前 N）的规范 hit id 集合
     * @param sources             服务端构造的 sources
     * @param lowConfidence       是否与 meta.low_confidence 一致
     * @param answer              当前拟返回的 answer 正文
     */
    public static Optional<Patch> evaluate(
            boolean reflectionEnabled,
            int maxAnswerCharsWhenLowConfidence,
            Boolean requiresCitations,
            int hitCount,
            Set<String> allowedHitIds,
            List<EvalChatResponse.Source> sources,
            boolean lowConfidence,
            String answer) {
        if (!reflectionEnabled) {
            return Optional.empty();
        }
        Optional<Patch> citation = checkCitation(requiresCitations, hitCount, allowedHitIds, sources);
        if (citation.isPresent()) {
            return citation;
        }
        return checkVerboseLowConfidence(lowConfidence, answer, maxAnswerCharsWhenLowConfidence);
    }

    private static Optional<Patch> checkCitation(
            Boolean requiresCitations,
            int hitCount,
            Set<String> allowedHitIds,
            List<EvalChatResponse.Source> sources) {
        if (!Boolean.TRUE.equals(requiresCitations) || hitCount <= 0) {
            return Optional.empty();
        }
        if (sources == null || sources.isEmpty()) {
            return Optional.of(
                    new Patch(
                            "引用不满足检索闭环要求（要求引用但无可引用来源），已拒绝输出。",
                            "deny",
                            "SOURCE_NOT_IN_HITS",
                            "deny",
                            List.of("REQUIRES_CITATIONS_BUT_NO_SOURCES")));
        }
        for (EvalChatResponse.Source s : sources) {
            String id = s != null && s.getId() != null ? s.getId().trim() : "";
            if (id.isEmpty() || !allowedHitIds.contains(id)) {
                return Optional.of(
                        new Patch(
                                "引用不满足检索闭环要求，已拒绝输出。",
                                "deny",
                                "SOURCE_NOT_IN_HITS",
                                "deny",
                                List.of("SOURCE_NOT_IN_HITS")));
            }
        }
        return Optional.empty();
    }

    private static Optional<Patch> checkVerboseLowConfidence(
            boolean lowConfidence, String answer, int maxAnswerCharsWhenLowConfidence) {
        if (!lowConfidence || maxAnswerCharsWhenLowConfidence <= 0) {
            return Optional.empty();
        }
        String a = answer != null ? answer : "";
        if (a.length() <= maxAnswerCharsWhenLowConfidence) {
            return Optional.empty();
        }
        return Optional.of(
                new Patch(
                        "低置信场景下回答超过长度上限，已拒绝输出。",
                        "deny",
                        "GUARDRAIL_TRIGGERED",
                        "deny",
                        List.of("ANSWER_EXCEEDS_LIMIT_UNDER_LOW_CONFIDENCE")));
    }

    public record Patch(String answer, String behavior, String errorCode, String reflectionOutcome, List<String> reflectionReasons) {}
}
