package com.vagent.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 允许的来源模式（{@link org.springframework.web.cors.CorsConfiguration#addAllowedOriginPattern}）。
 * <p>
 * 生产 profile 应配置具体域名；默认 {@code *} 仅便于本地开发。
 */
@ConfigurationProperties(prefix = "vagent.security.cors")
public class CorsProperties {

    /**
     * 例：{@code http://localhost:*}、{@code https://app.example.com}。含字面量 {@code *} 时表示允许任意来源（仅开发）。
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns != null ? allowedOriginPatterns : new ArrayList<>();
    }
}
