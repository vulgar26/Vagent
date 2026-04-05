package com.vagent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 嵌入与分块配置（维度须与 {@code schema-vector.sql} 中 {@code vector(N)} 一致）。
 */
@ConfigurationProperties(prefix = "vagent.embedding")
public class EmbeddingProperties {

    /** 实现名：{@code hash} 为本地确定性占位。 */
    private String provider = "hash";

    /** 向量维度，须与库表 {@code kb_chunks.embedding} 一致。 */
    private int dimensions = 128;

    /** 单块最大字符数（近似 token 上限的占位策略）。 */
    private int chunkMaxChars = 512;

    /** 相邻块重叠字符数，须小于 {@link #chunkMaxChars}。 */
    private int chunkOverlap = 64;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getChunkMaxChars() {
        return chunkMaxChars;
    }

    public void setChunkMaxChars(int chunkMaxChars) {
        this.chunkMaxChars = chunkMaxChars;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
