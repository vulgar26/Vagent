package com.vagent.kb;

import com.vagent.chat.rag.RagProperties;
import com.vagent.embedding.EmbeddingClient;
import com.vagent.kb.dto.RetrieveHit;
import com.vagent.user.UserIdFormats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 向量检索：查询句嵌入 + pgvector 顺序扫描 Top-K（同用户隔离）。
 * <p>
 * U4：Micrometer 计时器 {@code vagent.rag.retrieve}。
 * <p>
 * U5：对话流使用 {@link #searchForRag}，可合并第二路全表召回；{@link #search} 仅用于 API，仅主路。
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
            String uid = UserIdFormats.compact(userId);
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
    public List<RetrieveHit> searchForRag(UUID userId, String query, RagProperties ragProps) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int topK = ragProps.getTopK();
            String uid = UserIdFormats.compact(userId);
            float[] q = embeddingClient.embed(query);
            String qv = VectorFormats.toPgVectorLiteral(q);
            List<RetrieveHit> primary = kbChunkMapper.searchNearest(uid, qv, topK);
            for (RetrieveHit h : primary) {
                h.setSource("primary");
            }
            RagProperties.SecondPath sp = ragProps.getSecondPath();
            if (sp == null || !sp.isEnabled() || !sp.isCrossTenant()) {
                return primary;
            }
            if (!shouldTriggerSecondPath(primary, query, sp)) {
                return primary;
            }
            List<RetrieveHit> global = kbChunkMapper.searchNearestGlobal(qv, sp.getTopK());
            for (RetrieveHit h : global) {
                h.setSource("global");
            }
            return RetrieveHitMerge.mergeAndTakeTop(primary, global, topK);
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
}
