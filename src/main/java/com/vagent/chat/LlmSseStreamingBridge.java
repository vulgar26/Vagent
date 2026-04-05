package com.vagent.chat;

import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmStreamSink;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
/**
 * 把 {@link LlmClient#streamChat} 的回调统一映射到 SSE：{@code chunk} / {@code done} / {@code cancelled} / {@code error}。
 * <p>
 * <b>为什么单独拆出类：</b>
 * M3 的 {@link StreamChatService} 与 M4 的 {@link RagStreamChatService} 都需要同一套「可取消 + 拼 JSON 事件」逻辑；
 * 若两处各写一份，后续改契约（例如增加字段）要改两遍。本类只做「LLM 流 → SSE」，不做会话鉴权与落库。
 * <p>
 * <b>assistantAccumulator：</b>
 * 若调用方传入非 null 的 {@link StringBuilder}，则在每次 {@link LlmStreamSink#onChunk} 时把文本追加进去，
 * 供 M4 在流正常结束后写入 {@code messages} 表的 ASSISTANT 行。
 * <p>
 * <b>onSuccessAfterDoneEvent：</b>
 * 仅在「未取消且已向前端发送 type=done」之后调用，用于落库；若回调抛异常，会 {@code completeWithError}，
 * 避免前端看到 done 但后端未持久化的静默失败（测试时更容易发现）。
 * <p>
 * <b>取消语义：</b>
 * 在传入的 {@link LlmChatRequest} 上再包一层取消回调：{@code 原请求.isCancelled() || taskRegistry.isCancelled(taskId)}，
 * 这样无论调用方是否在构造请求时传入取消回调，最终都与任务注册表一致。
 */
@Component
public class LlmSseStreamingBridge {

    private final LlmClient llmClient;
    private final LlmStreamTaskRegistry taskRegistry;

    public LlmSseStreamingBridge(LlmClient llmClient, LlmStreamTaskRegistry taskRegistry) {
        this.llmClient = llmClient;
        this.taskRegistry = taskRegistry;
    }

    public void streamChatToSse(
            SseEmitter emitter,
            String taskId,
            LlmChatRequest req,
            StringBuilder assistantAccumulator,
            Runnable onSuccessAfterDoneEvent) {
        Objects.requireNonNull(emitter, "emitter");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(req, "req");

        final LlmChatRequest withCancel =
                new LlmChatRequest(
                        req.messages(),
                        req.model(),
                        () -> req.isCancelled() || taskRegistry.isCancelled(taskId));

        llmClient.streamChat(withCancel, new LlmStreamSink() {
            @Override
            public void onChunk(String text) {
                if (withCancel.isCancelled()) {
                    return;
                }
                if (assistantAccumulator != null && text != null) {
                    assistantAccumulator.append(text);
                }
                try {
                    sendEvent(emitter, Map.of("type", "chunk", "text", text != null ? text : ""));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete() {
                try {
                    if (taskRegistry.isCancelled(taskId)) {
                        sendEvent(emitter, Map.of("type", "cancelled"));
                    } else {
                        sendEvent(emitter, Map.of("type", "done"));
                        if (onSuccessAfterDoneEvent != null) {
                            onSuccessAfterDoneEvent.run();
                        }
                    }
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                try {
                    String msg = t.getMessage() != null ? t.getMessage() : "unknown";
                    sendEvent(emitter, Map.of("type", "error", "message", msg));
                    emitter.completeWithError(t);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        });
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }
}
