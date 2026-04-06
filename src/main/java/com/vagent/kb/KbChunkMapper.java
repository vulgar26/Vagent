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
            WHERE c.user_id = #{userId}
            ORDER BY c.embedding <=> CAST(#{qv} AS vector(1024))
            LIMIT #{topK}
            """)
    List<RetrieveHit> searchNearest(
            @Param("userId") String userId,
            @Param("qv") String qv,
            @Param("topK") int topK);
}
