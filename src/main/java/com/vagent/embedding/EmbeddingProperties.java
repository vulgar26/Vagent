package com.vagent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 嵌入与分块配置（维度须与 {@code schema-vector.sql} 中 {@code vector(N)} 及 {@code KbChunkMapper} 中 CAST 一致）。
 * <p>
 * {@code provider}：{@code hash} 为本地确定性占位；{@code dashscope} 为 U2 通义千问兼容嵌入（需 API Key）。
 */
@ConfigurationProperties(prefix = "vagent.embedding")
public class EmbeddingProperties {

    /** 实现名：{@code hash} 为本地确定性占位。 */
    private String provider = "hash";

    /** 向量维度，须与 {@code schema-vector.sql} 中 {@code vector(N)} 及检索 SQL 中 {@code CAST(... AS vector(N))} 一致（U2 默认 1024）。 */
    private int dimensions = 1024;

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
