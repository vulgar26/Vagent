package com.vagent.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式生成任务登记：用于取消（按 taskId + 用户 id 校验，防止越权取消他人任务）。
 */
@Component
public class LlmStreamTaskRegistry {

    private record Task(String userIdCompact, AtomicBoolean cancelled) {
    }

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    public String registerTask(String userIdCompact) {
        String id = java.util.UUID.randomUUID().toString();
        tasks.put(id, new Task(userIdCompact, new AtomicBoolean(false)));
        return id;
    }

    /**
     * @return 是否成功标记取消（任务不存在或用户不匹配时 false）
     */
    public boolean cancel(String taskId, String userIdCompact) {
        Task t = tasks.get(taskId);
        if (t == null) {
            return false;
        }
        if (!t.userIdCompact.equals(userIdCompact)) {
            return false;
        }
        t.cancelled.set(true);
        return true;
    }

    public boolean isCancelled(String taskId) {
        Task t = tasks.get(taskId);
        return t != null && t.cancelled.get();
    }

    public void remove(String taskId) {
        tasks.remove(taskId);
    }
}
