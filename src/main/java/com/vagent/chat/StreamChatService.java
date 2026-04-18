package com.vagent.chat;

import com.vagent.chat.rag.RagProperties;
import com.vagent.conversation.ConversationService;
import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmMessage;
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
 * 流式对话入口：M3 起负责 SSE 与可取消任务；M4 起在配置开启时将编排委托给 {@link RagStreamChatService}。
 */
@Service
public class StreamChatService {

    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final ConversationService conversationService;
    private final LlmStreamTaskRegistry taskRegistry;
    private final LlmProperties llmProperties;
    private final Executor llmStreamExecutor;
    private final RagProperties ragProperties;
    private final RagStreamChatService ragStreamChatService;
    private final LlmSseStreamingBridge llmSseStreamingBridge;

    public StreamChatService(
            ConversationService conversationService,
            LlmStreamTaskRegistry taskRegistry,
            LlmProperties llmProperties,
            RagProperties ragProperties,
            RagStreamChatService ragStreamChatService,
            LlmSseStreamingBridge llmSseStreamingBridge,
            @Qualifier("llmStreamExecutor") Executor llmStreamExecutor) {
        this.conversationService = conversationService;
        this.taskRegistry = taskRegistry;
        this.llmProperties = llmProperties;
        this.ragProperties = ragProperties;
        this.ragStreamChatService = ragStreamChatService;
        this.llmSseStreamingBridge = llmSseStreamingBridge;
        this.llmStreamExecutor = llmStreamExecutor;
    }

    public SseEmitter stream(UUID userId, String conversationId, String message) {
        if (ragProperties.isEnabled()) {
            return ragStreamChatService.stream(userId, conversationId, message);
        }

        conversationService.findOwnedByUser(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "会话不存在或无权访问"));
        String userKey = UserIdFormats.canonical(userId);
        String taskId = taskRegistry.registerTask(userKey, conversationId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> taskRegistry.remove(taskId));
        emitter.onTimeout(() -> taskRegistry.remove(taskId));
        emitter.onError(e -> taskRegistry.remove(taskId));

        llmStreamExecutor.execute(() -> runStream(emitter, taskId, message));
        return emitter;
    }

    public boolean cancel(UUID userId, String taskId) {
        return taskRegistry.cancel(taskId, UserIdFormats.canonical(userId));
    }

    /**
     * M3 兼容路径：单条 USER 消息，不经由知识库与 messages 表（与 {@link RagProperties#isEnabled()}=false 对应）。
     */
    private void runStream(SseEmitter emitter, String taskId, String message) {
        try {
            sendEvent(emitter, Map.of("type", "meta", "taskId", taskId));
            String model = llmProperties.getDefaultModel() != null ? llmProperties.getDefaultModel() : "";
            LlmChatRequest req = new LlmChatRequest(List.of(new LlmMessage(LlmMessage.Role.USER, message)), model);
            llmSseStreamingBridge.streamChatToSse(emitter, taskId, req, null, null);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }
}
