package com.vagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * U6：MCP（Model Context Protocol）Client 配置。
 * <p>
 * 本仓库的 U6 采用「独立 MCP Server（HTTP）+ Vagent 作为 Client」的形态；默认关闭。
 */
@ConfigurationProperties(prefix = "vagent.mcp")
public class McpProperties {

    /** 是否启用 MCP Client（启用后才注册 Debug Controller/Client Bean）。 */
    private boolean enabled = false;

    /** MCP Server base url，例如 {@code http://127.0.0.1:8765}。 */
    private String baseUrl = "http://127.0.0.1:8765";

    /** 可选：Bearer token（建议通过环境变量注入，勿提交到 Git）。 */
    private String token = "";

    /** MCP 协议版本；默认与 MCP 2025-03-26 schema 对齐。 */
    private String protocolVersion = "2025-03-26";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration requestTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}

