package com.vagent.mcp.tools;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 工具治理 SSOT（最小可用）：集中管理“工具是否存在、schema key、版本”等元信息。
 * <p>
 * D-1 先只覆盖 echo/ping；后续 D1–D4 扩展为完整 ToolRegistry（版本/resultSchema/审计/配额）。
 */
@Component
public final class ToolRegistry {

    /** 最小信息：只提供 schema key 与是否要求出参 schema。 */
    public record ToolSpec(String toolNameLower, String argSchemaKey, String resultSchemaKey, boolean resultSchemaRequired, String version) {}

    private final Map<String, ToolSpec> byNameLower =
            Map.of(
                    "echo", new ToolSpec("echo", "echo", "echo", true, "v1"),
                    "ping", new ToolSpec("ping", "ping", "ping", true, "v1"));

    public Optional<ToolSpec> find(String toolName) {
        String k = normalize(toolName);
        if (k == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNameLower.get(k));
    }

    public Optional<String> argSchemaKey(String toolName) {
        return find(toolName).map(ToolSpec::argSchemaKey).filter(s -> s != null && !s.isBlank());
    }

    public Optional<String> resultSchemaKey(String toolName) {
        return find(toolName).map(ToolSpec::resultSchemaKey).filter(s -> s != null && !s.isBlank());
    }

    public boolean isResultSchemaRequired(String toolName) {
        return find(toolName).map(ToolSpec::resultSchemaRequired).orElse(false);
    }

    private static String normalize(String toolName) {
        if (toolName == null) {
            return null;
        }
        String n = toolName.trim();
        if (n.isEmpty()) {
            return null;
        }
        return n.toLowerCase(Locale.ROOT);
    }
}

