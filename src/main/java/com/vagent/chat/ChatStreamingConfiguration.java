package com.vagent.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 流式 LLM 在独立线程执行，避免阻塞 Servlet 线程。
 */
@Configuration
public class ChatStreamingConfiguration {

    @Bean(name = "llmStreamExecutor")
    public Executor llmStreamExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("llm-stream-");
        ex.initialize();
        return ex;
    }
}
