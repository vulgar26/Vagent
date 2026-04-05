package com.vagent.chat;

import com.vagent.conversation.ConversationService;
import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.LlmStreamSink;
import com.vagent.llm.config.LlmProperties;
import com.vagent.user.UserIdFormats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 将 {@link LlmClient#streamChat} 桥接到 {@link SseEmitter}，并登记可取消任务。
 */
@Service
public class StreamChatService {

    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final LlmClient llmClient;
    private final ConversationService conversationService;
    private final LlmStreamTaskRegistry taskRegistry;
    private final LlmProperties llmProperties;
    private final Executor llmStreamExecutor;

    public StreamChatService(
            LlmClient llmClient,
            ConversationService conversationService,
            LlmStreamTaskRegistry taskRegistry,
            LlmProperties llmProperties,
            @Qualifier("llmStreamExecutor") Executor llmStreamExecutor) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.taskRegistry = taskRegistry;
        this.llmProperties = llmProperties;
        this.llmStreamExecutor = llmStreamExecutor;
    }

    public SseEmitter stream(UUID userId, String conversationId, String message) {
        conversationService.findOwnedByUser(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "会话不存在或无权访问"));
        String userCompact = UserIdFormats.compact(userId);
        String taskId = taskRegistry.registerTask(userCompact);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> taskRegistry.remove(taskId));
        emitter.onTimeout(() -> taskRegistry.remove(taskId));
        emitter.onError(e -> taskRegistry.remove(taskId));

        llmStreamExecutor.execute(() -> runStream(emitter, taskId, message));
        return emitter;
    }

    public boolean cancel(UUID userId, String taskId) {
        return taskRegistry.cancel(taskId, UserIdFormats.compact(userId));
    }

    private void runStream(SseEmitter emitter, String taskId, String message) {
        try {
            sendEvent(emitter, Map.of("type", "meta", "taskId", taskId));
            String model = llmProperties.getDefaultModel() != null ? llmProperties.getDefaultModel() : "";
            LlmChatRequest req = new LlmChatRequest(
                    List.of(new LlmMessage(LlmMessage.Role.USER, message)),
                    model,
                    () -> taskRegistry.isCancelled(taskId));
            llmClient.streamChat(req, new LlmStreamSink() {
                @Override
                public void onChunk(String text) {
                    if (taskRegistry.isCancelled(taskId)) {
                        return;
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
                        }
                        emitter.complete();
                    } catch (IOException e) {
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
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }
}
