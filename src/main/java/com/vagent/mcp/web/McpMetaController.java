package com.vagent.mcp.web;

import com.vagent.mcp.config.McpProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 配置快照（不依赖 MCP Server 可达）；便于前端与运维确认开关与白名单。
 */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpMetaController {

    private final McpProperties mcpProperties;

    public McpMetaController(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of(
                "enabled", mcpProperties.isEnabled(),
                "baseUrl", mcpProperties.getBaseUrl(),
                "protocolVersion", mcpProperties.getProtocolVersion(),
                "allowedTools", splitCsv(mcpProperties.getAllowedTools()),
                "hasTokenConfigured", mcpProperties.getToken() != null && !mcpProperties.getToken().isBlank());
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
