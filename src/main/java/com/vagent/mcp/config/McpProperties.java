package com.vagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * U7：允许调用的工具白名单（逗号分隔）。空表示不允许任何工具进入主链路（仅联调入口可用）。
     * <p>
     * 例：echo,ping
     */
    private String allowedTools = "";

    /** U7：单次 tools/call 超时（主链路内使用）；不配置则沿用 requestTimeout。 */
    private Duration toolCallTimeout = Duration.ofSeconds(3);

    /**
     * D-3：工具调用失败（尤其是 schema 校验失败）时的对话策略。
     * <p>
     * - clarify：直接转澄清，不继续走 RAG/LLM（默认，安全/可控）
     * - fallback：忽略工具，继续走 RAG/LLM（但 meta 仍会标注工具失败）
     */
    private String toolFailBehavior = "clarify";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration requestTimeout = Duration.ofSeconds(30);

    /** D-6：主链路 MCP 调用配额（进程内固定窗口；多实例需各算各的）。 */
    private Quota quota = new Quota();

    /**
     * D-7：在 {@code echo}/{@code ping} 之外登记 {@link com.vagent.mcp.tools.ToolRegistry} 条目（版本、schema 键、指纹）。
     * <p>
     * Schema 文件须已存在于 classpath：{@code /mcp/tool-arg-schemas/&lt;argSchemaKey&gt;.schema.json} 与
     * {@code /mcp/tool-result-schemas/&lt;resultSchemaKey&gt;.schema.json}；与 {@code vagent.mcp.allowed-tools} 白名单独立配置。
     */
    private List<RegisteredTool> registryTools = new ArrayList<>();

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

    public String getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(String allowedTools) {
        this.allowedTools = allowedTools;
    }

    public Duration getToolCallTimeout() {
        return toolCallTimeout;
    }

    public void setToolCallTimeout(Duration toolCallTimeout) {
        this.toolCallTimeout = toolCallTimeout;
    }

    public String getToolFailBehavior() {
        return toolFailBehavior;
    }

    public void setToolFailBehavior(String toolFailBehavior) {
        this.toolFailBehavior = toolFailBehavior;
    }

    public Quota getQuota() {
        return quota;
    }

    public void setQuota(Quota quota) {
        this.quota = quota != null ? quota : new Quota();
    }

    public List<RegisteredTool> getRegistryTools() {
        return registryTools;
    }

    public void setRegistryTools(List<RegisteredTool> registryTools) {
        this.registryTools = registryTools != null ? registryTools : new ArrayList<>();
    }

    public static final class RegisteredTool {
        /** 工具名（trim 后转小写登记）；不可为空。 */
        private String name = "";

        private String version = "1.0.0";

        /**
         * 入参 schema 资源键；空则默认与 {@link #name} 的小写一致。
         */
        private String argSchemaKey = "";

        /**
         * 出参 schema 资源键；空则默认与 {@link #name} 的小写一致。
         */
        private String resultSchemaKey = "";

        /** 是否强制做 MCP 返回值的 JSON Schema 校验（与内置工具一致）。 */
        private boolean resultSchemaRequired = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name != null ? name : "";
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version != null ? version : "";
        }

        public String getArgSchemaKey() {
            return argSchemaKey;
        }

        public void setArgSchemaKey(String argSchemaKey) {
            this.argSchemaKey = argSchemaKey != null ? argSchemaKey : "";
        }

        public String getResultSchemaKey() {
            return resultSchemaKey;
        }

        public void setResultSchemaKey(String resultSchemaKey) {
            this.resultSchemaKey = resultSchemaKey != null ? resultSchemaKey : "";
        }

        public boolean isResultSchemaRequired() {
            return resultSchemaRequired;
        }

        public void setResultSchemaRequired(boolean resultSchemaRequired) {
            this.resultSchemaRequired = resultSchemaRequired;
        }
    }

    public static final class Quota {
        /**
         * 是否启用配额；启用后须至少配置一项正数上限（用户维度和/或会话维度），否则等价于不限制。
         */
        private boolean enabled = false;

        /** 统计窗口长度。 */
        private Duration window = Duration.ofMinutes(1);

        /**
         * 每用户每工具在窗口内最多几次实际 {@code callTool}（通过入参 schema 后计数）。
         * {@code <=0} 表示不限制该维度。
         */
        private int maxInvocationsPerUserPerToolPerWindow = 60;

        /**
         * 每会话每工具在窗口内最多几次；{@code <=0} 表示不限制该维度。
         * <p>
         * 评测 real 路径无会话 id，仅用户维度生效。
         */
        private int maxInvocationsPerConversationPerToolPerWindow = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window != null && !window.isNegative() && !window.isZero() ? window : Duration.ofMinutes(1);
        }

        public int getMaxInvocationsPerUserPerToolPerWindow() {
            return maxInvocationsPerUserPerToolPerWindow;
        }

        public void setMaxInvocationsPerUserPerToolPerWindow(int maxInvocationsPerUserPerToolPerWindow) {
            this.maxInvocationsPerUserPerToolPerWindow = maxInvocationsPerUserPerToolPerWindow;
        }

        public int getMaxInvocationsPerConversationPerToolPerWindow() {
            return maxInvocationsPerConversationPerToolPerWindow;
        }

        public void setMaxInvocationsPerConversationPerToolPerWindow(int maxInvocationsPerConversationPerToolPerWindow) {
            this.maxInvocationsPerConversationPerToolPerWindow = maxInvocationsPerConversationPerToolPerWindow;
        }
    }
}

