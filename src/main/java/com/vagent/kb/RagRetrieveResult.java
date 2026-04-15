package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;

import java.util.List;
import java.util.Map;

/**
 * P1-0b：一次 {@link KnowledgeRetrieveService#searchForRag} 的检索结果 + 可观测归因（hybrid / rerank），
 * 便于 {@code POST /api/v1/eval/chat} 与 SSE meta 对齐 {@code plans/vagent-upgrade.md}。
 */
public final class RagRetrieveResult {

    private final List<RetrieveHit> hits;
    private final boolean hybridEnabled;
    private final String hybridLexicalOutcome;
    private final boolean rerankEnabled;
    private final String rerankOutcome;
    private final Long rerankLatencyMs;

    public RagRetrieveResult(
            List<RetrieveHit> hits,
            boolean hybridEnabled,
            String hybridLexicalOutcome,
            boolean rerankEnabled,
            String rerankOutcome,
            Long rerankLatencyMs) {
        this.hits = hits == null ? List.of() : List.copyOf(hits);
        this.hybridEnabled = hybridEnabled;
        this.hybridLexicalOutcome = hybridLexicalOutcome;
        this.rerankEnabled = rerankEnabled;
        this.rerankOutcome = rerankOutcome;
        this.rerankLatencyMs = rerankLatencyMs;
    }

    /** 纯向量路径（无 hybrid、无 rerank），用于测试 mock 与兼容旧行为。 */
    public static RagRetrieveResult vectorOnly(List<RetrieveHit> hits) {
        return new RagRetrieveResult(hits, false, "skipped", false, "skipped", null);
    }

    public List<RetrieveHit> hits() {
        return hits;
    }

    public boolean hybridEnabled() {
        return hybridEnabled;
    }

    public String hybridLexicalOutcome() {
        return hybridLexicalOutcome;
    }

    public boolean rerankEnabled() {
        return rerankEnabled;
    }

    public String rerankOutcome() {
        return rerankOutcome;
    }

    public Long rerankLatencyMs() {
        return rerankLatencyMs;
    }

    /** 写入 eval 响应 {@code meta}（snake_case，与现有 eval 字段风格一致）。 */
    public void putRetrievalTrace(Map<String, Object> meta) {
        meta.put("hybrid_enabled", hybridEnabled);
        meta.put("hybrid_lexical_outcome", hybridLexicalOutcome);
        meta.put("rerank_enabled", rerankEnabled);
        meta.put("rerank_outcome", rerankOutcome);
        if (rerankLatencyMs != null) {
            meta.put("rerank_latency_ms", rerankLatencyMs);
        }
    }
}
