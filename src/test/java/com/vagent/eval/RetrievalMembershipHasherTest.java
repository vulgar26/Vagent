package com.vagent.eval;

import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RetrievalMembershipHasherTest {

    @Test
    void evalHashes_stableForFixedInputs() {
        List<String> hashes =
                RetrievalMembershipHasher.buildEvalHitIdHashes(
                        "tok", "t1", "ds", "c1", List.of("chunk-a", "chunk-b"));
        assertEquals(2, hashes.size());
        assertEquals(64, hashes.get(0).length());
        assertFalse(hashes.get(0).equals(hashes.get(1)));
        List<String> again =
                RetrievalMembershipHasher.buildEvalHitIdHashes(
                        "tok", "t1", "ds", "c1", List.of("chunk-a", "chunk-b"));
        assertEquals(hashes, again);
    }

    @Test
    void evalHashes_emptyWhenTokenBlank() {
        assertEquals(
                List.of(),
                RetrievalMembershipHasher.buildEvalHitIdHashes("", "t1", "ds", "c1", List.of("x")));
    }

    @Test
    void sseHashes_differFromEvalForSameIds() {
        UUID uid = UUID.fromString("00000000-0000-4000-8000-000000000001");
        String conv = "conv-1";
        String task = "task-1";
        List<String> ids = List.of("chunk-x");
        List<String> eval =
                RetrievalMembershipHasher.buildEvalHitIdHashes("tok", "t1", "ds", "c1", ids);
        List<String> sse =
                RetrievalMembershipHasher.buildSseHitIdHashes("sse-secret", uid, conv, task, ids);
        assertEquals(1, eval.size());
        assertEquals(1, sse.size());
        assertFalse(eval.get(0).equals(sse.get(0)));
    }

    @Test
    void canonicalHitId_prefersChunkId() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("  c1 ");
        h.setDocumentId("d1");
        assertEquals("c1", RetrievalMembershipHasher.canonicalHitId(h));
    }

    @Test
    void canonicalHitId_fallsBackToDocumentId() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("  ");
        h.setDocumentId(" doc1 ");
        assertEquals("doc1", RetrievalMembershipHasher.canonicalHitId(h));
    }
}
