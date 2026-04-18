package com.vagent.rag.gate;

import com.vagent.chat.rag.EmptyHitsBehavior;
import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RagPostRetrieveGateTest {

    @Test
    void evalAligned_zeroHits_alwaysShortCircuits() {
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "q",
                        List.of(),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        null,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null);
        assertThat(r).isPresent();
        assertThat(r.get().errorCode()).isEqualTo("RETRIEVE_EMPTY");
    }

    @Test
    void respectRag_allowLlm_zeroHits_doesNotShortCircuit() {
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "query text ok",
                        List.of(),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        null,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.RESPECT_RAG_PROPERTIES,
                        EmptyHitsBehavior.ALLOW_LLM,
                        "custom");
        assertThat(r).isEmpty();
    }

    @Test
    void respectRag_noLlm_zeroHits_usesConfiguredMessage() {
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "query text ok",
                        List.of(),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        null,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.RESPECT_RAG_PROPERTIES,
                        EmptyHitsBehavior.NO_LLM,
                        "configured-empty-msg");
        assertThat(r).isPresent();
        assertThat(r.get().answer()).isEqualTo("configured-empty-msg");
    }

    @Test
    void queryTooShort_triggersBeforeDistance() {
        RetrieveHit weak = new RetrieveHit();
        weak.setChunkId("c1");
        weak.setDocumentId("d1");
        weak.setContent("x".repeat(100));
        weak.setDistance(99.0);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "ab",
                        List.of(weak),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        0.1,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null);
        assertThat(r).isPresent();
        assertThat(r.get().lowConfidenceReasons()).containsExactly("QUERY_TOO_SHORT");
    }
}
