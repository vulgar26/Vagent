package com.vagent.mcp.client;

import java.util.List;
import java.util.Map;

/**
 * U6：MCP Client（最小接口），用于列出工具与调用工具。
 * <p>
 * 传输层与协议细节由实现类封装（当前为 HTTP + JSON 响应模式）。
 */
public interface McpClient {

    List<Map<String, Object>> listTools();

    Map<String, Object> callTool(String name, Map<String, Object> arguments);
}

