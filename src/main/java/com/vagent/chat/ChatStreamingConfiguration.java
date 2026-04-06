package com.vagent.chat;

import com.vagent.chat.rag.RagProperties;
import com.vagent.observability.MdcTaskDecorator;
import com.vagent.orchestration.OrchestrationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 流式 LLM 在独立线程执行，避免阻塞 Servlet 线程；并注册 {@link com.vagent.chat.rag.RagProperties}（M4）、
 * {@link com.vagent.orchestration.OrchestrationProperties}（M5）。
 */
@Configuration
@EnableConfigurationProperties({RagProperties.class, OrchestrationProperties.class})
public class ChatStreamingConfiguration {

    @Bean(name = "llmStreamExecutor")
    public Executor llmStreamExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("llm-stream-");
        ex.setTaskDecorator(new MdcTaskDecorator());
        ex.initialize();
        return ex;
    }
}
