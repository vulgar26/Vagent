package com.vagent.kb.dto;

/**
 * BM25/全文索引构建所需的最小行：chunk 主键 + doc 主键 + 原文内容。
 */
public class KbChunkIndexRow {

    private String chunkId;
    private String documentId;
    private String content;

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

