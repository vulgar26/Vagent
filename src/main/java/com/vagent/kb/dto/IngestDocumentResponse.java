package com.vagent.kb.dto;

/**
 * 入库结果：文档 id 与分块数量。
 */
public class IngestDocumentResponse {

    private String documentId;
    private int chunkCount;

    public IngestDocumentResponse() {
    }

    public IngestDocumentResponse(String documentId, int chunkCount) {
        this.documentId = documentId;
        this.chunkCount = chunkCount;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
