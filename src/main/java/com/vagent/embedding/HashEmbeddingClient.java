package com.vagent.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

/**
 * 基于 SHA-256 种子 + {@link SplittableRandom} 的确定性嵌入（无网络、便于集成测试）。
 * <p>
 * 输出向量经 L2 归一化，与 pgvector 余弦距离运算符 {@code <=>} 一致。
 */
public final class HashEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    public HashEmbeddingClient(EmbeddingProperties properties) {
        this.dimensions = properties.getDimensions();
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
    }

    @Override
    public float[] embed(String text) {
        String input = text == null ? "" : text;
        byte[] seed;
        try {
            seed = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        long s0 = 0;
        for (int i = 0; i < 8 && i < seed.length; i++) {
            s0 = (s0 << 8) | (seed[i] & 0xffL);
        }
        SplittableRandom random = new SplittableRandom(s0);
        float[] v = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            v[i] = random.nextFloat() * 2f - 1f;
        }
        l2Normalize(v);
        return v;
    }

    private static void l2Normalize(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += (double) f * f;
        }
        if (sum == 0) {
            return;
        }
        double inv = 1.0 / Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] * inv);
        }
    }
}
