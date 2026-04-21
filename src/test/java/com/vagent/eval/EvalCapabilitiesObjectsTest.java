package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.guardrails.GuardrailsProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCapabilitiesObjectsTest {

    @Test
    void guardrailsFromProperties_quoteOff_noScopeKeysInMap() {
        GuardrailsProperties gp = new GuardrailsProperties();
        gp.getQuoteOnly().setEnabled(false);
        EvalChatResponse.GuardrailsFlag g = EvalCapabilitiesObjects.guardrailsFromProperties(gp);
        Map<String, Object> m = EvalCapabilitiesObjects.guardrailsToSnakeCaseMap(g);
        assertThat(m.get("quote_only")).isEqualTo(false);
        assertThat(m.get("evidence_map")).isEqualTo(true);
        assertThat(m.get("reflection")).isEqualTo(false);
        assertThat(m).doesNotContainKey("quote_only_scope");
        assertThat(m).doesNotContainKey("quote_only_scopes_supported");
    }

    @Test
    void guardrailsFromProperties_quoteOn_includesScopeAndSupportedList() {
        GuardrailsProperties gp = new GuardrailsProperties();
        gp.getQuoteOnly().setEnabled(true);
        gp.getQuoteOnly().setScope("digits_only");
        gp.getReflection().setEnabled(true);
        EvalChatResponse.GuardrailsFlag g = EvalCapabilitiesObjects.guardrailsFromProperties(gp);
        Map<String, Object> m = EvalCapabilitiesObjects.guardrailsToSnakeCaseMap(g);
        assertThat(m.get("quote_only")).isEqualTo(true);
        assertThat(m.get("quote_only_scope")).isEqualTo("digits_only");
        assertThat(m.get("quote_only_scopes_supported")).asList().hasSize(3);
        assertThat(m.get("reflection")).isEqualTo(true);
    }
}
