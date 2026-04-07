package com.vagent.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vagent.mcp.client.HttpMcpClient;
import com.vagent.mcp.client.McpClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * U6：按 {@code vagent.mcp.enabled} 装配 MCP Client。
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "vagent.mcp", name = "enabled", havingValue = "true")
    McpClient mcpClient(McpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return new HttpMcpClient(properties, objectMapper, meterRegistry);
    }
}

