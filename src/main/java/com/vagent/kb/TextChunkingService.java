package com.vagent.kb;

import com.vagent.embedding.EmbeddingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定字符窗口 + 重叠切分正文（占位；后续可换为按 token 的切分器）。
 */
@Service
public class TextChunkingService {

    private final EmbeddingProperties embeddingProperties;

    public TextChunkingService(EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
    }

    /**
     * @param text 原文
     * @return 非重叠块列表；空串返回空列表
     */
    public List<String> split(String text) {
        int max = embeddingProperties.getChunkMaxChars();
        int overlap = embeddingProperties.getChunkOverlap();
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (max <= 0) {
            throw new IllegalArgumentException("chunkMaxChars must be positive");
        }
        int safeOverlap = Math.min(Math.max(0, overlap), max - 1);
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + max, text.length());
            out.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - safeOverlap;
        }
        return out;
    }
}
