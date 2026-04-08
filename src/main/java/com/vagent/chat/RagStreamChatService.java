package com.vagent.chat;

import com.vagent.chat.message.Message;
import com.vagent.chat.message.MessageService;
import com.vagent.chat.rag.EmptyHitsBehavior;
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
import com.vagent.mcp.client.McpClient;
import com.vagent.mcp.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * M4/M5：在「会话 + 多轮消息 +（可选）知识检索」前提下编排 {@link LlmChatRequest}，经 {@link LlmSseStreamingBridge} 推 SSE。
 * <p>
 * <b>M5 在 M4 顺序上的插入点：</b>
 * <ul>
 *   <li>落库用户句之后：先做 {@link QueryRewriteService#rewriteForRetrieval}，得到检索专用 query（可与用户原句不同，例如拼接历史 USER）。</li>
 *   <li>若 {@link OrchestrationProperties#isIntentEnabled()}：{@link IntentResolutionService#resolve} 决定分支；
 *       {@link ChatBranch#CLARIFICATION} 时不检索、不调主 LLM，仅流式输出引导文案；{@link ChatBranch#SYSTEM_DIALOG} 时不检索但仍调 LLM；
 *       {@link ChatBranch#RAG} 与原先 M4 一致（检索 query 使用改写结果）；<b>U3</b> 若检索 0 条且 {@code empty-hits-behavior=no-llm}，不调 {@link com.vagent.llm.LlmClient}；
 *       <b>U5</b> 对话检索走 {@link com.vagent.kb.KnowledgeRetrieveService#searchForRag}，可合并第二路全表召回。</li>
 * </ul>
 */
@Service
public class RagStreamChatService {

    private static final Logger log = LoggerFactory.getLogger(RagStreamChatService.class);

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
    private final ObjectProvider<McpClient> mcpClientProvider;
    private final McpProperties mcpProperties;

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
            ObjectProvider<McpClient> mcpClientProvider,
            McpProperties mcpProperties,
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
        this.mcpClientProvider = mcpClientProvider;
        this.mcpProperties = mcpProperties;
        this.llmStreamExecutor = llmStreamExecutor;
    }

    public SseEmitter stream(UUID userId, String conversationId, String userMessage) {
        conversationService
                .findOwnedByUser(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "会话不存在或无权访问"));

        String userKey = UserIdFormats.canonical(userId);

        List<Message> history =
                messageService.listRecentForConversation(conversationId, ragProperties.getMaxHistoryMessages());

        messageService.saveUserMessage(conversationId, userKey, userMessage);

        RewriteResult rewrite = queryRewriteService.rewriteForRetrieval(userMessage, history);

        ChatBranch branch = ChatBranch.RAG;
        IntentResult intent = null;
        if (orchestrationProperties.isIntentEnabled()) {
            intent = intentResolutionService.resolve(userMessage);
            branch = intent.branch();
        }

        String taskId = taskRegistry.registerTask(userKey);
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
                    () ->
                            runFixedAssistantStream(
                                    emitter,
                                    taskId,
                                    conversationId,
                                    userKey,
                                    guidance != null ? guidance : "",
                                    ChatBranch.CLARIFICATION.name(),
                                    0));
            return emitter;
        }

        List<RetrieveHit> hits;
        String systemText;
        String toolMetaName = null;
        boolean toolUsed = false;
        String toolOutcome = null;
        String toolError = null;
        if (branch == ChatBranch.SYSTEM_DIALOG) {
            hits = List.of();
            systemText = buildSystemDialogPrompt();
        } else {
            // U7：工具意图命中时，可在 RAG 编排中调用 MCP 工具，并把结果并入系统提示词。
            String toolContextText = null;
            if (branch == ChatBranch.RAG && intent != null && intent.toolIntent()) {
                toolMetaName = intent.optionalToolName().orElse(orchestrationProperties.getToolIntentDefaultToolName());
                ToolContextOutcome out = tryCallToolForContext(toolMetaName, intent.safeToolArguments(), userMessage);
                toolContextText = out.contextText();
                toolUsed = out.used();
                toolOutcome = out.outcome();
                toolError = out.error();
            }

            hits = knowledgeRetrieveService.searchForRag(userId, rewrite.retrievalQuery(), ragProperties);
            if (branch == ChatBranch.RAG
                    && hits.isEmpty()
                    && ragProperties.getEmptyHitsBehavior() == EmptyHitsBehavior.NO_LLM) {
                llmStreamExecutor.execute(
                        () ->
                                        runEmptyHitsNoLlmStream(
                                        emitter, taskId, conversationId, userKey));
                return emitter;
            }
            systemText = buildSystemPromptWithToolAndKnowledge(toolContextText, hits);
        }

        if (log.isDebugEnabled()) {
            log.debug("stream branch={} hitCount={}", branch, hits.size());
        }

        List<LlmMessage> llmMessages = buildLlmMessages(systemText, history, userMessage);
        String model = llmProperties.getDefaultModel() != null ? llmProperties.getDefaultModel() : "";
        LlmChatRequest prepared = new LlmChatRequest(llmMessages, model);

        final int hitCount = hits.size();
        final String branchName = branch.name();
        final boolean toolUsedFinal = toolUsed;
        final String toolNameFinal = toolMetaName;
        final String toolOutcomeFinal = toolOutcome;
        final String toolErrorFinal = toolError;
        llmStreamExecutor.execute(
                () ->
                        runAsyncStream(
                                emitter,
                                taskId,
                                prepared,
                                conversationId,
                                userKey,
                                hitCount,
                                branchName,
                                toolUsedFinal,
                                toolNameFinal,
                                toolOutcomeFinal,
                                toolErrorFinal));
        return emitter;
    }

    private void runAsyncStream(
            SseEmitter emitter,
            String taskId,
            LlmChatRequest prepared,
            String conversationId,
            String userKey,
            int hitCount,
            String branch,
            boolean toolUsed,
            String toolName,
            String toolOutcome,
            String toolError) {
        try {
            sendMeta(emitter, taskId, hitCount, branch, toolUsed, toolName, toolOutcome, toolError);
            StringBuilder assistantBuffer = new StringBuilder();
            llmSseStreamingBridge.streamChatToSse(
                    emitter,
                    taskId,
                    prepared,
                    assistantBuffer,
                    () ->
                            messageService.saveAssistantMessage(
                                    conversationId, userKey, assistantBuffer.toString()));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * U3：RAG 分支检索无命中且配置为 {@link EmptyHitsBehavior#NO_LLM} 时，不调 {@link com.vagent.llm.LlmClient}，
     * 与澄清分支同属「固定文案 + done」。
     */
    private void runEmptyHitsNoLlmStream(
            SseEmitter emitter, String taskId, String conversationId, String userKey) {
        String text = ragProperties.getEmptyHitsNoLlmMessage();
        runFixedAssistantStream(
                emitter,
                taskId,
                conversationId,
                userKey,
                text != null ? text : "",
                ChatBranch.RAG.name(),
                0);
    }

    /**
     * 澄清分支与 U3 空命中：不经过 {@link com.vagent.llm.LlmClient}，推送固定文案并完成 SSE，同时落库 ASSISTANT。
     * <p>
     * 流程：meta → chunk → done。
     *
     * @param hitCount meta 中的 hitCount（澄清与空命中为 0）
     */
    private void runFixedAssistantStream(
            SseEmitter emitter,
            String taskId,
            String conversationId,
            String userKey,
            String fullText,
            String branchName,
            int hitCount) {
        try {
            sendMeta(emitter, taskId, hitCount, branchName, false, null, null, null);
            if (taskRegistry.isCancelled(taskId)) {
                sendEvent(emitter, Map.of("type", "cancelled"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, Map.of("type", "chunk", "text", fullText));
            sendEvent(emitter, Map.of("type", "done"));
            messageService.saveAssistantMessage(conversationId, userKey, fullText);
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void sendMeta(
            SseEmitter emitter,
            String taskId,
            int hitCount,
            String branch,
            boolean toolUsed,
            String toolName,
            String toolOutcome,
            String toolError)
            throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "meta");
        meta.put("taskId", taskId);
        meta.put("hitCount", hitCount);
        meta.put("branch", branch);
        meta.put("toolUsed", toolUsed);
        if (toolUsed && toolName != null && !toolName.isBlank()) {
            meta.put("toolName", toolName);
        }
        if (toolOutcome != null && !toolOutcome.isBlank()) {
            meta.put("toolOutcome", toolOutcome);
        }
        if (toolError != null && !toolError.isBlank()) {
            meta.put("toolError", toolError);
        }
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
            sb.append("--- 片段 ").append(i + 1);
            if ("global".equals(h.getSource())) {
                sb.append("（来源：共享语料/跨用户召回，仅作参考）");
            }
            sb.append(" ---\n");
            String body = h.getContent();
            sb.append(body != null ? body : "").append("\n\n");
        }
        return sb.toString();
    }

    private String buildSystemPromptWithToolAndKnowledge(String toolContextText, List<RetrieveHit> hits) {
        String base = buildKnowledgeSystemPrompt(hits);
        if (toolContextText == null || toolContextText.isBlank()) {
            return base;
        }
        return "你是企业场景下的助手。除知识库检索片段外，下方还包含工具调用返回的上下文信息。"
                + "请优先依据可验证的信息作答；若工具与知识库冲突，以知识库与明确证据为准。\n\n"
                + toolContextText
                + "\n\n"
                + base;
    }

    private ToolContextOutcome tryCallToolForContext(String toolName, Map<String, Object> toolArgs, String userMessage) {
        if (toolName == null || toolName.isBlank()) {
            return ToolContextOutcome.skipped();
        }
        if (!mcpProperties.isEnabled()) {
            return ToolContextOutcome.skipped();
        }
        McpClient client = mcpClientProvider.getIfAvailable();
        if (client == null) {
            return ToolContextOutcome.skipped();
        }
        if (!isToolAllowed(toolName, mcpProperties.getAllowedTools())) {
            return ToolContextOutcome.skipped();
        }

        try {
            Map<String, Object> args = sanitizeToolArguments(toolName, toolArgs, userMessage);
            Map<String, Object> result = client.callTool(toolName, args);
            return ToolContextOutcome.used(buildToolContextPrompt(toolName, result));
        } catch (Exception e) {
            log.warn("mcp tool call failed: tool={}", toolName, e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String outcome = isLikelyTimeout(e) ? "timeout" : "error";
            return ToolContextOutcome.failed(outcome, msg);
        }
    }

    private static boolean isLikelyTimeout(Throwable e) {
        if (e == null) {
            return false;
        }
        String m = e.getMessage();
        if (m != null && m.toLowerCase(Locale.ROOT).contains("timeout")) {
            return true;
        }
        Throwable c = e.getCause();
        if (c != null && c != e) {
            return isLikelyTimeout(c);
        }
        return false;
    }

    private record ToolContextOutcome(boolean used, String contextText, String outcome, String error) {
        static ToolContextOutcome skipped() {
            return new ToolContextOutcome(false, null, null, null);
        }

        static ToolContextOutcome used(String contextText) {
            return new ToolContextOutcome(true, contextText, "success", null);
        }

        static ToolContextOutcome failed(String outcome, String error) {
            return new ToolContextOutcome(false, null, outcome, error);
        }
    }

    private static Map<String, Object> sanitizeToolArguments(
            String toolName, Map<String, Object> raw, String userMessage) {
        if ("ping".equals(toolName)) {
            return Map.of();
        }
        if ("echo".equals(toolName)) {
            Object v = raw != null ? raw.get("message") : null;
            String message = v != null ? String.valueOf(v) : (userMessage != null ? userMessage : "");
            return Map.of("message", message);
        }
        // Unknown tool: no arguments by default; server-side tool handler should validate inputSchema.
        return Map.of();
    }

    private static boolean isToolAllowed(String toolName, String csvAllowed) {
        if (csvAllowed == null || csvAllowed.isBlank()) {
            return false;
        }
        String[] parts = csvAllowed.split(",");
        for (String p : parts) {
            if (toolName.equals(p.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String buildToolContextPrompt(String toolName, Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 工具上下文\n");
        sb.append("- tool: ").append(toolName).append("\n");
        sb.append("- result: ").append(result != null ? result.toString() : "{}").append("\n");
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
