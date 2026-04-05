package com.vagent.orchestration.model;

/**
 * 查询改写输出：当前仅承载「用于向量检索的 query 文本」。
 * <p>
 * 后续若增加子问题拆分，可在此扩展 {@code subQueries} 等字段，而无需改控制器签名。
 */
public record RewriteResult(String retrievalQuery) {

    public RewriteResult {
        if (retrievalQuery == null || retrievalQuery.isBlank()) {
            throw new IllegalArgumentException("retrievalQuery must not be blank");
        }
    }
}
