package com.vagent.kb.dto;

/**
 * 单条检索命中（余弦距离越小越相似）。
 */
public class RetrieveHit {

    /** U5：{@code primary} 用户隔离主路；{@code global} 第二路跨租户全局召回（仅配置开启时）。 */
    private String source = "primary";

    private String chunkId;
    private String documentId;
    private String content;
    private double distance;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

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

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
