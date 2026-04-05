package com.vagent.orchestration;

import com.vagent.chat.message.Message;
import com.vagent.orchestration.impl.ConfigurableQueryRewriteService;
import com.vagent.orchestration.model.RewriteResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableQueryRewriteServiceTest {

    @Test
    void passthrough_returnsTrimmedCurrentOnly() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setRewriteStrategy(OrchestrationProperties.RewriteStrategy.PASSTHROUGH);
        ConfigurableQueryRewriteService svc = new ConfigurableQueryRewriteService(p);

        RewriteResult r = svc.rewriteForRetrieval("  请假流程  ", List.of(user("旧问题")));
        assertThat(r.retrievalQuery()).isEqualTo("请假流程");
    }

    @Test
    void concat_joinsPriorUserTurnsThenCurrent() {
        OrchestrationProperties p = new OrchestrationProperties();
        p.setRewriteStrategy(OrchestrationProperties.RewriteStrategy.CONCAT_USER_HISTORY);
        // 共 2 段：历史中最近 1 条 USER + 本轮
        p.setConcatMaxUserSegments(2);
        ConfigurableQueryRewriteService svc = new ConfigurableQueryRewriteService(p);

        List<Message> history =
                List.of(
                        user("第一轮"),
                        assistant("答"),
                        user("第二轮"),
                        assistant("答2"));

        RewriteResult r = svc.rewriteForRetrieval("追问", history);
        assertThat(r.retrievalQuery()).isEqualTo("第二轮\n追问");
    }

    @Test
    void blankCurrent_throws() {
        OrchestrationProperties p = new OrchestrationProperties();
        ConfigurableQueryRewriteService svc = new ConfigurableQueryRewriteService(p);
        assertThatThrownBy(() -> svc.rewriteForRetrieval("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Message user(String content) {
        Message m = new Message();
        m.setRole(Message.ROLE_USER);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.MIN);
        return m;
    }

    private static Message assistant(String content) {
        Message m = new Message();
        m.setRole(Message.ROLE_ASSISTANT);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.MIN);
        return m;
    }
}
