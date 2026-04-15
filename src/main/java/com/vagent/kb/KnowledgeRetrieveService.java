package com.vagent.kb;

import com.vagent.chat.rag.RagProperties;
import com.vagent.embedding.EmbeddingClient;
import com.vagent.kb.dto.RetrieveHit;
import com.vagent.user.UserIdFormats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 向量检索：查询句嵌入 + pgvector 顺序扫描 Top-K（同用户隔离）。
 * <p>
 * U4：Micrometer 计时器 {@code vagent.rag.retrieve}。
 * <p>
 * U5：对话流使用 {@link #searchForRag}，可合并第二路全表召回；{@link #search} 仅用于 API，仅主路。
 * <p>P1-0b：{@link #searchForRag} 返回 {@link RagRetrieveResult}，包含可选 hybrid（向量 + 关键词 RRF）与 rerank 占位归因（默认关闭）。</p>
 */
@Service
public class KnowledgeRetrieveService {

    private final KbChunkMapper kbChunkMapper;
    private final EmbeddingClient embeddingClient;
    private final MeterRegistry meterRegistry;

    public KnowledgeRetrieveService(
            KbChunkMapper kbChunkMapper, EmbeddingClient embeddingClient, MeterRegistry meterRegistry) {
        this.kbChunkMapper = kbChunkMapper;
        this.embeddingClient = embeddingClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * KB 直连接口：仅当前用户隔离检索，不触发 U5 第二路。
     */
    @Transactional(readOnly = true)
    public List<RetrieveHit> search(UUID userId, String query, int topK) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String uid = UserIdFormats.canonical(userId);
            float[] q = embeddingClient.embed(query);
            String qv = VectorFormats.toPgVectorLiteral(q);
            List<RetrieveHit> hits = kbChunkMapper.searchNearest(uid, qv, topK);
            for (RetrieveHit h : hits) {
                h.setSource("primary");
            }
            return hits;
        } finally {
            sample.stop(Timer.builder("vagent.rag.retrieve").register(meterRegistry));
        }
    }

    /**
     * RAG 流式对话：先主路，再按配置决定是否合并第二路全局向量、去重截断。
     */
    @Transactional(readOnly = true)
    public RagRetrieveResult searchForRag(UUID userId, String query, RagProperties ragProps) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int topK = ragProps.getTopK();
            String uid = UserIdFormats.canonical(userId);
            float[] q = embeddingClient.embed(query);
            String qv = VectorFormats.toPgVectorLiteral(q);
            List<RetrieveHit> primary = kbChunkMapper.searchNearest(uid, qv, topK);
            for (RetrieveHit h : primary) {
                h.setSource("primary");
            }

            RagProperties.Hybrid hy = ragProps.getHybrid();
            boolean hybridOn = hy != null && hy.isEnabled();
            String lexicalOutcome = "skipped";
            String lexicalModeUsed = "skipped";
            List<RetrieveHit> lexicalHits = List.of();
            List<RetrieveHit> fused = primary;
            if (hybridOn && hy != null) {
                String mode = hy.getLexicalMode() == null ? "ilike" : hy.getLexicalMode().trim().toLowerCase(Locale.ROOT);
                int lexK = Math.max(1, hy.getLexicalTopK());
                if ("tsvector".equals(mode)) {
                    String qtext = QueryTextLimiter.trimAndLimit(query, 500);
                    if (qtext.isEmpty()) {
                        lexicalOutcome = "skipped";
                        lexicalModeUsed = "skipped";
                    } else {
                        lexicalModeUsed = "tsvector";
                        try {
                            List<RetrieveHit> lex = kbChunkMapper.searchLexicalTsvector(uid, qtext, lexK);
                            for (RetrieveHit h : lex) {
                                h.setSource("lexical");
                            }
                            lexicalHits = lex;
                            lexicalOutcome = "ok";
                            fused = RrfHitFusion.fuse(primary, lex, topK, hy.getRrfK());
                        } catch (RuntimeException e) {
                            lexicalOutcome = "error";
                            fused = primary;
                        }
                    }
                } else {
                    String pattern = LexicalPatternBuilder.buildContainsPattern(query, 300);
                    if (pattern == null) {
                        lexicalOutcome = "skipped";
                        lexicalModeUsed = "skipped";
                    } else {
                        lexicalModeUsed = "ilike";
                        try {
                            List<RetrieveHit> lex = kbChunkMapper.searchLexical(uid, pattern, lexK);
                            for (RetrieveHit h : lex) {
                                h.setSource("lexical");
                            }
                            lexicalHits = lex;
                            lexicalOutcome = "ok";
                            fused = RrfHitFusion.fuse(primary, lex, topK, hy.getRrfK());
                        } catch (RuntimeException e) {
                            lexicalOutcome = "error";
                            fused = primary;
                        }
                    }
                }
            }

            RagProperties.SecondPath sp = ragProps.getSecondPath();
            List<RetrieveHit> afterSecond = fused;
            if (sp != null && sp.isEnabled() && sp.isCrossTenant()) {
                if (shouldTriggerSecondPath(fused, query, sp)) {
                    List<RetrieveHit> global = kbChunkMapper.searchNearestGlobal(qv, sp.getTopK());
                    for (RetrieveHit h : global) {
                        h.setSource("global");
                    }
                    afterSecond = RetrieveHitMerge.mergeAndTakeTop(fused, global, topK);
                }
            }

            RagProperties.Rerank rr = ragProps.getRerank();
            boolean rerankOn = rr != null && rr.isEnabled();
            String rerankOutcome = "skipped";
            Long rerankLatencyMs = null;
            List<RetrieveHit> finalHits = afterSecond;
            if (rerankOn) {
                long t0 = System.nanoTime();
                // 供应商 rerank 尚未接入：显式 skipped，主路径不失败（对齐 vagent-upgrade 降级矩阵）
                rerankOutcome = "skipped";
                rerankLatencyMs = (System.nanoTime() - t0) / 1_000_000L;
            }

            Integer primaryChunkIdCount = null;
            Integer lexicalChunkIdCount = null;
            Integer fusedChunkIdCount = null;
            Double chunkIdDeltaRate = null;
            if (hybridOn) {
                Set<String> p = chunkIdSet(primary);
                Set<String> l = chunkIdSet(lexicalHits);
                Set<String> f = chunkIdSet(fused);
                primaryChunkIdCount = p.size();
                lexicalChunkIdCount = l.size();
                fusedChunkIdCount = f.size();
                chunkIdDeltaRate = jaccardDistance(p, f);
            }

            return new RagRetrieveResult(
                    finalHits,
                    hybridOn,
                    lexicalOutcome,
                    lexicalModeUsed,
                    primaryChunkIdCount,
                    lexicalChunkIdCount,
                    fusedChunkIdCount,
                    chunkIdDeltaRate,
                    rerankOn,
                    rerankOutcome,
                    rerankLatencyMs);
        } finally {
            sample.stop(Timer.builder("vagent.rag.retrieve").register(meterRegistry));
        }
    }

    private static boolean shouldTriggerSecondPath(
            List<RetrieveHit> primary, String query, RagProperties.SecondPath sp) {
        if (sp.isTriggerOnEmpty() && primary.isEmpty()) {
            return true;
        }
        if (sp.getMinPrimaryHitsBelow() > 0 && primary.size() < sp.getMinPrimaryHitsBelow()) {
            return true;
        }
        String t = query == null ? "" : query.trim();
        if (sp.getMinQueryLength() > 0 && t.length() < sp.getMinQueryLength()) {
            return true;
        }
        return false;
    }

    private static Set<String> chunkIdSet(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Set.of();
        }
        HashSet<String> out = new HashSet<>(hits.size() * 2);
        for (RetrieveHit h : hits) {
            if (h == null) {
                continue;
            }
            String id = h.getChunkId();
            if (id != null && !id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    /** Jaccard distance：1 - |A∩B|/|A∪B|，空集 vs 空集 视为 0。 */
    private static double jaccardDistance(Set<String> a, Set<String> b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
            return 0.0;
        }
        Set<String> aa = a == null ? Set.of() : a;
        Set<String> bb = b == null ? Set.of() : b;
        int intersection = 0;
        // iterate smaller set
        Set<String> small = aa.size() <= bb.size() ? aa : bb;
        Set<String> large = aa.size() <= bb.size() ? bb : aa;
        for (String x : small) {
            if (large.contains(x)) {
                intersection++;
            }
        }
        int union = aa.size() + bb.size() - intersection;
        if (union <= 0) {
            return 0.0;
        }
        return 1.0 - ((double) intersection / (double) union);
    }
}
