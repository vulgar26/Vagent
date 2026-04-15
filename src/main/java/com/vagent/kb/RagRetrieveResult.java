package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * P1-0b：一次 {@link KnowledgeRetrieveService#searchForRag} 的检索结果 + 可观测归因（hybrid / rerank），
 * 便于 {@code POST /api/v1/eval/chat} 与 SSE meta 对齐 {@code plans/vagent-upgrade.md}。
 */
public final class RagRetrieveResult {

    private final List<RetrieveHit> hits;
    private final boolean hybridEnabled;
    private final String hybridLexicalOutcome;
    /** {@code skipped} | {@code ilike} | {@code tsvector}：关键词通道实现口径。 */
    private final String hybridLexicalMode;
    /** hybrid 融合前向量候选集 chunk_id 去重计数（仅 hybrid 开启时提供）。 */
    private final Integer hybridPrimaryChunkIdCount;
    /** hybrid 融合前词法候选集 chunk_id 去重计数（仅 hybrid 开启时提供）。 */
    private final Integer hybridLexicalChunkIdCount;
    /** hybrid 融合后候选集 chunk_id 去重计数（仅 hybrid 开启时提供）。 */
    private final Integer hybridFusedChunkIdCount;
    /**
     * 融合前后 chunk_id 变化率（Jaccard distance = 1 - |A∩B|/|A∪B|；仅 hybrid 开启且可计算时提供）。
     * 值域：[0,1]，越大表示融合改动越大。
     */
    private final Double hybridChunkIdDeltaRate;
    private final boolean rerankEnabled;
    private final String rerankOutcome;
    private final Long rerankLatencyMs;

    public RagRetrieveResult(
            List<RetrieveHit> hits,
            boolean hybridEnabled,
            String hybridLexicalOutcome,
            String hybridLexicalMode,
            Integer hybridPrimaryChunkIdCount,
            Integer hybridLexicalChunkIdCount,
            Integer hybridFusedChunkIdCount,
            Double hybridChunkIdDeltaRate,
            boolean rerankEnabled,
            String rerankOutcome,
            Long rerankLatencyMs) {
        this.hits = hits == null ? List.of() : List.copyOf(hits);
        this.hybridEnabled = hybridEnabled;
        this.hybridLexicalOutcome = hybridLexicalOutcome;
        this.hybridLexicalMode = hybridLexicalMode == null ? "skipped" : hybridLexicalMode;
        this.hybridPrimaryChunkIdCount = hybridPrimaryChunkIdCount;
        this.hybridLexicalChunkIdCount = hybridLexicalChunkIdCount;
        this.hybridFusedChunkIdCount = hybridFusedChunkIdCount;
        this.hybridChunkIdDeltaRate = hybridChunkIdDeltaRate;
        this.rerankEnabled = rerankEnabled;
        this.rerankOutcome = rerankOutcome;
        this.rerankLatencyMs = rerankLatencyMs;
    }

    /** 纯向量路径（无 hybrid、无 rerank），用于测试 mock 与兼容旧行为。 */
    public static RagRetrieveResult vectorOnly(List<RetrieveHit> hits) {
        return new RagRetrieveResult(hits, false, "skipped", "skipped", null, null, null, null, false, "skipped", null);
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

    public String hybridLexicalMode() {
        return hybridLexicalMode;
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
        meta.put("hybrid_lexical_mode", hybridLexicalMode);
        if (hybridPrimaryChunkIdCount != null) {
            meta.put("hybrid_primary_chunk_id_count", hybridPrimaryChunkIdCount);
        }
        if (hybridLexicalChunkIdCount != null) {
            meta.put("hybrid_lexical_chunk_id_count", hybridLexicalChunkIdCount);
        }
        if (hybridFusedChunkIdCount != null) {
            meta.put("hybrid_fused_chunk_id_count", hybridFusedChunkIdCount);
        }
        if (hybridChunkIdDeltaRate != null) {
            meta.put("hybrid_chunk_id_delta_rate", hybridChunkIdDeltaRate);
        }
        meta.put("rerank_enabled", rerankEnabled);
        meta.put("rerank_outcome", rerankOutcome);
        if (rerankLatencyMs != null) {
            meta.put("rerank_latency_ms", rerankLatencyMs);
        }

        // 距离分布：以当前输出 hits 为口径（融合/second-path/rerank 之后的最终候选集）。
        if (hits != null && !hits.isEmpty()) {
            java.util.ArrayList<Double> distances = new java.util.ArrayList<>(hits.size());
            for (RetrieveHit h : hits) {
                if (h != null) {
                    distances.add(h.getDistance());
                }
            }
            if (!distances.isEmpty()) {
                distances = new java.util.ArrayList<>(distances.stream().sorted().toList());
                double top1 = distances.get(0);
                meta.put("retrieve_top1_distance", top1);
                meta.put("retrieve_top1_distance_bucket", bucketOf(top1));
                meta.put("retrieve_topk_distance_p50", quantile(distances, 0.50));
                meta.put("retrieve_topk_distance_p95", quantile(distances, 0.95));

                // TopK 距离分桶计数（便于 eval 侧做分布对比；桶边界固定，避免前后不一致）。
                Map<String, Integer> buckets = new LinkedHashMap<>();
                for (double d : distances) {
                    String b = bucketOf(d);
                    buckets.put(b, buckets.getOrDefault(b, 0) + 1);
                }
                meta.put("retrieve_topk_distance_buckets", buckets);
            }
        }
    }

    private static double quantile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0.0;
        }
        if (p <= 0) {
            return sorted.get(0);
        }
        if (p >= 1) {
            return sorted.get(sorted.size() - 1);
        }
        int n = sorted.size();
        int idx = (int) Math.floor(p * (n - 1));
        return sorted.get(Math.max(0, Math.min(n - 1, idx)));
    }

    /**
     * 统一的距离桶边界：
     * - 向量余弦距离（归一化向量）常见范围 [0,2]
     * - tsvector 伪距离为 1/(1+rank)，常见落在 (0,1]
     */
    private static String bucketOf(double distance) {
        double d = Double.isFinite(distance) ? distance : Double.POSITIVE_INFINITY;
        double[] edges = new double[] {0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.5, 2.0};
        double start = 0.0;
        for (double e : edges) {
            if (d <= e) {
                return formatRange(start, e);
            }
            start = e;
        }
        return "2.0+";
    }

    private static String formatRange(double start, double end) {
        // 保持稳定字符串，便于 eval 聚合（避免 0.6000000001 之类）。
        return String.format(java.util.Locale.ROOT, "%.1f-%.1f", start, end);
    }
}
