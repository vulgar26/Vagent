package com.vagent.chat;

import com.vagent.chat.message.Message;
import com.vagent.chat.message.MessageService;
import com.vagent.chat.rag.RagProperties;
import com.vagent.conversation.ConversationService;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.dto.RetrieveHit;
import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.config.LlmProperties;
import com.vagent.orchestration.IntentResolutionService;
import com.vagent.orchestration.OrchestrationProperties;
import com.vagent.orchestration.QueryRewriteService;
import com.vagent.orchestration.model.ChatBranch;
import com.vagent.orchestration.model.IntentResult;
import com.vagent.orchestration.model.RewriteResult;
import com.vagent.user.UserIdFormats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * M4/M5：在「会话 + 多轮消息 +（可选）知识检索」前提下编排 {@link LlmChatRequest}，经 {@link LlmSseStreamingBridge} 推 SSE。
 * <p>
 * <b>M5 在 M4 顺序上的插入点：</b>
 * <ul>
 *   <li>落库用户句之后：先做 {@link QueryRewriteService#rewriteForRetrieval}，得到检索专用 query（可与用户原句不同，例如拼接历史 USER）。</li>
 *   <li>若 {@link OrchestrationProperties#isIntentEnabled()}：{@link IntentResolutionService#resolve} 决定分支；
 *       {@link ChatBranch#CLARIFICATION} 时不检索、不调主 LLM，仅流式输出引导文案；{@link ChatBranch#SYSTEM_DIALOG} 时不检索但仍调 LLM；
 *       {@link ChatBranch#RAG} 与原先 M4 一致（检索 query 使用改写结果）。</li>
 * </ul>
 */
@Service
public class RagStreamChatService {

    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final KnowledgeRetrieveService knowledgeRetrieveService;
    private final RagProperties ragProperties;
    private final LlmProperties llmProperties;
    private final LlmStreamTaskRegistry taskRegistry;
    private final LlmSseStreamingBridge llmSseStreamingBridge;
    private final Executor llmStreamExecutor;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolutionService intentResolutionService;
    private final OrchestrationProperties orchestrationProperties;

    public RagStreamChatService(
            ConversationService conversationService,
            MessageService messageService,
            KnowledgeRetrieveService knowledgeRetrieveService,
            RagProperties ragProperties,
            LlmProperties llmProperties,
            LlmStreamTaskRegistry taskRegistry,
            LlmSseStreamingBridge llmSseStreamingBridge,
            QueryRewriteService queryRewriteService,
            IntentResolutionService intentResolutionService,
            OrchestrationProperties orchestrationProperties,
            @Qualifier("llmStreamExecutor") Executor llmStreamExecutor) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.knowledgeRetrieveService = knowledgeRetrieveService;
        this.ragProperties = ragProperties;
        this.llmProperties = llmProperties;
        this.taskRegistry = taskRegistry;
        this.llmSseStreamingBridge = llmSseStreamingBridge;
        this.queryRewriteService = queryRewriteService;
        this.intentResolutionService = intentResolutionService;
        this.orchestrationProperties = orchestrationProperties;
        this.llmStreamExecutor = llmStreamExecutor;
    }

    public SseEmitter stream(UUID userId, String conversationId, String userMessage) {
        conversationService
                .findOwnedByUser(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "会话不存在或无权访问"));

        String userCompact = UserIdFormats.compact(userId);

        List<Message> history =
                messageService.listRecentForConversation(conversationId, ragProperties.getMaxHistoryMessages());

        messageService.saveUserMessage(conversationId, userCompact, userMessage);

        RewriteResult rewrite = queryRewriteService.rewriteForRetrieval(userMessage, history);

        ChatBranch branch = ChatBranch.RAG;
        IntentResult intent = null;
        if (orchestrationProperties.isIntentEnabled()) {
            intent = intentResolutionService.resolve(userMessage);
            branch = intent.branch();
        }

        String taskId = taskRegistry.registerTask(userCompact);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> taskRegistry.remove(taskId));
        emitter.onTimeout(() -> taskRegistry.remove(taskId));
        emitter.onError(e -> taskRegistry.remove(taskId));

        if (branch == ChatBranch.CLARIFICATION) {
            String guidance =
                    intent != null && intent.optionalClarificationHint().isPresent()
                            ? intent.optionalClarificationHint().get()
                            : orchestrationProperties.getClarificationTemplate();
            llmStreamExecutor.execute(
                    () -> runGuidanceStream(emitter, taskId, guidance, conversationId, userCompact));
            return emitter;
        }

        List<RetrieveHit> hits;
        String systemText;
        if (branch == ChatBranch.SYSTEM_DIALOG) {
            hits = List.of();
            systemText = buildSystemDialogPrompt();
        } else {
            hits =
                    knowledgeRetrieveService.search(
                            userId, rewrite.retrievalQuery(), ragProperties.getTopK());
            systemText = buildKnowledgeSystemPrompt(hits);
        }

        List<LlmMessage> llmMessages = buildLlmMessages(systemText, history, userMessage);
        String model = llmProperties.getDefaultModel() != null ? llmProperties.getDefaultModel() : "";
        LlmChatRequest prepared = new LlmChatRequest(llmMessages, model);

        final int hitCount = hits.size();
        final String branchName = branch.name();
        llmStreamExecutor.execute(
                () -> runAsyncStream(emitter, taskId, prepared, conversationId, userCompact, hitCount, branchName));
        return emitter;
    }

    private void runAsyncStream(
            SseEmitter emitter,
            String taskId,
            LlmChatRequest prepared,
            String conversationId,
            String userCompact,
            int hitCount,
            String branch) {
        try {
            sendMeta(emitter, taskId, hitCount, branch);
            StringBuilder assistantBuffer = new StringBuilder();
            llmSseStreamingBridge.streamChatToSse(
                    emitter,
                    taskId,
                    prepared,
                    assistantBuffer,
                    () ->
                            messageService.saveAssistantMessage(
                                    conversationId, userCompact, assistantBuffer.toString()));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 澄清分支：不经过 {@link com.vagent.llm.LlmClient}，直接推送一段固定文案并完成 SSE，同时落库 ASSISTANT。
     */
    private void runGuidanceStream(
            SseEmitter emitter,
            String taskId,
            String guidanceText,
            String conversationId,
            String userCompact) {
        try {
            sendMeta(emitter, taskId, 0, ChatBranch.CLARIFICATION.name());
            if (taskRegistry.isCancelled(taskId)) {
                sendEvent(emitter, Map.of("type", "cancelled"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, Map.of("type", "chunk", "text", guidanceText != null ? guidanceText : ""));
            sendEvent(emitter, Map.of("type", "done"));
            messageService.saveAssistantMessage(
                    conversationId, userCompact, guidanceText != null ? guidanceText : "");
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void sendMeta(SseEmitter emitter, String taskId, int hitCount, String branch) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "meta");
        meta.put("taskId", taskId);
        meta.put("hitCount", hitCount);
        meta.put("branch", branch);
        sendEvent(emitter, meta);
    }

    private static List<LlmMessage> buildLlmMessages(
            String systemText, List<Message> history, String currentUserMessage) {
        List<LlmMessage> llmMessages = new ArrayList<>();
        llmMessages.add(new LlmMessage(LlmMessage.Role.SYSTEM, systemText));
        if (history != null) {
            for (Message m : history) {
                if (Message.ROLE_USER.equals(m.getRole())) {
                    llmMessages.add(new LlmMessage(LlmMessage.Role.USER, m.getContent()));
                } else if (Message.ROLE_ASSISTANT.equals(m.getRole())) {
                    llmMessages.add(new LlmMessage(LlmMessage.Role.ASSISTANT, m.getContent()));
                }
            }
        }
        llmMessages.add(new LlmMessage(LlmMessage.Role.USER, currentUserMessage));
        return llmMessages;
    }

    private String buildKnowledgeSystemPrompt(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "你是企业场景下的助手。当前知识库检索未命中相关片段。请结合对话历史（若有）与常识谨慎作答；"
                    + "若无法确认内部事实，请明确说明信息来源不足，不要编造内部政策或文档细节。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业场景下的助手。以下是与用户问题相关的知识库片段（按相似度排序）。请优先依据这些内容组织答案；")
                .append("若用户问题与片段明显无关，可先简要说明再回答。片段内容：\n\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrieveHit h = hits.get(i);
            sb.append("--- 片段 ").append(i + 1).append(" ---\n");
            String body = h.getContent();
            sb.append(body != null ? body : "").append("\n\n");
        }
        return sb.toString();
    }

    private static String buildSystemDialogPrompt() {
        return "你是友好、简洁的对话助手。用户可能在寒暄或闲聊。请简短自然回复，不要编造企业内部政策、文档或数据；"
                + "若用户随后提出业务问题，可提示其具体描述需求。";
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }
}
