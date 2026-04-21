package com.vagent.eval.evidence;

import com.vagent.eval.dto.EvalChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceMapExtractorTest {

    @Test
    void numeric_fourDigitPlain_notSplitIntoPrefix() {
        EvalChatResponse.Source src =
                new EvalChatResponse.Source("c1", null, "单价 1200 元含税，详见条款。");
        List<EvalChatResponse.EvidenceMapItem> map =
                EvidenceMapExtractor.buildEvidenceMap("推荐含税价 1200 元", List.of(src));
        assertThat(map.stream().filter(i -> "numeric".equals(i.getClaimType())).map(EvalChatResponse.EvidenceMapItem::getClaimValue))
                .containsExactly("1200");
    }

    @Test
    void numeric_commaGrouped_normalizedWithoutCommas() {
        EvalChatResponse.Source src = new EvalChatResponse.Source("c1", null, "Total USD 12,345.67 confirmed.");
        List<EvalChatResponse.EvidenceMapItem> map =
                EvidenceMapExtractor.buildEvidenceMap("金额 12,345.67 美元", List.of(src));
        assertThat(map.stream().filter(i -> "numeric".equals(i.getClaimType())).map(EvalChatResponse.EvidenceMapItem::getClaimValue))
                .contains("12345.67");
    }

    @Test
    void numeric_decimalBranch() {
        EvalChatResponse.Source src = new EvalChatResponse.Source("c1", null, "rate 2.5% in the table");
        List<EvalChatResponse.EvidenceMapItem> map =
                EvidenceMapExtractor.buildEvidenceMap("The rate is 2.5 percent", List.of(src));
        assertThat(map.stream().filter(i -> "numeric".equals(i.getClaimType())).map(EvalChatResponse.EvidenceMapItem::getClaimValue))
                .contains("2.5");
    }

    @Test
    void singleDigit_notEmittedAsNumericClaim() {
        EvalChatResponse.Source src = new EvalChatResponse.Source("c1", null, "chapter 5 only");
        List<EvalChatResponse.EvidenceMapItem> map =
                EvidenceMapExtractor.buildEvidenceMap("see chapter 5", List.of(src));
        assertThat(map.stream().filter(i -> "numeric".equals(i.getClaimType()))).isEmpty();
    }
}
