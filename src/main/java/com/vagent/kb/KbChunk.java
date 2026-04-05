package com.vagent.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vagent.kb.mybatis.PgVectorFloatArrayTypeHandler;

/**
 * 文档分块及向量（度量：余弦距离 {@code <=>}，向量已 L2 归一化）。
 */
@TableName(value = "kb_chunks", autoResultMap = true)
public class KbChunk {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("document_id")
    private String documentId;

    @TableField("user_id")
    private String userId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String content;

    @TableField(value = "embedding", typeHandler = PgVectorFloatArrayTypeHandler.class)
    private float[] embedding;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
