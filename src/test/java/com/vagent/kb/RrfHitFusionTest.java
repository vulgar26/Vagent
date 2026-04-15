package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfHitFusionTest {

    @Test
    void fuseCombinesTwoRankedLists() {
        RetrieveHit a = hit("a", 0.01);
        RetrieveHit b = hit("b", 0.02);
        RetrieveHit c = hit("c", 0.03);
        List<RetrieveHit> vector = List.of(a, b);
        List<RetrieveHit> lexical = List.of(c, b, a);

        List<RetrieveHit> fused = RrfHitFusion.fuse(vector, lexical, 3, 60);
        assertThat(fused).hasSize(3);
        assertThat(fused).extracting(RetrieveHit::getChunkId).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void fuseSecondaryEmptyReturnsPrimaryHead() {
        RetrieveHit a = hit("a", 0.1);
        RetrieveHit b = hit("b", 0.2);
        assertThat(RrfHitFusion.fuse(List.of(a, b), List.of(), 1, 60)).extracting(RetrieveHit::getChunkId).containsExactly("a");
    }

    private static RetrieveHit hit(String id, double distance) {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId(id);
        h.setDocumentId("d-" + id);
        h.setContent("c");
        h.setDistance(distance);
        return h;
    }
}
