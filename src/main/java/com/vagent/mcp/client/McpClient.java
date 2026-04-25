package com.vagent.mcp.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * U6：MCP Client（最小接口），用于列出工具与调用工具。
 * <p>
 * 传输层与协议细节由实现类封装（当前为 HTTP + JSON 响应模式）。
 */
public interface McpClient {

    List<Map<String, Object>> listTools();

    /**
     * @param perToolHttpTimeout 覆盖 {@code vagent.mcp.tool-call-timeout} 的单次 {@code tools/call} HTTP 超时；
     *                             {@code null} 表示使用全局配置（由 {@link com.vagent.mcp.tools.ToolRegistry} 等传入）
     */
    Map<String, Object> callTool(String name, Map<String, Object> arguments, Duration perToolHttpTimeout);

    default Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        return callTool(name, arguments, null);
    }
}

