package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieveHitMergeTest {

    @Test
    void dedupesByChunkIdKeepsBetterDistance() {
        RetrieveHit a = hit("c1", 0.1);
        RetrieveHit b = hit("c1", 0.5);
        RetrieveHit c = hit("c2", 0.2);
        List<RetrieveHit> merged = RetrieveHitMerge.mergeAndTakeTop(List.of(a), List.of(b, c), 10);
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getChunkId()).isEqualTo("c1");
        assertThat(merged.get(0).getDistance()).isEqualTo(0.1);
        assertThat(merged.get(1).getChunkId()).isEqualTo("c2");
    }

    @Test
    void respectsLimit() {
        RetrieveHit a = hit("c1", 0.1);
        RetrieveHit b = hit("c2", 0.2);
        RetrieveHit c = hit("c3", 0.3);
        List<RetrieveHit> merged = RetrieveHitMerge.mergeAndTakeTop(List.of(a), List.of(b, c), 2);
        assertThat(merged).hasSize(2);
    }

    private static RetrieveHit hit(String id, double d) {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId(id);
        h.setDistance(d);
        h.setContent("x");
        return h;
    }
}
