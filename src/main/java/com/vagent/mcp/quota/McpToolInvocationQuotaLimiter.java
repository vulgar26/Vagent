package com.vagent.mcp.quota;

import com.vagent.mcp.config.McpProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D-6：MCP {@code tools/call} 前的进程内配额（固定窗口计数）。
 * <p>
 * 仅在入参 JSON Schema 已通过、即将发起网络调用前扣减；失败/超时仍计一次（与「真实下游压力」一致）。
 */
@Component
public final class McpToolInvocationQuotaLimiter {

    private final McpProperties mcpProperties;
    private final ConcurrentHashMap<String, WindowBucket> userToolWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowBucket> conversationToolWindows = new ConcurrentHashMap<>();

    public McpToolInvocationQuotaLimiter(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    /**
     * @return {@code true} 若允许本次调用并已在窗口内计数；{@code false} 若应拒绝（配额已满）
     */
    public boolean tryAcquire(UUID userId, UUID conversationId, String toolName) {
        McpProperties.Quota q = mcpProperties != null ? mcpProperties.getQuota() : null;
        if (q == null || !q.isEnabled()) {
            return true;
        }
        long windowMs = q.getWindow().toMillis();
        if (windowMs <= 0L) {
            return true;
        }
        String toolKey = normalizeTool(toolName);
        if (toolKey == null) {
            return true;
        }
        int perUser = q.getMaxInvocationsPerUserPerToolPerWindow();
        int perConv = q.getMaxInvocationsPerConversationPerToolPerWindow();
        if (perUser <= 0 && perConv <= 0) {
            return true;
        }
        boolean userReserved = false;
        if (userId != null && perUser > 0) {
            String key = userId + "|" + toolKey;
            if (!tryAcquireOne(userToolWindows, key, windowMs, perUser)) {
                return false;
            }
            userReserved = true;
        }
        if (conversationId != null && perConv > 0) {
            String key = conversationId + "|" + toolKey;
            if (!tryAcquireOne(conversationToolWindows, key, windowMs, perConv)) {
                if (userReserved) {
                    releaseOne(userToolWindows, userId + "|" + toolKey, windowMs);
                }
                return false;
            }
        }
        return true;
    }

    private static boolean tryAcquireOne(
            ConcurrentHashMap<String, WindowBucket> map, String key, long windowMs, int max) {
        WindowBucket b = map.computeIfAbsent(key, k -> new WindowBucket());
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (now - b.windowStartMs >= windowMs) {
                b.windowStartMs = now;
                b.count = 1;
                return true;
            }
            if (b.count >= max) {
                return false;
            }
            b.count++;
            return true;
        }
    }

    private static void releaseOne(ConcurrentHashMap<String, WindowBucket> map, String key, long windowMs) {
        WindowBucket b = map.get(key);
        if (b == null) {
            return;
        }
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (now - b.windowStartMs >= windowMs) {
                return;
            }
            if (b.count > 0) {
                b.count--;
            }
        }
    }

    private static String normalizeTool(String toolName) {
        if (toolName == null) {
            return null;
        }
        String t = toolName.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private static final class WindowBucket {
        long windowStartMs;
        int count;
    }
}
