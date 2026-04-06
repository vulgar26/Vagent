package com.vagent.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 hash 嵌入：维度与 L2 归一化（不依赖数据库）。
 */
class HashEmbeddingClientTest {

    @Test
    void embedIsNormalizedAndMatchesConfiguredDimensions() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setDimensions(1024);
        HashEmbeddingClient client = new HashEmbeddingClient(p);
        float[] v = client.embed("hello");
        assertEquals(1024, v.length);
        double sum = 0;
        for (float f : v) {
            sum += (double) f * f;
        }
        assertTrue(sum > 0.99 && sum < 1.01, "L2 norm ~1: " + sum);
    }
}
