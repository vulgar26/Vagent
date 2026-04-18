package com.vagent.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式任务注册表：属主校验与取消标记（与 SSE 取消语义一致）。
 */
class LlmStreamTaskRegistryTest {

    @Test
    void registerThenCancel_sameUser_marksCancelled() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        String taskId = reg.registerTask("user-a");
        assertThat(reg.cancel(taskId, "user-a")).isTrue();
        assertThat(reg.isCancelled(taskId)).isTrue();
    }

    @Test
    void cancel_differentUser_returnsFalse_andNotCancelled() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        String taskId = reg.registerTask("user-a");
        assertThat(reg.cancel(taskId, "user-b")).isFalse();
        assertThat(reg.isCancelled(taskId)).isFalse();
    }

    @Test
    void cancel_unknownTask_returnsFalse() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        assertThat(reg.cancel("non-existent", "user-a")).isFalse();
    }

    @Test
    void remove_dropsTask() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        String taskId = reg.registerTask("u");
        reg.remove(taskId);
        assertThat(reg.isCancelled(taskId)).isFalse();
        assertThat(reg.cancel(taskId, "u")).isFalse();
    }

    @Test
    void cancelAllForConversation_marksMatchingTasks() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        String conv = "conv-1";
        String t1 = reg.registerTask("user-a", conv);
        String t2 = reg.registerTask("user-a", conv);
        reg.registerTask("user-a", "other-conv");
        assertThat(reg.cancelAllForConversation("user-a", conv)).isEqualTo(2);
        assertThat(reg.isCancelled(t1)).isTrue();
        assertThat(reg.isCancelled(t2)).isTrue();
    }
}
