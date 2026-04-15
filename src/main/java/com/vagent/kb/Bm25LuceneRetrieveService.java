package com.vagent.kb;

import com.vagent.kb.dto.KbChunkIndexRow;
import com.vagent.kb.dto.RetrieveHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第二检索系统（BM25）：按用户维护 Lucene 倒排索引，并提供关键词检索 TopK。
 * <p>
 * 当前阶段的取舍：索引在首次使用时构建并缓存，按 TTL 过期重建；后续可升级为“入库增量更新 + 持久化索引目录”。</p>
 */
@Service
public class Bm25LuceneRetrieveService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final KbChunkMapper kbChunkMapper;
    private final Duration ttl;
    private final Map<String, CachedIndex> cache = new ConcurrentHashMap<>();

    @Autowired
    public Bm25LuceneRetrieveService(KbChunkMapper kbChunkMapper) {
        this.kbChunkMapper = kbChunkMapper;
        this.ttl = DEFAULT_TTL;
    }

    public List<RetrieveHit> search(String userId, String queryText, int topK) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        CachedIndex idx = cache.compute(userId, (uid, cur) -> {
            long now = System.currentTimeMillis();
            if (cur == null || cur.isExpired(now, ttl)) {
                if (cur != null) {
                    cur.closeQuietly();
                }
                List<KbChunkIndexRow> rows = kbChunkMapper.listChunksForIndex(uid);
                LuceneBm25Index built = LuceneBm25Index.build(rows);
                return new CachedIndex(built, now);
            }
            return cur;
        });
        return idx.index.search(queryText, topK);
    }

    private record CachedIndex(LuceneBm25Index index, long builtAtMs) {
        boolean isExpired(long nowMs, Duration ttl) {
            long age = nowMs - builtAtMs;
            return age < 0 || age > ttl.toMillis();
        }

        void closeQuietly() {
            try {
                index.close();
            } catch (Exception ignored) {
            }
        }
    }
}

