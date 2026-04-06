package com.vagent.embedding.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 解析 DashScope / OpenAI 兼容 {@code embeddings} 响应 JSON。
 */
public final class DashScopeEmbeddingsResponseParser {

    private DashScopeEmbeddingsResponseParser() {
    }

    public static float[] parseEmbeddingArray(ObjectMapper mapper, String responseBody, int expectedDim)
            throws IOException {
        JsonNode root = mapper.readTree(responseBody);
        if (root.has("error")) {
            String msg = root.path("error").path("message").asText(root.path("error").toString());
            throw new IOException("DashScope embeddings error: " + msg);
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IOException("embeddings response missing data[0]");
        }
        JsonNode emb = data.get(0).path("embedding");
        if (!emb.isArray()) {
            throw new IOException("embeddings response missing embedding array");
        }
        if (emb.size() != expectedDim) {
            throw new IOException(
                    "embedding length mismatch: expected " + expectedDim + ", got " + emb.size());
        }
        float[] v = new float[expectedDim];
        for (int i = 0; i < expectedDim; i++) {
            v[i] = (float) emb.get(i).asDouble();
        }
        return v;
    }
}
