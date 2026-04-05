package com.vagent.orchestration;

import com.vagent.orchestration.impl.RuleBasedIntentResolutionService;
import com.vagent.orchestration.model.ChatBranch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedIntentResolutionServiceTest {

    @Test
    void greeting_prefix_triggers_systemDialog() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setSystemDialogPrefixes("你好,hi");
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        assertThat(svc.resolve("你好呀").branch()).isEqualTo(ChatBranch.SYSTEM_DIALOG);
        assertThat(svc.resolve("HI there").branch()).isEqualTo(ChatBranch.SYSTEM_DIALOG);
    }

    @Test
    void very_short_non_greeting_triggers_clarification() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setClarificationMinChars(4);
        p.setClarificationTemplate("请多说点");
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        assertThat(svc.resolve("abc").branch()).isEqualTo(ChatBranch.CLARIFICATION);
        assertThat(svc.resolve("abc").optionalClarificationHint()).contains("请多说点");
    }

    @Test
    void normal_question_is_rag() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setClarificationMinChars(3);
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        assertThat(svc.resolve("如何申请年假").branch()).isEqualTo(ChatBranch.RAG);
    }

    @Test
    void empty_message_is_clarification() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setClarificationTemplate("空模板");
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        assertThat(svc.resolve("  ").branch()).isEqualTo(ChatBranch.CLARIFICATION);
    }
}
