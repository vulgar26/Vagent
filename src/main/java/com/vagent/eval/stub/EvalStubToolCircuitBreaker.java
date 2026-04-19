package com.vagent.eval.stub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评测桩工具简易熔断：同一工具名连续失败后短暂开路，避免异常路径拖垮评测 runner。
 */
public final class EvalStubToolCircuitBreaker {

    private final int failureThreshold;
    private final long openNanos;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public EvalStubToolCircuitBreaker(int failureThreshold, int openSeconds) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openNanos = Math.max(1L, openSeconds) * 1_000_000_000L;
    }

    public boolean allow(String toolKey) {
        State s = states.computeIfAbsent(toolKey, k -> new State());
        synchronized (s) {
            long now = System.nanoTime();
            if (s.openUntilNanos > now) {
                return false;
            }
            if (s.openUntilNanos > 0 && s.openUntilNanos <= now) {
                s.failures = 0;
                s.openUntilNanos = 0;
            }
            return true;
        }
    }

    public void recordSuccess(String toolKey) {
        State s = states.get(toolKey);
        if (s == null) {
            return;
        }
        synchronized (s) {
            s.failures = 0;
            s.openUntilNanos = 0;
        }
    }

    public void recordFailure(String toolKey) {
        State s = states.computeIfAbsent(toolKey, k -> new State());
        synchronized (s) {
            s.failures++;
            if (s.failures >= failureThreshold) {
                s.openUntilNanos = System.nanoTime() + openNanos;
                s.failures = 0;
            }
        }
    }

    private static final class State {
        private int failures;
        private long openUntilNanos;
    }
}
