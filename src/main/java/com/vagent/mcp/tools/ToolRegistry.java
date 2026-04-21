package com.vagent.mcp.tools;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final Map<String, ToolSpec> byNameLower =
            Map.of(
                    "echo",
                            new ToolSpec(
                                    "echo",
                                    "1.0.0",
                                    "echo",
                                    "echo",
                                    true,
                                    buildToolSchemaHash("echo", "echo")),
                    "ping",
                            new ToolSpec(
                                    "ping",
                                    "1.0.0",
                                    "ping",
                                    "ping",
                                    true,
                                    buildToolSchemaHash("ping", "ping")));

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

