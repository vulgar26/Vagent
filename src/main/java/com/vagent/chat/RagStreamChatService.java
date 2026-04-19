package com.vagent.chat;

import com.vagent.chat.message.Message;
import com.vagent.chat.message.MessageService;
import com.vagent.chat.rag.EmptyHitsBehavior;
import com.vagent.chat.rag.RagProperties;
import com.vagent.conversation.ConversationService;
import com.vagent.eval.EvalApiProperties;
import com.vagent.eval.EvalBehaviorMetaSync;
import com.vagent.eval.EvalChatSafetyGate;
import com.vagent.eval.EvalQuoteOnlyGuard;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.RagRetrieveResult;
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
import com.vagent.rag.RagKnowledgeSystemPrompts;
import com.vagent.rag.gate.RagPostRetrieveGate;
import com.vagent.rag.gate.RagPostRetrieveGateSettings;
import com.vagent.guardrails.GuardrailsProperties;
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
import java.util.Optional;
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
 *
 * <p><b>P1-0 门控与 {@code query} 口径：</b>检索使用 {@code rewrite.retrievalQuery()}；{@link RagPostRetrieveGate#shortCircuitAfterRetrieve} 的「过短 / 模糊子串」
 * 当前传入<strong>用户原句</strong> {@code userMessage}（与评测「整段题面」角色对应，但与检索改写句可不一致）。阈值与子串由 {@link RagPostRetrieveGateSettings} 解析（优先 {@code vagent.rag.gate.*}）。详见 {@link RagPostRetrieveGate} 与
 * {@code plans/vagent-upgrade.md} §P1-0。
 *
 * <p><b>SSE 首帧 {@code meta} 与评测根级对齐（P1-0 收口）：</b>首条 {@code type=meta} 均经 {@link com.vagent.eval.EvalBehaviorMetaSync#applyRootToMeta}
 * 写入 {@code behavior}/{@code error_code}，与 {@link com.vagent.eval.dto.EvalChatResponse} 根级字段<strong>同值</strong>；成功走主链路 LLM 时为
 * {@code behavior=answer} 且不写 {@code error_code}（等同根级无归因码）。安全短路、检索后门控短路、意图澄清分支同理。</p>
 *
 * <p>可选：{@code vagent.guardrails.quote-only.enabled=true} 且 {@code apply-to-sse-stream=true} 时，RAG 有命中则先缓冲 LLM 全文，
 * 再经 {@link EvalQuoteOnlyGuard} 与 eval 同源写 {@code meta} 后一次性 {@code chunk}（见 {@code plans/quote-only-guardrails.md}）。</p>
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
    private final EvalApiProperties evalApiProperties;
    private final RagPostRetrieveGateSettings ragPostRetrieveGateSettings;
    private final GuardrailsProperties guardrailsProperties;

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
            EvalApiProperties evalApiProperties,
            RagPostRetrieveGateSettings ragPostRetrieveGateSettings,
            GuardrailsProperties guardrailsProperties,
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
        this.evalApiProperties = evalApiProperties;
        this.ragPostRetrieveGateSettings = ragPostRetrieveGateSettings;
        this.guardrailsProperties = guardrailsProperties;
        this.llmStreamExecutor = llmStreamExecutor;
    }

    public SseEmitter stream(UUID userId, String conversationId, String userMessage) {
        conversationService
                .findOwnedByUser(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "会话不存在或无权访问"));

        String userKey = UserIdFormats.canonical(userId);

        if (evalApiProperties.isSafetyRulesEnabled()) {
            Optional<EvalChatSafetyGate.Outcome> safety =
                    EvalChatSafetyGate.evaluatePreRetrieval(userMessage, false);
            if (safety.isPresent()) {
                String taskId = taskRegistry.registerTask(userKey, conversationId);
                SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
                emitter.onCompletion(() -> taskRegistry.remove(taskId));
                emitter.onTimeout(() -> taskRegistry.remove(taskId));
                emitter.onError(e -> taskRegistry.remove(taskId));
                EvalChatSafetyGate.Outcome o = safety.get();
                llmStreamExecutor.execute(
                        () -> runSafetyShortCircuitStream(emitter, taskId, conversationId, userId, o));
                return emitter;
            }
        }

        List<Message> history =
                messageService.listRecentForConversation(conversationId, ragProperties.getMaxHistoryMessages());

        messageService.saveUserMessage(conversationId, userId, userMessage);

        RewriteResult rewrite = queryRewriteService.rewriteForRetrieval(userMessage, history);

        ChatBranch branch = ChatBranch.RAG;
        IntentResult intent = null;
        if (orchestrationProperties.isIntentEnabled()) {
            intent = intentResolutionService.resolve(userMessage);
            branch = intent.branch();
        }

        String taskId = taskRegistry.registerTask(userKey, conversationId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> taskRegistry.remove(taskId));
        emitter.onTimeout(() -> taskRegistry.remove(taskId));
        emitter.onError(e -> taskRegistry.remove(taskId));

        if (branch == ChatBranch.CLARIFICATION) {
            String guidance =
                    intent != null && intent.optionalClarificationHint().isPresent()
                            ? intent.optionalClarificationHint().get()
                            : orchestrationProperties.getClarificationTemplate();
            Map<String, Object> clarifyMeta = new LinkedHashMap<>();
            EvalBehaviorMetaSync.applyRootToMeta(clarifyMeta, "clarify", null);
            llmStreamExecutor.execute(
                    () ->
                            runFixedAssistantStream(
                                    emitter,
                                    taskId,
                                    conversationId,
                                    userId,
                                    guidance != null ? guidance : "",
                                    ChatBranch.CLARIFICATION.name(),
                                    0,
                                    clarifyMeta));
            return emitter;
        }

        List<RetrieveHit> hits;
        RagRetrieveResult retrieveTrace = null;
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

            retrieveTrace = knowledgeRetrieveService.searchForRag(userId, rewrite.retrievalQuery(), ragProperties);
            hits = retrieveTrace.hits();
            // P1-0：门控「过短/子串」用 userMessage；检索用 rewrite.retrievalQuery()（见 RagPostRetrieveGate 与 vagent-upgrade §P1-0）
            Optional<RagPostRetrieveGate.ShortCircuit> gate =
                    RagPostRetrieveGate.shortCircuitAfterRetrieve(
                            userMessage,
                            hits,
                            RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                            ragPostRetrieveGateSettings.lowConfidenceCosineDistanceThreshold(),
                            ragPostRetrieveGateSettings.lowConfidenceQuerySubstrings(),
                            RagPostRetrieveGate.ZeroHitsPolicy.RESPECT_RAG_PROPERTIES,
                            ragProperties.getEmptyHitsBehavior(),
                            ragProperties.getEmptyHitsNoLlmMessage());
            if (gate.isPresent()) {
                final RagPostRetrieveGate.ShortCircuit gateSc = gate.get();
                final RagRetrieveResult traceForGate = retrieveTrace;
                llmStreamExecutor.execute(
                        () ->
                                runRagGateShortCircuitStream(
                                        emitter, taskId, conversationId, userId, gateSc, traceForGate));
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
        final RagRetrieveResult retrieveTraceFinal = retrieveTrace;
        final List<RetrieveHit> ragHitsFinal = List.copyOf(hits);
        llmStreamExecutor.execute(
                () ->
                        runAsyncStream(
                                emitter,
                                taskId,
                                prepared,
                                conversationId,
                                userId,
                                hitCount,
                                branchName,
                                toolUsedFinal,
                                toolNameFinal,
                                toolOutcomeFinal,
                                toolErrorFinal,
                                retrieveTraceFinal,
                                ragHitsFinal));
        return emitter;
    }

    private void runAsyncStream(
            SseEmitter emitter,
            String taskId,
            LlmChatRequest prepared,
            String conversationId,
            UUID userId,
            int hitCount,
            String branch,
            boolean toolUsed,
            String toolName,
            String toolOutcome,
            String toolError,
            RagRetrieveResult retrieveTrace,
            List<RetrieveHit> ragHitsForGuard) {
        try {
            Map<String, Object> metaExtra = new LinkedHashMap<>();
            if (hitCount == 0 && ragProperties.getEmptyHitsBehavior() == EmptyHitsBehavior.ALLOW_LLM) {
                RagPostRetrieveGate.applyZeroHitsAllowLlmMeta(metaExtra);
            }
            boolean sseBufferedQuoteOnly =
                    guardrailsProperties != null
                            && guardrailsProperties.getQuoteOnly().isEnabled()
                            && guardrailsProperties.getQuoteOnly().isApplyToSseStream()
                            && ChatBranch.RAG.name().equals(branch)
                            && hitCount > 0
                            && ragHitsForGuard != null
                            && !EvalQuoteOnlyGuard.corpusFromRetrieveHits(ragHitsForGuard).isEmpty();

            if (!sseBufferedQuoteOnly) {
                EvalBehaviorMetaSync.applyRootToMeta(metaExtra, "answer", null);
                sendMeta(
                        emitter,
                        taskId,
                        hitCount,
                        branch,
                        toolUsed,
                        toolName,
                        toolOutcome,
                        toolError,
                        retrieveTrace,
                        metaExtra);
            }

            StringBuilder assistantBuffer = new StringBuilder();
            if (sseBufferedQuoteOnly) {
                String qs =
                        guardrailsProperties.getQuoteOnly().getStrictness() != null
                                ? guardrailsProperties.getQuoteOnly().getStrictness().trim().toLowerCase(Locale.ROOT)
                                : "moderate";
                llmSseStreamingBridge.streamChatToSse(
                        emitter,
                        taskId,
                        prepared,
                        assistantBuffer,
                        () ->
                                messageService.saveAssistantMessage(
                                        conversationId, userId, assistantBuffer.toString()),
                        (emitter2, buf) -> {
                            metaExtra.put("quote_only", true);
                            metaExtra.put("quote_only_strictness", qs);
                            if (!metaExtra.containsKey("guardrail_triggered")) {
                                metaExtra.put("guardrail_triggered", false);
                            }
                            var patchOpt =
                                    EvalQuoteOnlyGuard.evaluate(
                                            EvalQuoteOnlyGuard.Strictness.fromConfig(
                                                    guardrailsProperties.getQuoteOnly().getStrictness()),
                                            buf.toString(),
                                            EvalQuoteOnlyGuard.corpusFromRetrieveHits(ragHitsForGuard));
                            if (patchOpt.isPresent()) {
                                var p = patchOpt.get();
                                buf.setLength(0);
                                buf.append(p.answer());
                                metaExtra.put("guardrail_triggered", true);
                                metaExtra.put("reflection_outcome", p.reflectionOutcome());
                                metaExtra.put("reflection_reasons", p.reflectionReasons());
                                EvalBehaviorMetaSync.applyRootToMeta(metaExtra, p.behavior(), p.errorCode());
                            } else {
                                metaExtra.put("quote_only_passed", true);
                                EvalBehaviorMetaSync.applyRootToMeta(metaExtra, "answer", null);
                            }
                            sendMeta(
                                    emitter2,
                                    taskId,
                                    hitCount,
                                    branch,
                                    toolUsed,
                                    toolName,
                                    toolOutcome,
                                    toolError,
                                    retrieveTrace,
                                    metaExtra);
                            sendEvent(emitter2, Map.of("type", "chunk", "text", buf.toString()));
                        });
            } else {
                llmSseStreamingBridge.streamChatToSse(
                        emitter,
                        taskId,
                        prepared,
                        assistantBuffer,
                        () ->
                                messageService.saveAssistantMessage(
                                        conversationId, userId, assistantBuffer.toString()));
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void runSafetyShortCircuitStream(
            SseEmitter emitter,
            String taskId,
            String conversationId,
            UUID userId,
            EvalChatSafetyGate.Outcome outcome) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "meta");
            meta.put("taskId", taskId);
            meta.put("hitCount", 0);
            meta.put("retrieve_hit_count", 0);
            meta.put("branch", ChatBranch.RAG.name());
            meta.put("toolUsed", false);
            applySafetyShortCircuitMeta(meta, outcome);
            EvalBehaviorMetaSync.applyRootToMeta(meta, outcome.behavior(), outcome.errorCode());
            sendEvent(emitter, meta);
            if (taskRegistry.isCancelled(taskId)) {
                sendEvent(emitter, Map.of("type", "cancelled"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, Map.of("type", "chunk", "text", outcome.answer()));
            sendEvent(emitter, Map.of("type", "done"));
            // 与评测短路一致：不写入 messages，避免攻击面 query/拒答文案进入会话历史
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private static void applySafetyShortCircuitMeta(
            Map<String, Object> meta, EvalChatSafetyGate.Outcome outcome) {
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        meta.put("eval_safety_rule_id", outcome.ruleId());
        if ("deny".equals(outcome.behavior())) {
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
        } else {
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("SAFETY_QUERY_GATE"));
        }
    }

    private void runRagGateShortCircuitStream(
            SseEmitter emitter,
            String taskId,
            String conversationId,
            UUID userId,
            RagPostRetrieveGate.ShortCircuit gate,
            RagRetrieveResult retrieveTrace) {
        try {
            int hc = retrieveTrace != null ? retrieveTrace.hits().size() : 0;
            Map<String, Object> metaExtra = new LinkedHashMap<>();
            metaExtra.put("low_confidence", gate.lowConfidence());
            metaExtra.put("low_confidence_reasons", gate.lowConfidenceReasons());
            EvalBehaviorMetaSync.applyRootToMeta(metaExtra, gate.behavior(), gate.errorCode());
            sendMeta(
                    emitter,
                    taskId,
                    hc,
                    ChatBranch.RAG.name(),
                    false,
                    null,
                    null,
                    null,
                    retrieveTrace,
                    metaExtra);
            if (taskRegistry.isCancelled(taskId)) {
                sendEvent(emitter, Map.of("type", "cancelled"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, Map.of("type", "chunk", "text", gate.answer()));
            sendEvent(emitter, Map.of("type", "done"));
            messageService.saveAssistantMessage(conversationId, userId, gate.answer());
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
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
            UUID userId,
            String fullText,
            String branchName,
            int hitCount,
            Map<String, Object> metaExtra) {
        try {
            sendMeta(
                    emitter,
                    taskId,
                    hitCount,
                    branchName,
                    false,
                    null,
                    null,
                    null,
                    null,
                    metaExtra != null ? metaExtra : Map.of());
            if (taskRegistry.isCancelled(taskId)) {
                sendEvent(emitter, Map.of("type", "cancelled"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, Map.of("type", "chunk", "text", fullText));
            sendEvent(emitter, Map.of("type", "done"));
            messageService.saveAssistantMessage(conversationId, userId, fullText);
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
            String toolError,
            RagRetrieveResult retrieveTrace,
            Map<String, Object> additionalMeta)
            throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "meta");
        meta.put("taskId", taskId);
        meta.put("hitCount", hitCount);
        meta.put("retrieve_hit_count", hitCount);
        meta.put("branch", branch);
        if (retrieveTrace != null) {
            retrieveTrace.putRetrievalTrace(meta);
        }
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
        if (additionalMeta != null && !additionalMeta.isEmpty()) {
            for (Map.Entry<String, Object> e : additionalMeta.entrySet()) {
                meta.put(e.getKey(), e.getValue());
            }
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
        return RagKnowledgeSystemPrompts.buildFromHits(hits);
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
