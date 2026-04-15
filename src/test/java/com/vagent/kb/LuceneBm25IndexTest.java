package com.vagent.kb;

import com.vagent.kb.dto.KbChunkIndexRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneBm25IndexTest {

    @Test
    void search_returns_best_matching_chunk() {
        KbChunkIndexRow a = new KbChunkIndexRow();
        a.setChunkId("c1");
        a.setDocumentId("d1");
        a.setContent("这是第一节内容，包含关键字：电池寿命 和 充电。");

        KbChunkIndexRow b = new KbChunkIndexRow();
        b.setChunkId("c2");
        b.setDocumentId("d1");
        b.setContent("这是第二节内容，讨论售后与保修政策。");

        try (LuceneBm25Index idx = LuceneBm25Index.build(List.of(a, b))) {
            var hits = idx.search("电池寿命", 3);
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).getChunkId()).isEqualTo("c1");
            assertThat(hits.get(0).getContent()).contains("电池寿命");
        }
    }
}

