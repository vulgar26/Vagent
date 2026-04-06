package com.vagent.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 {@code vagent.embedding.provider} 注册 {@link EmbeddingClient}。
 */
@Configuration
@EnableConfigurationProperties({EmbeddingProperties.class, EmbeddingDashScopeProperties.class})
public class EmbeddingClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "vagent.embedding", name = "provider", havingValue = "hash", matchIfMissing = true)
    EmbeddingClient hashEmbeddingClient(EmbeddingProperties properties) {
        return new HashEmbeddingClient(properties);
    }

    /**
     * U2：DashScope 兼容模式嵌入；须配置 API Key，且 {@link EmbeddingProperties#getDimensions()} 与库表 {@code vector(N)} 一致。
     */
    @Bean
    @ConditionalOnProperty(prefix = "vagent.embedding", name = "provider", havingValue = "dashscope")
    EmbeddingClient dashScopeEmbeddingClient(
            EmbeddingProperties embeddingProperties,
            EmbeddingDashScopeProperties embeddingDashScopeProperties,
            ObjectMapper objectMapper) {
        return new DashScopeEmbeddingClient(embeddingProperties, embeddingDashScopeProperties, objectMapper);
    }
}
