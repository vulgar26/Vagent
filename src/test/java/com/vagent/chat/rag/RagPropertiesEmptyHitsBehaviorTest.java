package com.vagent.chat.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U3：{@link RagProperties#emptyHitsBehavior} 与 Spring relaxed binding（{@code no-llm} / {@code allow-llm}）。
 */
class RagPropertiesEmptyHitsBehaviorTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(RagPropsTestConfig.class);

    @Configuration
    @EnableConfigurationProperties(RagProperties.class)
    static class RagPropsTestConfig {}

    @Test
    void defaultIsAllowLlm() {
        runner.run(
                ctx -> {
                    RagProperties p = ctx.getBean(RagProperties.class);
                    assertThat(p.getEmptyHitsBehavior()).isEqualTo(EmptyHitsBehavior.ALLOW_LLM);
                    assertThat(p.getEmptyHitsNoLlmMessage()).isNotBlank();
                });
    }

    @Test
    void bindsNoLlmHyphen() {
        runner.withPropertyValues("vagent.rag.empty-hits-behavior=no-llm")
                .run(
                        ctx -> {
                            RagProperties p = ctx.getBean(RagProperties.class);
                            assertThat(p.getEmptyHitsBehavior()).isEqualTo(EmptyHitsBehavior.NO_LLM);
                        });
    }

    @Test
    void bindsAllowLlmHyphen() {
        runner.withPropertyValues("vagent.rag.empty-hits-behavior=allow-llm")
                .run(
                        ctx -> {
                            RagProperties p = ctx.getBean(RagProperties.class);
                            assertThat(p.getEmptyHitsBehavior()).isEqualTo(EmptyHitsBehavior.ALLOW_LLM);
                        });
    }
}
