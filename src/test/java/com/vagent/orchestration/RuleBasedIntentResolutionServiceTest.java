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

    @Test
    void tool_intent_parses_explicit_tool_and_args() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setToolIntentEnabled(true);
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        var r1 = svc.resolve("tool=echo message=hello");
        assertThat(r1.branch()).isEqualTo(ChatBranch.RAG);
        assertThat(r1.toolIntent()).isTrue();
        assertThat(r1.optionalToolName()).contains("echo");
        assertThat(r1.safeToolArguments()).containsEntry("message", "hello");

        var r2 = svc.resolve("/tool ping");
        assertThat(r2.branch()).isEqualTo(ChatBranch.RAG);
        assertThat(r2.toolIntent()).isTrue();
        assertThat(r2.optionalToolName()).contains("ping");
    }

    @Test
    void tool_intent_parses_chinese_colon_directive() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setToolIntentEnabled(true);
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        var r = svc.resolve("工具:echo message=你好");
        assertThat(r.branch()).isEqualTo(ChatBranch.RAG);
        assertThat(r.toolIntent()).isTrue();
        assertThat(r.optionalToolName()).contains("echo");
        assertThat(r.safeToolArguments()).containsEntry("message", "你好");

        var r2 = svc.resolve("工具：ping");
        assertThat(r2.optionalToolName()).contains("ping");
    }

    @Test
    void tool_intent_parses_quoted_args() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setToolIntentEnabled(true);
        RuleBasedIntentResolutionService svc = new RuleBasedIntentResolutionService(p);

        var r = svc.resolve("tool=echo message=\"hello world\"");
        assertThat(r.toolIntent()).isTrue();
        assertThat(r.safeToolArguments()).containsEntry("message", "hello world");
    }
}
