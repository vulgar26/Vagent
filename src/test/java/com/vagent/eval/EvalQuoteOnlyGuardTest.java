package com.vagent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalQuoteOnlyGuardTest {

    @Test
    void relaxed_passesWhenDigitsGrounded() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.RELAXED,
                        "单价 1200 元含税",
                        List.of("商品说明：单价 1200 元含税，详见附件。"));
        assertThat(p).isEmpty();
    }

    @Test
    void relaxed_failsWhenDigitRunNotInCorpus() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.RELAXED,
                        "参考编号 987654321",
                        List.of("文档中只有普通描述，没有该编号。"));
        assertThat(p).isPresent();
        assertThat(p.get().errorCode()).isEqualTo("GUARDRAIL_TRIGGERED");
        assertThat(p.get().reflectionReasons()).contains("QUOTE_ONLY_UNGROUNDED");
    }

    @Test
    void moderate_failsOnLongTokenNotInCorpus() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.MODERATE,
                        "型号 SUPERMODELX 可选",
                        List.of("型号 BASEONLY 说明"));
        assertThat(p).isPresent();
    }

    @Test
    void moderate_passesWhenLongTokenGrounded() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.MODERATE,
                        "型号 SUPERMODELX 可选",
                        List.of("库存 SUPERMODELX 批次说明"));
        assertThat(p).isEmpty();
    }

    @Test
    void strict_requiresAsciiWordInCorpus() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.STRICT,
                        "Please confirm availability immediately",
                        List.of("Please confirm availability")); // missing "immediately"
        assertThat(p).isPresent();
    }

    @Test
    void strict_passesWhenWordsGrounded() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.STRICT,
                        "Please confirm availability immediately",
                        List.of("Please confirm availability immediately in the manual."));
        assertThat(p).isEmpty();
    }

    @Test
    void fromConfig_invalidFallsBackToModerate() {
        assertThat(EvalQuoteOnlyGuard.Strictness.fromConfig("not-a-level"))
                .isEqualTo(EvalQuoteOnlyGuard.Strictness.MODERATE);
        assertThat(EvalQuoteOnlyGuard.Strictness.fromConfig("RELAXED"))
                .isEqualTo(EvalQuoteOnlyGuard.Strictness.RELAXED);
    }
}
