package com.vagent.mcp.web;

import com.vagent.mcp.client.McpClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * U6：MCP 联调入口（开发期验证 list/call 是否打通）。
 * <p>
 * 受 Spring Security /api/v1/** 保护（需 JWT）。
 */
@RestController
@RequestMapping("/api/v1/mcp")
@ConditionalOnBean(McpClient.class)
public class McpDebugController {

    private final McpClient mcpClient;

    public McpDebugController(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return mcpClient.listTools();
    }

    @PostMapping("/tools/{name}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> callTool(
            @PathVariable("name") @NotBlank String name,
            @RequestBody(required = false) Map<String, Object> arguments) {
        return mcpClient.callTool(name, arguments);
    }
}

