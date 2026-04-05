package com.vagent.kb;

import com.vagent.embedding.EmbeddingClient;
import com.vagent.kb.dto.RetrieveHit;
import com.vagent.user.UserIdFormats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 向量检索：查询句嵌入 + pgvector 顺序扫描 Top-K（同用户隔离）。
 */
@Service
public class KnowledgeRetrieveService {

    private final KbChunkMapper kbChunkMapper;
    private final EmbeddingClient embeddingClient;

    public KnowledgeRetrieveService(KbChunkMapper kbChunkMapper, EmbeddingClient embeddingClient) {
        this.kbChunkMapper = kbChunkMapper;
        this.embeddingClient = embeddingClient;
    }

    @Transactional(readOnly = true)
    public List<RetrieveHit> search(UUID userId, String query, int topK) {
        String uid = UserIdFormats.compact(userId);
        float[] q = embeddingClient.embed(query);
        String qv = VectorFormats.toPgVectorLiteral(q);
        return kbChunkMapper.searchNearest(uid, qv, topK);
    }
}
