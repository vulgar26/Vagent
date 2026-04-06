package com.vagent.observability;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * U4：将 Servlet 线程上的 {@link MDC}（含 {@code traceId}）复制到 {@code llm-stream-*} 异步线程，便于流式阶段日志关联。
 */
public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (previous != null) {
                    MDC.setContextMap(previous);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
