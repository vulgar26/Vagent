package com.vagent.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式生成任务登记：用于取消（按 taskId + 用户 id 校验，防止越权取消他人任务）。
 * <p>可选记录 {@code conversationId}，便于删除会话时批量标记取消。</p>
 */
@Component
public class LlmStreamTaskRegistry {

    private record Task(String userIdCompact, String conversationId, AtomicBoolean cancelled) {
    }

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * @param conversationId 可为 null（例如非会话型调用）；删除会话时用于匹配待取消任务
     */
    public String registerTask(String userIdCompact, String conversationId) {
        String id = java.util.UUID.randomUUID().toString();
        String conv = conversationId != null ? conversationId.trim() : "";
        tasks.put(id, new Task(userIdCompact, conv, new AtomicBoolean(false)));
        return id;
    }

    /** 兼容旧调用：不绑定会话。 */
    public String registerTask(String userIdCompact) {
        return registerTask(userIdCompact, null);
    }

    /**
     * 将会话下所有进行中任务标记为取消（幂等）。
     *
     * @return 被标记的任务数
     */
    public int cancelAllForConversation(String userIdCompact, String conversationId) {
        if (userIdCompact == null
                || conversationId == null
                || conversationId.isBlank()) {
            return 0;
        }
        String conv = conversationId.trim();
        int n = 0;
        for (Task t : tasks.values()) {
            if (userIdCompact.equals(t.userIdCompact()) && conv.equals(t.conversationId())) {
                t.cancelled().set(true);
                n++;
            }
        }
        return n;
    }

    /**
     * @return 是否成功标记取消（任务不存在或用户不匹配时 false）
     */
    public boolean cancel(String taskId, String userIdCompact) {
        Task t = tasks.get(taskId);
        if (t == null) {
            return false;
        }
        if (!t.userIdCompact().equals(userIdCompact)) {
            return false;
        }
        t.cancelled().set(true);
        return true;
    }

    public boolean isCancelled(String taskId) {
        Task t = tasks.get(taskId);
        return t != null && t.cancelled().get();
    }

    public void remove(String taskId) {
        tasks.remove(taskId);
    }
}
