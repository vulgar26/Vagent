package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    @Test
    void scopeDigitsOnly_passesWhenLongTokenNotInCorpus() {
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.MODERATE,
                        EvalQuoteOnlyGuard.Scope.DIGITS_ONLY,
                        "型号 SUPERMODELX 可选",
                        List.of("型号 BASEONLY 说明"),
                        null);
        assertThat(p).isEmpty();
    }

    @Test
    void scopeFromConfig_hyphenatedPlusEvidence() {
        assertThat(EvalQuoteOnlyGuard.Scope.fromConfig(null))
                .isEqualTo(EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS);
        assertThat(EvalQuoteOnlyGuard.Scope.fromConfig("digits-plus-tokens-plus-evidence"))
                .isEqualTo(EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS_PLUS_EVIDENCE);
        assertThat(EvalQuoteOnlyGuard.Scope.fromConfig("unknown"))
                .isEqualTo(EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS);
    }

    @Test
    void scopePlusEvidence_failsWhenDigitsNotBoundToEvidenceMap() {
        EvalChatResponse.Source src = new EvalChatResponse.Source("id1", null, "不含数字的摘要");
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.RELAXED,
                        EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS_PLUS_EVIDENCE,
                        "价格为 333 元",
                        List.of("条款 价格为 333 元 含税"),
                        List.of(src));
        assertThat(p).isPresent();
        assertThat(p.get().reflectionReasons().get(0)).isEqualTo("QUOTE_ONLY_EVIDENCE_UNBOUND");
    }

    @Test
    void scopePlusEvidence_passesWhenNumericClaimBound() {
        EvalChatResponse.Source src = new EvalChatResponse.Source("id1", null, "含税价 1200 元 详见条款");
        Optional<EvalReflectionOneShotGuard.Patch> p =
                EvalQuoteOnlyGuard.evaluate(
                        EvalQuoteOnlyGuard.Strictness.RELAXED,
                        EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS_PLUS_EVIDENCE,
                        "推荐含税价 1200 元",
                        List.of("含税价 1200 元 详见条款"),
                        List.of(src));
        assertThat(p).isEmpty();
    }

    @Test
    void corpusFromRetrieveHits_skipsBlankContent() {
        RetrieveHit h = new RetrieveHit();
        h.setContent("  alpha  ");
        assertThat(EvalQuoteOnlyGuard.corpusFromRetrieveHits(Arrays.asList(h, null)))
                .containsExactly("  alpha  ");
        assertThat(EvalQuoteOnlyGuard.corpusFromRetrieveHits(List.of())).isEmpty();
    }
}
