package com.vagent.mcp.tools;

import com.vagent.mcp.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 工具治理 SSOT：集中管理「工具 schema 键、版本、指纹」等元信息。
 * <p>
 * 内置 {@code echo}/{@code ping}；可通过 {@code vagent.mcp.registry-tools} 追加登记（D-7），须已有对应 classpath JSON Schema。
 */
@Component
public final class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /**
     * 最小信息（D-2 版）：
     * - toolVersion：语义化版本（registry 控制）
     * - toolSchemaHash：对入参/出参 schema 的 SHA-256 指纹（稳定、可审计）
     */
    public record ToolSpec(
            String toolNameLower,
            String toolVersion,
            String argSchemaKey,
            String resultSchemaKey,
            boolean resultSchemaRequired,
            String toolSchemaHash) {}

    private final Map<String, ToolSpec> byNameLower;

    public ToolRegistry(McpProperties mcpProperties) {
        Map<String, ToolSpec> map = new LinkedHashMap<>();
        putBuiltin(map, "echo", "1.0.0", "echo", "echo", true);
        putBuiltin(map, "ping", "1.0.0", "ping", "ping", true);
        if (mcpProperties != null) {
            List<McpProperties.RegisteredTool> extras = mcpProperties.getRegistryTools();
            if (extras != null) {
                for (McpProperties.RegisteredTool rt : extras) {
                    registerConfigured(map, rt);
                }
            }
        }
        this.byNameLower = Map.copyOf(map);
    }

    private static void putBuiltin(
            Map<String, ToolSpec> map,
            String nameLower,
            String version,
            String argSchemaKey,
            String resultSchemaKey,
            boolean resultSchemaRequired) {
        map.put(
                nameLower,
                new ToolSpec(
                        nameLower,
                        version,
                        argSchemaKey,
                        resultSchemaKey,
                        resultSchemaRequired,
                        buildToolSchemaHash(argSchemaKey, resultSchemaKey)));
    }

    private static void registerConfigured(Map<String, ToolSpec> map, McpProperties.RegisteredTool rt) {
        String n = normalize(rt.getName());
        if (n == null) {
            log.warn("Skipping vagent.mcp.registry-tools entry with blank name");
            return;
        }
        if (map.containsKey(n)) {
            log.warn(
                    "Skipping vagent.mcp.registry-tools entry for '{}': name already registered (built-in or earlier entry wins)",
                    n);
            return;
        }
        String version =
                rt.getVersion() != null && !rt.getVersion().isBlank() ? rt.getVersion().trim() : "1.0.0";
        String argKey =
                rt.getArgSchemaKey() != null && !rt.getArgSchemaKey().isBlank()
                        ? rt.getArgSchemaKey().trim()
                        : n;
        String resKey =
                rt.getResultSchemaKey() != null && !rt.getResultSchemaKey().isBlank()
                        ? rt.getResultSchemaKey().trim()
                        : n;
        try {
            String hash = buildToolSchemaHash(argKey, resKey);
            map.put(n, new ToolSpec(n, version, argKey, resKey, rt.isResultSchemaRequired(), hash));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid vagent.mcp.registry-tools entry for tool '"
                            + n
                            + "': cannot load arg/result schema resources for argSchemaKey='"
                            + argKey
                            + "', resultSchemaKey='"
                            + resKey
                            + "'",
                    e);
        }
    }

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

    public Optional<String> toolVersion(String toolName) {
        return find(toolName).map(ToolSpec::toolVersion).filter(s -> s != null && !s.isBlank());
    }

    public Optional<String> toolSchemaHash(String toolName) {
        return find(toolName).map(ToolSpec::toolSchemaHash).filter(s -> s != null && !s.isBlank());
    }

    public boolean isResultSchemaRequired(String toolName) {
        return find(toolName).map(ToolSpec::resultSchemaRequired).orElse(false);
    }

    private static String buildToolSchemaHash(String argSchemaKey, String resultSchemaKey) {
        // 约定：工具 schema 指纹 = sha256("arg:<argJson>\nresult:<resultJson>\n") 的 hex lower
        String argJson = readClasspathText("/mcp/tool-arg-schemas/" + argSchemaKey + ".schema.json");
        String resultJson = readClasspathText("/mcp/tool-result-schemas/" + resultSchemaKey + ".schema.json");
        String material = "arg:" + argJson + "\nresult:" + resultJson + "\n";
        return sha256Hex(material.getBytes(StandardCharsets.UTF_8));
    }

    private static String readClasspathText(String path) {
        try (InputStream in = ToolRegistry.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema resource: " + path);
            }
            byte[] bytes = readAllBytes(in);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema resource: " + path, e);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bout.write(buf, 0, n);
        }
        return bout.toByteArray();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(bytes != null ? bytes : new byte[0]);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
