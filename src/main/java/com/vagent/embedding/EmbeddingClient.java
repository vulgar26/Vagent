package com.vagent.embedding;

/**
 * 文本向量嵌入客户端（可替换为远程 Embedding API）。
 * <p>
 * <b>作用：</b> 将自然语言文本转为固定维度浮点向量，供入库与检索；M2 默认 {@link HashEmbeddingClient} 可复现、无外部依赖。
 */
public interface EmbeddingClient {

    /**
     * @param text 非 null；实现可对空串做与业务一致的约定
     * @return 与配置 {@code vagent.embedding.dimensions} 等长的向量（hash 实现已 L2 归一化，便于余弦距离）
     */
    float[] embed(String text);
}
