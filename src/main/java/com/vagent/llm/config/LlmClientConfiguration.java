package com.vagent.llm.config;

import com.vagent.llm.LlmClient;
import com.vagent.llm.impl.FakeStreamingLlmClient;
import com.vagent.llm.impl.NoopLlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 相关 Spring Bean 的装配配置。
 * <p>
 * <b>这个类是干什么的：</b>
 * 根据配置 {@code vagent.llm.provider} 向容器里注册对应的 {@link LlmClient} 实现。业务代码通过构造器注入 {@code LlmClient}，
 * 运行时得到的就是这里选中的实现。
 * <p>
 * <b>为什么用 {@code @ConditionalOnProperty}：</b>
 * 不同厂商实现可能依赖不同依赖包；按配置二选一（或多选一）注册 Bean，避免无关实现被加载，也避免多个实现同时成为 Primary 冲突。
 * <p>
 * <b>后续怎么加新厂商：</b>
 * 在本类中新增一个 {@code @Bean} + {@code @ConditionalOnProperty(..., havingValue = "新名字")}，指向新的实现类即可。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfiguration {

    /**
     * 当 provider 为 noop 或未配置时启用：占位实现，不发起网络请求，用于保证应用可启动、可跑测试。
     */
    @Bean
    @ConditionalOnProperty(prefix = "vagent.llm", name = "provider", havingValue = "noop", matchIfMissing = true)
    LlmClient noopLlmClient() {
        return new NoopLlmClient();
    }

    /**
     * 本地演示：按块输出最后一条用户消息，不调用外网。
     */
    @Bean
    @ConditionalOnProperty(prefix = "vagent.llm", name = "provider", havingValue = "fake-stream")
    LlmClient fakeStreamingLlmClient(LlmProperties llmProperties) {
        return new FakeStreamingLlmClient(llmProperties);
    }
}
