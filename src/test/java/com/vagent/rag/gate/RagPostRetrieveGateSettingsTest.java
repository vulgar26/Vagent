package com.vagent.rag.gate;

import com.vagent.eval.EvalApiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPostRetrieveGateSettingsTest {

    @Test
    void prefersRagGateDistanceOverEval() {
        RagGateProperties gate = new RagGateProperties();
        gate.setLowConfidenceCosineDistanceThreshold(0.88);
        EvalApiProperties eval = new EvalApiProperties();
        eval.setLowConfidenceCosineDistanceThreshold(0.11);
        RagPostRetrieveGateSettings s = new RagPostRetrieveGateSettings(gate, eval);
        assertThat(s.lowConfidenceCosineDistanceThreshold()).isEqualTo(0.88);
    }

    @Test
    void fallsBackToEvalDistanceWhenGateUnset() {
        RagGateProperties gate = new RagGateProperties();
        EvalApiProperties eval = new EvalApiProperties();
        eval.setLowConfidenceCosineDistanceThreshold(0.33);
        RagPostRetrieveGateSettings s = new RagPostRetrieveGateSettings(gate, eval);
        assertThat(s.lowConfidenceCosineDistanceThreshold()).isEqualTo(0.33);
    }

    @Test
    void prefersRagGateSubstringsWhenNonEmpty() {
        RagGateProperties gate = new RagGateProperties();
        gate.setLowConfidenceQuerySubstrings(List.of("alpha"));
        EvalApiProperties eval = new EvalApiProperties();
        eval.setLowConfidenceQuerySubstrings(List.of("beta"));
        RagPostRetrieveGateSettings s = new RagPostRetrieveGateSettings(gate, eval);
        assertThat(s.lowConfidenceQuerySubstrings()).containsExactly("alpha");
    }

    @Test
    void fallsBackToEvalSubstringsWhenGateEmpty() {
        RagGateProperties gate = new RagGateProperties();
        gate.setLowConfidenceQuerySubstrings(List.of());
        EvalApiProperties eval = new EvalApiProperties();
        eval.setLowConfidenceQuerySubstrings(List.of("gamma"));
        RagPostRetrieveGateSettings s = new RagPostRetrieveGateSettings(gate, eval);
        assertThat(s.lowConfidenceQuerySubstrings()).containsExactly("gamma");
    }
}
