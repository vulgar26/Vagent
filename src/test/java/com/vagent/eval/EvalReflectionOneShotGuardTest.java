package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReflectionOneShotGuardTest {

    @Test
    void disabled_noPatch() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalReflectionOneShotGuard.evaluate(
                        false,
                        500,
                        true,
                        1,
                        Set.of("a"),
                        List.of(),
                        false,
                        "x");
        assertThat(p).isEmpty();
    }

    @Test
    void requiresCitations_emptySources_triggersSourceNotInHits() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalReflectionOneShotGuard.evaluate(
                        true,
                        500,
                        true,
                        3,
                        Set.of("h1"),
                        List.of(),
                        false,
                        "OK");
        assertThat(p).isPresent();
        assertThat(p.get().errorCode()).isEqualTo("SOURCE_NOT_IN_HITS");
        assertThat(p.get().behavior()).isEqualTo("deny");
        assertThat(p.get().reflectionOutcome()).isEqualTo("deny");
        assertThat(p.get().reflectionReasons()).contains("REQUIRES_CITATIONS_BUT_NO_SOURCES");
    }

    @Test
    void requiresCitations_idNotAllowed_triggersSourceNotInHits() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalReflectionOneShotGuard.evaluate(
                        true,
                        500,
                        true,
                        1,
                        Set.of("allowed-id"),
                        List.of(new EvalChatResponse.Source("other-id", "t", "s")),
                        false,
                        "OK");
        assertThat(p).isPresent();
        assertThat(p.get().errorCode()).isEqualTo("SOURCE_NOT_IN_HITS");
        assertThat(p.get().reflectionReasons()).contains("SOURCE_NOT_IN_HITS");
    }

    @Test
    void lowConfidence_longAnswer_triggersGuardrail() {
        String longAns = "x".repeat(501);
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalReflectionOneShotGuard.evaluate(
                        true,
                        500,
                        false,
                        0,
                        Set.of(),
                        List.of(),
                        true,
                        longAns);
        assertThat(p).isPresent();
        assertThat(p.get().errorCode()).isEqualTo("GUARDRAIL_TRIGGERED");
        assertThat(p.get().reflectionReasons()).contains("ANSWER_EXCEEDS_LIMIT_UNDER_LOW_CONFIDENCE");
    }

    @Test
    void citationCheckedBeforeVerbose() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalReflectionOneShotGuard.evaluate(
                        true,
                        5,
                        true,
                        1,
                        Set.of("a"),
                        List.of(),
                        true,
                        "12345678901");
        assertThat(p.get().errorCode()).isEqualTo("SOURCE_NOT_IN_HITS");
    }
}
