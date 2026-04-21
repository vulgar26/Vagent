package com.vagent.rag.gate;

import com.vagent.chat.rag.EmptyHitsBehavior;
import com.vagent.kb.dto.RetrieveHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索之后、调用主 LLM 之前的统一门控结论，供 {@code POST /api/v1/eval/chat} 与 {@link com.vagent.chat.RagStreamChatService} 共用，
 * 与 {@code plans/vagent-upgrade.md} P1-0「空命中 / 低置信」口径对齐。
 *
 * <p><b>调用方传入的 {@code query} 语义（须与检索实际用语对齐理解）：</b>
 * <ul>
 *   <li><b>评测</b>（{@link com.vagent.eval.EvalChatController}）：{@code query} 与 {@code KnowledgeRetrieveService#searchForRag} 使用的是<strong>同一段</strong>用户题面（trim 后）。</li>
 *   <li><b>SSE 主链路</b>（{@link com.vagent.chat.RagStreamChatService}）：检索使用
 *       {@link com.vagent.orchestration.QueryRewriteService#rewriteForRetrieval} 产出的 {@code retrievalQuery}；
 *       当前实现里 {@code shortCircuitAfterRetrieve} 的 {@code query} 传入的是<strong>本轮用户原句</strong>（{@code userMessage}），用于「过短」与「模糊子串」规则，
 *       与检索字符串<strong>可以不一致</strong>。若将来要与「实际参与检索的字符串」完全同一，应改为传入 {@code rewrite.retrievalQuery()} 并在策划书中更新本节。</li>
 * </ul>
 */
public final class RagPostRetrieveGate {

    /** 与 {@link com.vagent.eval.EvalChatController} 中过短 query 门控一致。 */
    public static final int DEFAULT_MIN_QUERY_CHARS = 3;

    /** 与 {@link com.vagent.eval.EvalChatController} 默认文案一致。 */
    public static final String MSG_EMPTY_HITS =
            "知识库未检索到相关片段，请尝试补充关键词或更具体的问题描述。";

    public static final String MSG_QUERY_TOO_SHORT = "你的问题描述过短，请补充更多上下文或关键词。";

    public static final String MSG_LOW_CONFIDENCE =
            "当前检索结果置信度不足，或问题指代不够明确；请补充具体对象、范围或关键词后再试。";

    private RagPostRetrieveGate() {}

    public enum LowConfidenceBehavior {
        DENY,
        CLARIFY,
        ALLOW_LLM;

        public static LowConfidenceBehavior fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return CLARIFY;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "deny" -> DENY;
                case "allow-llm", "allow_llm", "allowllm" -> ALLOW_LLM;
                default -> CLARIFY;
            };
        }
    }

    public enum LowConfidenceRule {
        QUERY_TOO_SHORT,
        WEAK_TOP_HIT_DISTANCE,
        VAGUE_QUERY_REFERENCE;

        public static LowConfidenceRule fromToken(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            String v = token.trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "query_too_short" -> QUERY_TOO_SHORT;
                case "weak_top_hit_distance", "weak_distance", "distance" -> WEAK_TOP_HIT_DISTANCE;
                case "vague_query_reference", "vague_substring", "vague" -> VAGUE_QUERY_REFERENCE;
                default -> null;
            };
        }
    }

    public static Set<LowConfidenceRule> parseLowConfidenceRuleSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of(LowConfidenceRule.QUERY_TOO_SHORT, LowConfidenceRule.WEAK_TOP_HIT_DISTANCE, LowConfidenceRule.VAGUE_QUERY_REFERENCE);
        }
        Set<LowConfidenceRule> out =
                java.util.Arrays.stream(raw.split(","))
                        .map(LowConfidenceRule::fromToken)
                        .filter(r -> r != null)
                        .collect(Collectors.toUnmodifiableSet());
        return out.isEmpty()
                ? Set.of(LowConfidenceRule.QUERY_TOO_SHORT, LowConfidenceRule.WEAK_TOP_HIT_DISTANCE, LowConfidenceRule.VAGUE_QUERY_REFERENCE)
                : out;
    }

    /**
     * @param query 用于「过短」与「模糊子串」判定（trim 后比长度、{@code contains} 子串）；须与调用方约定是否与检索 query 同形（见类 Javadoc）。
     * @param zeroHitsPolicy {@link ZeroHitsPolicy#EVAL_ALIGNED}：0 命中一律走澄清+{@code RETRIEVE_EMPTY}（评测默认）；
     *                       {@link ZeroHitsPolicy#RESPECT_RAG_PROPERTIES}：按 {@link EmptyHitsBehavior} 区分 NO_LLM 固定文案与 ALLOW_LLM 放行
     */
    public static Optional<ShortCircuit> shortCircuitAfterRetrieve(
            String query,
            List<RetrieveHit> hits,
            int minQueryChars,
            Double lowConfidenceCosineDistanceThreshold,
            List<String> lowConfidenceQuerySubstrings,
            ZeroHitsPolicy zeroHitsPolicy,
            EmptyHitsBehavior emptyHitsBehavior,
            String configuredEmptyNoLlmMessage,
            LowConfidenceBehavior lowConfidenceBehavior,
            Set<LowConfidenceRule> lowConfidenceRuleSet) {
        int hitCount = hits == null ? 0 : hits.size();
        String q = query == null ? "" : query.trim();

        if (hitCount == 0) {
            if (zeroHitsPolicy == ZeroHitsPolicy.EVAL_ALIGNED) {
                return Optional.of(
                        new ShortCircuit(
                                MSG_EMPTY_HITS,
                                "clarify",
                                "RETRIEVE_EMPTY",
                                true,
                                List.of("EMPTY_HITS")));
            }
            if (emptyHitsBehavior == EmptyHitsBehavior.NO_LLM) {
                String msg =
                        configuredEmptyNoLlmMessage != null && !configuredEmptyNoLlmMessage.isBlank()
                                ? configuredEmptyNoLlmMessage.trim()
                                : MSG_EMPTY_HITS;
                return Optional.of(
                        new ShortCircuit(msg, "clarify", "RETRIEVE_EMPTY", true, List.of("EMPTY_HITS")));
            }
            return Optional.empty();
        }

        List<String> reasons =
                computeLowConfidenceReasons(
                        q,
                        hits,
                        minQueryChars,
                        lowConfidenceCosineDistanceThreshold,
                        lowConfidenceQuerySubstrings,
                        lowConfidenceRuleSet);
        if (!reasons.isEmpty()) {
            if (lowConfidenceBehavior == null) {
                lowConfidenceBehavior = LowConfidenceBehavior.CLARIFY;
            }
            if (lowConfidenceBehavior == LowConfidenceBehavior.ALLOW_LLM) {
                return Optional.empty();
            }
            String behavior = lowConfidenceBehavior == LowConfidenceBehavior.DENY ? "deny" : "clarify";
            String msg = reasons.contains("QUERY_TOO_SHORT") ? MSG_QUERY_TOO_SHORT : MSG_LOW_CONFIDENCE;
            return Optional.of(new ShortCircuit(msg, behavior, "RETRIEVE_LOW_CONFIDENCE", true, List.copyOf(reasons)));
        }

        return Optional.empty();
    }

    /**
     * 仅对「hitCount&gt;0」的场景计算低置信原因；调用方可用于 {@code allow-llm} 路径打标而不短路。
     */
    public static List<String> computeLowConfidenceReasons(
            String query,
            List<RetrieveHit> hits,
            int minQueryChars,
            Double lowConfidenceCosineDistanceThreshold,
            List<String> lowConfidenceQuerySubstrings,
            Set<LowConfidenceRule> lowConfidenceRuleSet) {
        String q = query == null ? "" : query.trim();
        Set<LowConfidenceRule> rules =
                lowConfidenceRuleSet == null || lowConfidenceRuleSet.isEmpty()
                        ? Set.of(LowConfidenceRule.QUERY_TOO_SHORT, LowConfidenceRule.WEAK_TOP_HIT_DISTANCE, LowConfidenceRule.VAGUE_QUERY_REFERENCE)
                        : lowConfidenceRuleSet;
        ArrayList<String> reasons = new ArrayList<>(2);
        if (rules.contains(LowConfidenceRule.QUERY_TOO_SHORT) && q.length() < minQueryChars) {
            reasons.add("QUERY_TOO_SHORT");
            return List.copyOf(reasons);
        }
        boolean distLow =
                rules.contains(LowConfidenceRule.WEAK_TOP_HIT_DISTANCE)
                        && isDistanceLowConfidence(hits, lowConfidenceCosineDistanceThreshold);
        boolean vagueLow =
                rules.contains(LowConfidenceRule.VAGUE_QUERY_REFERENCE)
                        && isVagueSubstringLowConfidence(q, lowConfidenceQuerySubstrings);
        if (distLow) {
            reasons.add("WEAK_TOP_HIT_DISTANCE");
        }
        if (vagueLow) {
            reasons.add("VAGUE_QUERY_REFERENCE");
        }
        return reasons.isEmpty() ? List.of() : List.copyOf(reasons);
    }

    /** 0 命中且允许走 LLM 时，写入 SSE/eval 风格 meta 的前缀字段。 */
    public static void applyZeroHitsAllowLlmMeta(java.util.Map<String, Object> meta) {
        meta.put("retrieve_hit_count", 0);
        meta.put("low_confidence", true);
        meta.put("low_confidence_reasons", List.of("EMPTY_HITS"));
        meta.put("low_confidence_gate", "empty_hits_allow_llm");
    }

    private static boolean isDistanceLowConfidence(List<RetrieveHit> orderedHits, Double threshold) {
        if (threshold == null || orderedHits == null || orderedHits.isEmpty()) {
            return false;
        }
        double d = orderedHits.get(0).getDistance();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        return d > threshold;
    }

    private static boolean isVagueSubstringLowConfidence(String query, List<String> subs) {
        if (subs == null || subs.isEmpty() || query == null) {
            return false;
        }
        for (String s : subs) {
            if (s != null && !s.isBlank() && query.contains(s.strip())) {
                return true;
            }
        }
        return false;
    }

    public enum ZeroHitsPolicy {
        /** 与评测接口一致：0 命中即澄清，不调 LLM。 */
        EVAL_ALIGNED,
        /** 尊重 {@link com.vagent.chat.rag.RagProperties#getEmptyHitsBehavior()}。 */
        RESPECT_RAG_PROPERTIES
    }

    /**
     * @param behavior {@code clarify} 或 {@code deny}（与 eval SSE 约定一致）
     */
    public record ShortCircuit(
            String answer, String behavior, String errorCode, boolean lowConfidence, List<String> lowConfidenceReasons) {

        public ShortCircuit {
            answer = answer != null ? answer : "";
            behavior = behavior != null ? behavior : "clarify";
            errorCode = errorCode != null ? errorCode : "";
            lowConfidenceReasons =
                    lowConfidenceReasons == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(lowConfidenceReasons));
        }
    }
}
