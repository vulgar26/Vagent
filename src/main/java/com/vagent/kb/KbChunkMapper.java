package com.vagent.kb;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vagent.kb.dto.RetrieveHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * 按余弦距离 {@code <=>} 取当前用户下最近的若干块（顺序扫描；大数据量需 ANN 索引）。
     * <p>
     * {@code vector(1024)} 须与 {@code schema-vector.sql} 及 {@code vagent.embedding.dimensions} 同步修改。
     */
    @Select("""
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   c.content AS content,
                   (c.embedding <=> CAST(#{qv} AS vector(1024))) AS distance
            FROM kb_chunks c
            WHERE c.user_id = CAST(#{userId} AS uuid)
            ORDER BY c.embedding <=> CAST(#{qv} AS vector(1024))
            LIMIT #{topK}
            """)
    List<RetrieveHit> searchNearest(
            @Param("userId") String userId,
            @Param("qv") String qv,
            @Param("topK") int topK);

    /**
     * U5：全表最近邻（不按用户隔离）；仅应在显式开启第二路且接受跨租户语义时使用。
     */
    @Select("""
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   c.content AS content,
                   (c.embedding <=> CAST(#{qv} AS vector(1024))) AS distance
            FROM kb_chunks c
            ORDER BY c.embedding <=> CAST(#{qv} AS vector(1024))
            LIMIT #{topK}
            """)
    List<RetrieveHit> searchNearestGlobal(@Param("qv") String qv, @Param("topK") int topK);

    /**
     * P1-0b：关键词通道（ILIKE 子串匹配）。仅用于 hybrid；pattern 须由调用方转义并限制长度。
     */
    @Select("""
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   c.content AS content,
                   1.0 AS distance
            FROM kb_chunks c
            WHERE c.user_id = CAST(#{userId} AS uuid)
              AND c.content ILIKE CAST(#{pattern} AS text)
            LIMIT #{topK}
            """)
    List<RetrieveHit> searchLexical(
            @Param("userId") String userId, @Param("pattern") String pattern, @Param("topK") int topK);

    /**
     * P1-0b+：全文检索（{@code content_tsv} @@ plainto_tsquery）。distance 为伪距离：越小表示 ts_rank_cd 越大越相关。
     */
    @Select("""
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   c.content AS content,
                   (1.0 / (1.0 + ts_rank_cd(c.content_tsv, plainto_tsquery('simple', CAST(#{queryText} AS text))))) AS distance
            FROM kb_chunks c
            WHERE c.user_id = CAST(#{userId} AS uuid)
              AND c.content_tsv @@ plainto_tsquery('simple', CAST(#{queryText} AS text))
            ORDER BY distance ASC
            LIMIT #{topK}
            """)
    List<RetrieveHit> searchLexicalTsvector(
            @Param("userId") String userId, @Param("queryText") String queryText, @Param("topK") int topK);
}
