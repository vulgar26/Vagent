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
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
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
                        "custom",
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
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
                        "configured-empty-msg",
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
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
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isPresent();
        assertThat(r.get().lowConfidenceReasons()).containsExactly("QUERY_TOO_SHORT");
    }

    @Test
    void nullDistanceThreshold_neverTriggersWeakTopHit() {
        RetrieveHit hit = new RetrieveHit();
        hit.setChunkId("c1");
        hit.setDocumentId("d1");
        hit.setDistance(99.0);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "long enough query",
                        List.of(hit),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        null,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isEmpty();
    }

    @Test
    void weakTopHitDistance_whenAboveThreshold() {
        RetrieveHit hit = new RetrieveHit();
        hit.setChunkId("c1");
        hit.setDocumentId("d1");
        hit.setDistance(0.99);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "long enough query",
                        List.of(hit),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        0.5,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isPresent();
        assertThat(r.get().errorCode()).isEqualTo("RETRIEVE_LOW_CONFIDENCE");
        assertThat(r.get().lowConfidenceReasons()).containsExactly("WEAK_TOP_HIT_DISTANCE");
    }

    @Test
    void weakTopHitDistance_notTriggered_whenAtOrBelowThreshold() {
        RetrieveHit hit = new RetrieveHit();
        hit.setChunkId("c1");
        hit.setDocumentId("d1");
        hit.setDistance(0.5);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "long enough query",
                        List.of(hit),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        0.5,
                        List.of(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isEmpty();
    }

    @Test
    void vagueQuerySubstring_whenQueryContainsConfiguredSubstring() {
        RetrieveHit hit = new RetrieveHit();
        hit.setChunkId("c1");
        hit.setDocumentId("d1");
        hit.setDistance(0.1);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "请解释这个方案",
                        List.of(hit),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        null,
                        List.of("这个"),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isPresent();
        assertThat(r.get().lowConfidenceReasons()).containsExactly("VAGUE_QUERY_REFERENCE");
    }

    @Test
    void distanceAndVagueBothReasons_whenBothMatch() {
        RetrieveHit hit = new RetrieveHit();
        hit.setChunkId("c1");
        hit.setDocumentId("d1");
        hit.setDistance(0.99);
        Optional<RagPostRetrieveGate.ShortCircuit> r =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        "请解释这个方案",
                        List.of(hit),
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        0.5,
                        List.of("这个"),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.CLARIFY,
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(null));
        assertThat(r).isPresent();
        assertThat(r.get().lowConfidenceReasons())
                .containsExactly("WEAK_TOP_HIT_DISTANCE", "VAGUE_QUERY_REFERENCE");
    }

    @Test
    void applyZeroHitsAllowLlmMeta_setsRetrieveAndLowConfidenceFlags() {
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        RagPostRetrieveGate.applyZeroHitsAllowLlmMeta(meta);
        assertThat(meta.get("retrieve_hit_count")).isEqualTo(0);
        assertThat(meta.get("low_confidence")).isEqualTo(true);
        assertThat(meta.get("low_confidence_reasons")).isEqualTo(List.of("EMPTY_HITS"));
    }
}
