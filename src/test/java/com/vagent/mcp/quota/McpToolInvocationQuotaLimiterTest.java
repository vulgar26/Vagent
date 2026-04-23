package com.vagent.mcp.quota;

import com.vagent.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolInvocationQuotaLimiterTest {

    @Test
    void perUserLimitResetsAfterWindowRolls() throws Exception {
        McpProperties props = new McpProperties();
        McpProperties.Quota q = props.getQuota();
        q.setEnabled(true);
        q.setWindow(Duration.ofMillis(80));
        q.setMaxInvocationsPerUserPerToolPerWindow(2);
        q.setMaxInvocationsPerConversationPerToolPerWindow(0);

        McpToolInvocationQuotaLimiter limiter = new McpToolInvocationQuotaLimiter(props);
        UUID user = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(user, null, "echo"));
        assertTrue(limiter.tryAcquire(user, null, "echo"));
        assertFalse(limiter.tryAcquire(user, null, "echo"));

        Thread.sleep(90);

        assertTrue(limiter.tryAcquire(user, null, "echo"));
    }

    @Test
    void conversationLimitIndependentOfUserCounter() {
        McpProperties props = new McpProperties();
        McpProperties.Quota q = props.getQuota();
        q.setEnabled(true);
        q.setWindow(Duration.ofMinutes(10));
        q.setMaxInvocationsPerUserPerToolPerWindow(100);
        q.setMaxInvocationsPerConversationPerToolPerWindow(1);

        McpToolInvocationQuotaLimiter limiter = new McpToolInvocationQuotaLimiter(props);
        UUID user = UUID.randomUUID();
        UUID conv = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(user, conv, "ping"));
        assertFalse(limiter.tryAcquire(user, conv, "ping"));
    }

    @Test
    void disabledQuotaAlwaysAllows() {
        McpProperties props = new McpProperties();
        props.getQuota().setEnabled(false);
        McpToolInvocationQuotaLimiter limiter = new McpToolInvocationQuotaLimiter(props);
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(user, null, "echo"));
        }
    }
}
