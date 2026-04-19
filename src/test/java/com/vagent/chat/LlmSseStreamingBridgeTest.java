package com.vagent.chat;

import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.impl.NoopLlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 流 → SSE 桥：完成回调与取消时不执行成功回调。
 */
class LlmSseStreamingBridgeTest {

    @Test
    void noop_complete_invokesSuccessRunnable() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        LlmSseStreamingBridge bridge =
                new LlmSseStreamingBridge(new NoopLlmClient(), reg, new SimpleMeterRegistry());
        String taskId = reg.registerTask("u1");
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicBoolean doneCallback = new AtomicBoolean();
        LlmChatRequest req =
                new LlmChatRequest(List.of(new LlmMessage(LlmMessage.Role.USER, "hi")), "m");

        bridge.streamChatToSse(emitter, taskId, req, null, () -> doneCallback.set(true));

        assertThat(doneCallback.get()).isTrue();
    }

    @Test
    void whenCancelledBeforeComplete_successRunnableNotRun() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        LlmSseStreamingBridge bridge =
                new LlmSseStreamingBridge(
                        (request, sink) -> {
                            sink.onChunk("x");
                            sink.onComplete();
                        },
                        reg,
                        new SimpleMeterRegistry());
        String taskId = reg.registerTask("u1");
        reg.cancel(taskId, "u1");
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicBoolean doneCallback = new AtomicBoolean();
        LlmChatRequest req =
                new LlmChatRequest(List.of(new LlmMessage(LlmMessage.Role.USER, "hi")), "m");

        bridge.streamChatToSse(emitter, taskId, req, null, () -> doneCallback.set(true));

        assertThat(doneCallback.get()).isFalse();
    }

    @Test
    void chunksAppendToBuffer() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        LlmSseStreamingBridge bridge =
                new LlmSseStreamingBridge(
                        (request, sink) -> {
                            sink.onChunk("ab");
                            sink.onComplete();
                        },
                        reg,
                        new SimpleMeterRegistry());
        String taskId = reg.registerTask("u1");
        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder buf = new StringBuilder();
        bridge.streamChatToSse(
                emitter,
                taskId,
                new LlmChatRequest(List.of(new LlmMessage(LlmMessage.Role.USER, "hi")), "m"),
                buf,
                null);

        assertThat(buf.toString()).isEqualTo("ab");
    }

    @Test
    void bufferedModeDefersChunkUntilPreDoneEmitter() {
        LlmStreamTaskRegistry reg = new LlmStreamTaskRegistry();
        AtomicInteger chunkEventsBeforePreDone = new AtomicInteger();
        LlmSseStreamingBridge bridge =
                new LlmSseStreamingBridge(
                        (request, sink) -> {
                            sink.onChunk("a");
                            sink.onChunk("b");
                            sink.onComplete();
                        },
                        reg,
                        new SimpleMeterRegistry());
        String taskId = reg.registerTask("u1");
        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder buf = new StringBuilder();
        bridge.streamChatToSse(
                emitter,
                taskId,
                new LlmChatRequest(List.of(new LlmMessage(LlmMessage.Role.USER, "hi")), "m"),
                buf,
                null,
                (em, accumulated) -> {
                    chunkEventsBeforePreDone.incrementAndGet();
                    try {
                        em.send(
                                SseEmitter.event()
                                        .data(Map.of("type", "chunk", "text", accumulated.toString()), MediaType.APPLICATION_JSON));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        assertThat(buf.toString()).isEqualTo("ab");
        assertThat(chunkEventsBeforePreDone.get()).isEqualTo(1);
    }
}
