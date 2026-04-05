package com.vagent.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 {@code vagent.embedding.provider} 注册 {@link EmbeddingClient}。
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "vagent.embedding", name = "provider", havingValue = "hash", matchIfMissing = true)
    EmbeddingClient hashEmbeddingClient(EmbeddingProperties properties) {
        return new HashEmbeddingClient(properties);
    }
}
