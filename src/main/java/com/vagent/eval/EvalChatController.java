package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.eval.dto.EvalChatRequest;
import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.eval.evidence.EvidenceMapExtractor;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.RagRetrieveResult;
import com.vagent.kb.dto.RetrieveHit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.vagent.chat.rag.EmptyHitsBehavior;
import com.vagent.guardrails.GuardrailsProperties;
import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.LlmStreamSink;
import com.vagent.llm.config.LlmProperties;
import com.vagent.rag.RagKnowledgeSystemPrompts;
import com.vagent.rag.gate.RagPostRetrieveGate;
import com.vagent.rag.gate.RagPostRetrieveGateSettings;
import com.vagent.eval.stub.EvalStubToolService;
import com.vagent.mcp.client.McpClient;
import com.vagent.mcp.config.McpProperties;
import com.vagent.mcp.tools.McpToolArgumentSchemaValidator;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * P0：评测专用对话接口（非流式 + snake_case）。
 *
 * <p>Day1：请求/响应形态、读取 X-Eval-*、enabled + token-hash 校验。</p>
 * <p>Day2：sources[] 服务端生成、snippet 规则截断（≤300）。</p>
 * <p>Day3：空命中/低置信门控（retrieve_hit_count、low_confidence、low_confidence_reasons、error_code）。</p>
 * <p>Day4：EVAL_DEBUG + vagent.eval.api.debug-enabled 时可在 meta 输出明文 {@code retrieval_hit_ids[]}；否则绝不输出。</p>
 * <p>Day6：可选 {@code vagent.eval.api.allow-cidrs} 限制明文 debug 的客户端网段；返回前再次剔除越界 {@code retrieval_hit_ids}。</p>
 * <p>Day7：{@code vagent.guardrails.reflection.enabled} 时一次性门控：{@code meta.guardrail_triggered}、{@code reflection_outcome}、
 * {@code reflection_reasons[]} 与可归因 {@code error_code}（如 {@code SOURCE_NOT_IN_HITS}、{@code GUARDRAIL_TRIGGERED}）。</p>
 * <p>Quote-only：{@code vagent.guardrails.quote-only.enabled} 且请求 {@code quote_only=true} 时，由 {@link EvalQuoteOnlyGuard} 执行；
 * {@code strictness} 与 {@code scope} 见 {@code plans/quote-only-guardrails.md}。</p>
 *
 * <p>P0+：读取 {@code X-Eval-Membership-Top-N}（缺省用 {@code vagent.eval.api.membership-top-n}），使 {@code sources} 与根级
 * {@code retrieval_hits} 同前 N 条候选，与 vagent-eval {@code verifyCitationMembership} 的 top_n 对齐（§16.4）。</p>
 * <p>可选：检索后低置信门控阈值与子串见 {@code vagent.rag.gate.low-confidence-*}（主 SSOT）；未配置时回退
 * {@code vagent.eval.api.low-confidence-*}（已弃用别名），与 P0 {@code rag/low_conf} 对齐（默认关闭）。</p>
 * <p>P0+ B 线：{@code vagent.eval.api.safety-rules-enabled} 为 true 时，检索前经 {@link EvalChatSafetyGate} 做拒答/澄清短路。</p>
 * <p>可选：{@code vagent.eval.api.full-answer-enabled=true} 时，在未门控短路且仍为 {@code answer} 路径下调用 {@link com.vagent.llm.LlmClient}
 * 生成正文（与主链路同款提示词）；默认 false 保持占位 {@code "OK"} 以控成本与 CI 稳定性。</p>
 * <p>{@code tool_policy=real} 且 {@code expected_behavior=tool}：在 {@code vagent.mcp.enabled}、白名单与 {@link McpClient} Bean 就绪时，
 * 将 JSON {@code tool_stub_id} 作为 MCP 工具名<strong>同步</strong>调用（与 SSE 主链路 {@link com.vagent.chat.RagStreamChatService} 相同：
 * {@code tools/call} 的 HTTP 超时由 {@code vagent.mcp.tool-call-timeout} 决定，不经 {@code vagent.eval.api.stub-tool-timeout-ms}）。</p>
 */
/**
 * Bean 名显式指定，避免与包内其他 {@code EvalChatController}（若存在）默认名 {@code evalChatController} 冲突。
 */
@RestController("vagentEvalChatController")
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    /** Day5：hashed membership 候选集上限（强制 N≤50）。 */
    private static final int RETRIEVAL_CANDIDATE_LIMIT_N = RetrievalMembershipHasher.ENGINE_MEMBERSHIP_CAP;

    private final EvalApiProperties evalApiProperties;
    private final EvalDebugNetworkPolicy debugNetworkPolicy;
    private final GuardrailsProperties guardrailsProperties;
    private final RagProperties ragProperties;
    private final KnowledgeRetrieveService knowledgeRetrieveService;
    private final EvalTokenVerifier tokenVerifier;
    private final RagPostRetrieveGateSettings ragPostRetrieveGateSettings;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final EvalStubToolService evalStubToolService;
    private final McpProperties mcpProperties;
    private final ObjectProvider<McpClient> mcpClientProvider;
    private final McpToolArgumentSchemaValidator mcpToolArgumentSchemaValidator;

    public EvalChatController(
            EvalApiProperties evalApiProperties,
            EvalDebugNetworkPolicy debugNetworkPolicy,
            GuardrailsProperties guardrailsProperties,
            RagProperties ragProperties,
            KnowledgeRetrieveService knowledgeRetrieveService,
            RagPostRetrieveGateSettings ragPostRetrieveGateSettings,
            LlmClient llmClient,
            LlmProperties llmProperties,
            EvalStubToolService evalStubToolService,
            McpProperties mcpProperties,
            ObjectProvider<McpClient> mcpClientProvider,
            McpToolArgumentSchemaValidator mcpToolArgumentSchemaValidator) {
        this.evalApiProperties = evalApiProperties;
        this.debugNetworkPolicy = debugNetworkPolicy;
        this.guardrailsProperties = guardrailsProperties;
        this.ragProperties = ragProperties;
        this.knowledgeRetrieveService = knowledgeRetrieveService;
        this.tokenVerifier = new EvalTokenVerifier(evalApiProperties);
        this.ragPostRetrieveGateSettings = ragPostRetrieveGateSettings;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.evalStubToolService = evalStubToolService;
        this.mcpProperties = mcpProperties;
        this.mcpClientProvider = mcpClientProvider;
        this.mcpToolArgumentSchemaValidator = mcpToolArgumentSchemaValidator;
    }

    @PostMapping("/chat")
    public EvalChatResponse chat(
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Eval-Token", required = false) String xEvalToken,
            @RequestHeader(value = "X-Eval-Run-Id", required = false) String xEvalRunId,
            @RequestHeader(value = "X-Eval-Dataset-Id", required = false) String xEvalDatasetId,
            @RequestHeader(value = "X-Eval-Case-Id", required = false) String xEvalCaseId,
            @RequestHeader(value = "X-Eval-Target-Id", required = false) String xEvalTargetId,
            @RequestHeader(value = "X-Eval-Membership-Top-N", required = false) String xMembershipTopN,
            @Valid @RequestBody EvalChatRequest request) {
        long startNs = System.nanoTime();

        if (!tokenVerifier.isEnabled()) {
            // SSOT：disabled -> 404（隐藏存在性）
            throw new ResponseStatusException(NOT_FOUND);
        }

        String mode = request.getMode() != null && !request.getMode().isBlank() ? request.getMode().trim() : "EVAL";

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("mode", mode);
        // Day1：读取 X-Eval-*（不强制回传，但可用于 debug/审计；后续会用于 hashed membership key derivation）
        if (xEvalRunId != null && !xEvalRunId.isBlank()) meta.put("x_eval_run_id", xEvalRunId.trim());
        if (xEvalDatasetId != null && !xEvalDatasetId.isBlank()) meta.put("x_eval_dataset_id", xEvalDatasetId.trim());
        if (xEvalCaseId != null && !xEvalCaseId.isBlank()) meta.put("x_eval_case_id", xEvalCaseId.trim());
        if (xEvalTargetId != null && !xEvalTargetId.isBlank()) meta.put("x_eval_target_id", xEvalTargetId.trim());
        if (xMembershipTopN != null && !xMembershipTopN.isBlank()) {
            meta.put("x_eval_membership_top_n", xMembershipTopN.trim());
        }

        if (!tokenVerifier.verifyOrFalse(xEvalToken)) {
            EvalBehaviorMetaSync.applyRootToMeta(meta, "deny", "AUTH");
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(request),
                    meta,
                    List.of(),
                    List.of(),
                    "AUTH");
        }

        String q = request.getQuery() == null ? "" : request.getQuery().trim();
        if (evalApiProperties.isSafetyRulesEnabled()) {
            Optional<EvalChatSafetyGate.Outcome> safety =
                    EvalChatSafetyGate.evaluatePreRetrieval(q, request.getRequiresCitations());
            if (safety.isPresent()) {
                applySafetyShortCircuitMeta(meta, safety.get());
                EvalChatSafetyGate.Outcome o = safety.get();
                EvalBehaviorMetaSync.applyRootToMeta(meta, o.behavior(), o.errorCode());
                enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
                long latencyMsEarly = (System.nanoTime() - startNs) / 1_000_000L;
                return new EvalChatResponse(
                        o.answer(),
                        o.behavior(),
                        latencyMsEarly,
                        capabilitiesEffective(request),
                        meta,
                        List.of(),
                        List.of(),
                        o.errorCode());
            }
        }

        if (isStubToolRequiredButStubDisabled(request)) {
            return respondStubToolFeatureDisabled(request, meta, mode, httpRequest, startNs);
        }
        if (isStubToolEvalRequest(request)) {
            return respondStubToolEval(request, meta, mode, httpRequest, xEvalCaseId, startNs, q);
        }
        if (isRealToolEvalRequest(request)) {
            return respondRealToolEval(request, meta, mode, httpRequest, startNs, q);
        }
        if (isToolExpectedWithoutStubEvalExecution(request)) {
            return respondToolExpectedNonStubEval(request, meta, mode, httpRequest, startNs);
        }

        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        List<RetrieveHit> hits = List.of();
        List<EvalChatResponse.Source> sources = List.of();

        if (!retrievalSupported || knowledgeRetrieveService == null) {
            meta.put("retrieve_hit_count", 0);
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
            meta.put("low_confidence_gate", "none");
            meta.put("disabled_reason", "RETRIEVAL_DISABLED");
            EvalBehaviorMetaSync.applyRootToMeta(meta, "deny", "POLICY_DISABLED");
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "检索能力未启用。",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(request),
                    meta,
                    sources,
                    List.of(),
                    "POLICY_DISABLED");
        }

        UUID evalUserId = EvalStableUserId.fromEvalTargetId(xEvalTargetId);
        RagRetrieveResult retrieveResult =
                knowledgeRetrieveService.searchForRag(evalUserId, q, ragProperties);
        hits = retrieveResult.hits();
        retrieveResult.putRetrievalTrace(meta);
        int candidateTotal = hits.size();
        int membershipCap = resolveMembershipCap(xMembershipTopN);
        int limitN = Math.min(membershipCap, candidateTotal);
        List<RetrieveHit> candidates = hits.subList(0, limitN);
        sources = hitsToSources(candidates);
        List<EvalChatResponse.RetrievalHit> retrievalHits = hitsToRetrievalHits(candidates);
        int hitCount = candidateTotal;
        meta.put("retrieve_hit_count", hitCount);
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", limitN);
        meta.put("retrieval_candidate_total", candidateTotal);

        // Day5：非 debug 也能验证引用闭环：对候选集前 N 生成 hashed membership（HMAC 列表）
        // key 派生材料来自 X-Eval-Token + (targetId,datasetId,caseId)，与 eval 侧对齐
        meta.put(
                "retrieval_hit_id_hashes",
                RetrievalMembershipHasher.buildEvalHitIdHashes(
                        xEvalToken,
                        xEvalTargetId,
                        xEvalDatasetId,
                        xEvalCaseId,
                        canonicalIdsFromHits(candidates)));

        String answer = "OK";
        String behavior = "answer";
        String errorCode = null;
        boolean lowConfidence;

        // P1-0：门控 query 与上文 searchForRag 题面一致（trim 后的 q）；见 RagPostRetrieveGate 与 vagent-upgrade §P1-0
        Optional<RagPostRetrieveGate.ShortCircuit> gate =
                RagPostRetrieveGate.shortCircuitAfterRetrieve(
                        q,
                        hits,
                        RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                        ragPostRetrieveGateSettings.lowConfidenceCosineDistanceThreshold(),
                        ragPostRetrieveGateSettings.lowConfidenceQuerySubstrings(),
                        RagPostRetrieveGate.ZeroHitsPolicy.EVAL_ALIGNED,
                        EmptyHitsBehavior.ALLOW_LLM,
                        null,
                        RagPostRetrieveGate.LowConfidenceBehavior.fromConfig(ragProperties.getLowConfidenceBehavior()),
                        RagPostRetrieveGate.parseLowConfidenceRuleSet(ragProperties.getLowConfidenceRuleSet()));
        if (gate.isPresent()) {
            RagPostRetrieveGate.ShortCircuit sc = gate.get();
            answer = sc.answer();
            behavior = sc.behavior();
            errorCode = sc.errorCode();
            lowConfidence = sc.lowConfidence();
            meta.put("low_confidence", sc.lowConfidence());
            meta.put("low_confidence_reasons", sc.lowConfidenceReasons());
            meta.put("low_confidence_gate", "post_retrieve_gate");
        } else {
            var lcBehavior =
                    RagPostRetrieveGate.LowConfidenceBehavior.fromConfig(ragProperties.getLowConfidenceBehavior());
            var lcRules = RagPostRetrieveGate.parseLowConfidenceRuleSet(ragProperties.getLowConfidenceRuleSet());
            if (lcBehavior == RagPostRetrieveGate.LowConfidenceBehavior.ALLOW_LLM) {
                List<String> reasons =
                        RagPostRetrieveGate.computeLowConfidenceReasons(
                                q,
                                hits,
                                RagPostRetrieveGate.DEFAULT_MIN_QUERY_CHARS,
                                ragPostRetrieveGateSettings.lowConfidenceCosineDistanceThreshold(),
                                ragPostRetrieveGateSettings.lowConfidenceQuerySubstrings(),
                                lcRules);
                if (!reasons.isEmpty()) {
                    lowConfidence = true;
                    meta.put("low_confidence", true);
                    meta.put("low_confidence_reasons", reasons);
                    meta.put("low_confidence_gate", "post_retrieve_allow_llm");
                } else {
                    lowConfidence = false;
                    meta.put("low_confidence", false);
                    meta.put("low_confidence_reasons", List.of());
                    meta.put("low_confidence_gate", "none");
                }
            } else {
                lowConfidence = false;
                meta.put("low_confidence", false);
                meta.put("low_confidence_reasons", List.of());
                meta.put("low_confidence_gate", "none");
            }
        }

        if (evalApiProperties.isFullAnswerEnabled()
                && gate.isEmpty()
                && "answer".equals(behavior)) {
            var gen = generateEvalFullAnswer(q, candidates, meta);
            answer = gen.answer();
            if (gen.errorCode() != null) {
                errorCode = gen.errorCode();
            }
        }

        if (guardrailsProperties.getReflection().isEnabled()) {
            meta.put("guardrail_triggered", false);
            Set<String> allowedHitIds = allowedHitIdsFromCandidates(candidates);
            var patchOpt =
                    EvalReflectionOneShotGuard.evaluate(
                            true,
                            guardrailsProperties.getReflection().getMaxAnswerCharsWhenLowConfidence(),
                            request.getRequiresCitations(),
                            hitCount,
                            allowedHitIds,
                            sources,
                            lowConfidence,
                            answer);
            if (patchOpt.isPresent()) {
                EvalReflectionOneShotGuard.Patch p = patchOpt.get();
                answer = p.answer();
                behavior = p.behavior();
                errorCode = p.errorCode();
                meta.put("guardrail_triggered", true);
                meta.put("reflection_outcome", p.reflectionOutcome());
                meta.put("reflection_reasons", p.reflectionReasons());
            }
        }

        // Reflection（引用/低置信）先于 quote-only：若已非 answer，不再跑 quote-only。
        EvalQuoteOnlyGuard.QuoteOnlyOutcome quoteOutcomeForEvidence = EvalQuoteOnlyGuard.QuoteOnlyOutcome.none();
        if (shouldRunQuoteOnly(request, candidates, behavior)) {
            String qs =
                    guardrailsProperties.getQuoteOnly().getStrictness() != null
                            ? guardrailsProperties.getQuoteOnly().getStrictness().trim().toLowerCase(Locale.ROOT)
                            : "moderate";
            String qoScope =
                    guardrailsProperties.getQuoteOnly().getScope() != null
                            ? guardrailsProperties.getQuoteOnly().getScope().trim().toLowerCase(Locale.ROOT)
                            : "digits_plus_tokens";
            meta.put("quote_only", true);
            meta.put("quote_only_strictness", qs);
            meta.put("quote_only_scope", qoScope);
            if (!meta.containsKey("guardrail_triggered")) {
                meta.put("guardrail_triggered", false);
            }
            EvalQuoteOnlyGuard.Scope quoteScope =
                    EvalQuoteOnlyGuard.Scope.fromConfig(guardrailsProperties.getQuoteOnly().getScope());
            quoteOutcomeForEvidence =
                    EvalQuoteOnlyGuard.evaluateWithOutcome(
                            EvalQuoteOnlyGuard.Strictness.fromConfig(guardrailsProperties.getQuoteOnly().getStrictness()),
                            quoteScope,
                            answer,
                            EvalQuoteOnlyGuard.corpusFromRetrieveHits(candidates),
                            quoteScope == EvalQuoteOnlyGuard.Scope.DIGITS_PLUS_TOKENS_PLUS_EVIDENCE
                                    ? sources
                                    : null);
            Optional<EvalReflectionOneShotGuard.Patch> quotePatch = quoteOutcomeForEvidence.patch();
            if (quotePatch.isPresent()) {
                EvalReflectionOneShotGuard.Patch p = quotePatch.get();
                answer = p.answer();
                behavior = p.behavior();
                errorCode = p.errorCode();
                meta.put("guardrail_triggered", true);
                meta.put("reflection_outcome", p.reflectionOutcome());
                meta.put("reflection_reasons", p.reflectionReasons());
            } else {
                meta.put("quote_only_passed", true);
            }
        }

        // Day4：debug 模式可返回明文 hit ids（同 Day5 “候选集前 N” 口径）；Day6：受 allow-cidrs 约束
        maybeAttachDebugRetrievalHitIds(meta, mode, httpRequest, candidates);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);

        List<EvalChatResponse.EvidenceMapItem> evidenceMap = List.of();
        boolean evidenceMapRequired = Boolean.TRUE.equals(request.getRequiresCitations());
        if (evidenceMapRequired && "answer".equals(behavior)) {
            final EvalQuoteOnlyGuard.QuoteOnlyOutcome quoteOutcomeSnapshot = quoteOutcomeForEvidence;
            final String answerForEvidence = answer;
            final List<EvalChatResponse.Source> sourcesForEvidence = sources;
            evidenceMap =
                    quoteOutcomeSnapshot.plusEvidenceMapSnapshot()
                            .filter(list -> !list.isEmpty())
                            .orElseGet(() -> EvidenceMapExtractor.buildEvidenceMap(answerForEvidence, sourcesForEvidence));
            if (evidenceMap.isEmpty()) {
                // P1-S1：requires_citations=true 时必须提供可规则验证的 evidence_map，否则视为不被证据支撑。
                behavior = "deny";
                errorCode = "EVIDENCE_NOT_SUPPORTED";
                answer = "无法从回答与引用片段中生成可验证的证据映射，请改为仅输出可被引用片段直接支撑的数字/日期等结论。";
                meta.put("guardrail_triggered", true);
                meta.put("reflection_outcome", "deny");
                meta.put("reflection_reasons", List.of("EVIDENCE_MAP_EMPTY"));
                meta.put("evidence_map_required", true);
                meta.put("evidence_map_outcome", "missing");
            } else {
                meta.put("evidence_map_required", true);
                meta.put("evidence_map_outcome", "ok");
            }
        }

        EvalBehaviorMetaSync.applyRootToMeta(meta, behavior, errorCode);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        return new EvalChatResponse(
                answer,
                behavior,
                latencyMs,
                capabilitiesEffective(request),
                meta,
                sources,
                retrievalHits,
                errorCode,
                null,
                evidenceMap);
    }

    private EvalChatResponse respondStubToolFeatureDisabled(
            EvalChatRequest request,
            Map<String, Object> meta,
            String mode,
            HttpServletRequest httpRequest,
            long startNs) {
        meta.put("retrieve_hit_count", 0);
        meta.put("low_confidence", false);
        meta.put("low_confidence_reasons", List.of());
        meta.put("low_confidence_gate", "none");
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        meta.put("stub_tools_disabled", true);
        meta.put("tool_policy", "stub");
        String msg = "评测桩工具已在配置中关闭（vagent.eval.api.stub-tools-enabled=false）。";
        EvalBehaviorMetaSync.applyRootToMeta(meta, "clarify", null);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        EvalChatResponse.Tool toolBlock = new EvalChatResponse.Tool(true, false, false, "", "skipped", 0L);
        return new EvalChatResponse(
                msg,
                "clarify",
                latencyMs,
                capabilitiesEffective(request),
                meta,
                List.of(),
                List.of(),
                null,
                toolBlock,
                null);
    }

    /**
     * {@code expected_behavior=tool} 且当前请求不会进入 {@link #respondStubToolEval}（例如 {@code tool_policy=disabled|real}）时，
     * 禁止误走 RAG 并返回 {@code behavior=answer}，以免与题集工具语义冲突。
     */
    private EvalChatResponse respondToolExpectedNonStubEval(
            EvalChatRequest request,
            Map<String, Object> meta,
            String mode,
            HttpServletRequest httpRequest,
            long startNs) {
        meta.put("retrieve_hit_count", 0);
        meta.put("low_confidence", false);
        meta.put("low_confidence_reasons", List.of());
        meta.put("low_confidence_gate", "none");
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        String pol = normalizedToolPolicy(request.getToolPolicy());
        meta.put("tool_policy", pol);
        meta.put("tool_eval_non_stub", true);
        String msg =
                "real".equals(pol)
                        ? "本题为工具题（expected_behavior=tool），但 tool_policy=real 当前无法执行：需 vagent.mcp.enabled=true、"
                                + "allowed-tools 非空且进程内存在 McpClient Bean；并请在 JSON 中提供 tool_stub_id 作为工具名。可改用 stub 或在评测侧跳过。"
                        : ("stub".equals(pol)
                                ? "本题为工具题但未进入桩执行，请检查 vagent.eval.api.stub-tools-enabled 与 tool_policy。"
                                : "本题为工具题（expected_behavior=tool），但 tool_policy=disabled：未执行工具；请改用 stub 或在评测侧跳过。");
        EvalBehaviorMetaSync.applyRootToMeta(meta, "clarify", null);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        EvalChatResponse.Tool toolBlock = new EvalChatResponse.Tool(true, false, false, "", "skipped", 0L);
        return new EvalChatResponse(
                msg,
                "clarify",
                latencyMs,
                capabilitiesEffective(request),
                meta,
                List.of(),
                List.of(),
                null,
                toolBlock,
                null);
    }

    private EvalChatResponse respondStubToolEval(
            EvalChatRequest request,
            Map<String, Object> meta,
            String mode,
            HttpServletRequest httpRequest,
            String xEvalCaseId,
            long startNs,
            String q) {
        meta.put("retrieve_hit_count", 0);
        meta.put("low_confidence", false);
        meta.put("low_confidence_reasons", List.of());
        meta.put("low_confidence_gate", "none");
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        meta.put("eval_stub_tools", true);
        meta.put("tool_policy", "stub");

        EvalStubToolService.Result r = evalStubToolService.runStub(xEvalCaseId, q);
        EvalChatResponse.Tool toolBlock =
                new EvalChatResponse.Tool(
                        true, true, r.succeeded(), r.toolName(), r.outcome(), r.latencyMs());

        String behavior = "tool";
        String errorCode = null;
        if ("timeout".equalsIgnoreCase(r.outcome())) {
            errorCode = "TOOL_TIMEOUT";
        } else if (!r.succeeded()) {
            errorCode = "TOOL_ERROR";
        }

        EvalBehaviorMetaSync.applyRootToMeta(meta, behavior, errorCode);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        return new EvalChatResponse(
                r.answer(),
                behavior,
                latencyMs,
                capabilitiesEffective(request),
                meta,
                List.of(),
                List.of(),
                errorCode,
                toolBlock,
                null);
    }

    private boolean isRealToolEvalRequest(EvalChatRequest request) {
        if (!isExpectedBehaviorTool(request)) {
            return false;
        }
        if (!"real".equals(normalizedToolPolicy(request.getToolPolicy()))) {
            return false;
        }
        if (!toolsEffectiveSupported(request)) {
            return false;
        }
        return mcpClientProvider.getIfAvailable() != null;
    }

    private EvalChatResponse respondRealToolEval(
            EvalChatRequest request,
            Map<String, Object> meta,
            String mode,
            HttpServletRequest httpRequest,
            long startNs,
            String q) {
        meta.put("retrieve_hit_count", 0);
        meta.put("low_confidence", false);
        meta.put("low_confidence_reasons", List.of());
        meta.put("low_confidence_gate", "none");
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        meta.put("eval_real_tools", true);
        meta.put("tool_policy", "real");

        String toolName =
                request.getToolStubId() != null ? request.getToolStubId().trim() : "";
        if (toolName.isEmpty()) {
            String msg = "tool_policy=real 时须在 JSON 中提供 tool_stub_id（用作 MCP 工具名）。";
            EvalBehaviorMetaSync.applyRootToMeta(meta, "clarify", null);
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            EvalChatResponse.Tool toolBlock = new EvalChatResponse.Tool(true, false, false, "", "skipped", 0L);
            return new EvalChatResponse(
                    msg,
                    "clarify",
                    latencyMs,
                    capabilitiesEffective(request),
                    meta,
                    List.of(),
                    List.of(),
                    null,
                    toolBlock,
                    null);
        }
        if (!mcpToolAllowed(toolName, mcpProperties != null ? mcpProperties.getAllowedTools() : null)) {
            String msg = "工具「" + toolName + "」不在 vagent.mcp.allowed-tools 白名单中。";
            EvalBehaviorMetaSync.applyRootToMeta(meta, "clarify", null);
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            EvalChatResponse.Tool toolBlock = new EvalChatResponse.Tool(true, false, false, toolName, "skipped", 0L);
            return new EvalChatResponse(
                    msg,
                    "clarify",
                    latencyMs,
                    capabilitiesEffective(request),
                    meta,
                    List.of(),
                    List.of(),
                    null,
                    toolBlock,
                    null);
        }

        McpClient client = mcpClientProvider.getIfAvailable();
        Map<String, Object> args = resolveRealToolArguments(request, toolName, q);
        Optional<List<String>> schemaViolations = mcpToolArgumentSchemaValidator.validate(toolName, args);
        if (schemaViolations.isPresent()) {
            List<String> viol = schemaViolations.get();
            meta.put("tool_error_code", "TOOL_SCHEMA_INVALID");
            meta.put("tool_schema_violations", viol);
            EvalBehaviorMetaSync.applyRootToMeta(meta, "tool", "TOOL_SCHEMA_INVALID");
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            EvalChatResponse.Tool toolBlock =
                    new EvalChatResponse.Tool(true, false, false, toolName, "error", 0L);
            return new EvalChatResponse(
                    "MCP 工具入参未通过 JSON Schema 校验。",
                    "tool",
                    latencyMs,
                    capabilitiesEffective(request),
                    meta,
                    List.of(),
                    List.of(),
                    "TOOL_SCHEMA_INVALID",
                    toolBlock,
                    null);
        }
        long t0 = System.nanoTime();
        String outcome = "success";
        boolean succeeded = true;
        Map<String, Object> result = Map.of();
        try {
            // 与 RagStreamChatService.tryCallToolForContext 一致：同步 callTool，超时由 HttpMcpClient 的
            // vagent.mcp.tool-call-timeout（tools/call）承担，避免与 eval 桩超时双轨叠加。
            result = client.callTool(toolName, args);
        } catch (Exception e) {
            succeeded = false;
            outcome = isLikelyTimeoutEval(e) ? "timeout" : "error";
        }
        long latencyMsTool = (System.nanoTime() - t0) / 1_000_000L;

        String behavior = "tool";
        String errorCode = null;
        if ("timeout".equalsIgnoreCase(outcome)) {
            errorCode = "TOOL_TIMEOUT";
        } else if (!succeeded) {
            errorCode = "TOOL_ERROR";
        }
        String answer = succeeded ? formatMcpToolResult(result) : "MCP 工具调用失败。";

        EvalChatResponse.Tool toolBlock =
                new EvalChatResponse.Tool(true, true, succeeded, toolName, outcome, latencyMsTool);

        EvalBehaviorMetaSync.applyRootToMeta(meta, behavior, errorCode);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        return new EvalChatResponse(
                answer,
                behavior,
                latencyMs,
                capabilitiesEffective(request),
                meta,
                List.of(),
                List.of(),
                errorCode,
                toolBlock,
                null);
    }

    private static Map<String, Object> resolveRealToolArguments(
            EvalChatRequest request, String toolName, String userQuery) {
        Map<String, Object> override = request.getMcpToolArguments();
        if (override != null && !override.isEmpty()) {
            return new LinkedHashMap<>(override);
        }
        return evalMcpToolArguments(toolName, userQuery);
    }

    private static Map<String, Object> evalMcpToolArguments(String toolName, String userQuery) {
        if ("ping".equalsIgnoreCase(toolName)) {
            return Map.of();
        }
        if ("echo".equalsIgnoreCase(toolName)) {
            return Map.of("message", userQuery != null ? userQuery : "");
        }
        return Map.of("query", userQuery != null ? userQuery : "");
    }

    private static String formatMcpToolResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        Object content = result.get("content");
        if (content != null) {
            return String.valueOf(content);
        }
        return result.toString();
    }

    private static boolean isLikelyTimeoutEval(Throwable e) {
        if (e == null) {
            return false;
        }
        String m = e.getMessage();
        if (m != null && m.toLowerCase(Locale.ROOT).contains("timeout")) {
            return true;
        }
        Throwable c = e.getCause();
        if (c != null && c != e) {
            return isLikelyTimeoutEval(c);
        }
        return false;
    }

    private static boolean mcpToolAllowed(String toolName, String csvAllowed) {
        if (toolName == null || toolName.isBlank() || csvAllowed == null || csvAllowed.isBlank()) {
            return false;
        }
        for (String p : csvAllowed.split(",")) {
            if (toolName.equals(p.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExpectedBehaviorTool(EvalChatRequest request) {
        String exp = request.getExpectedBehavior();
        return exp != null && !exp.isBlank() && "tool".equalsIgnoreCase(exp.trim());
    }

    private boolean isStubToolRequiredButStubDisabled(EvalChatRequest request) {
        if (!isExpectedBehaviorTool(request)) {
            return false;
        }
        return "stub".equals(normalizedToolPolicy(request.getToolPolicy())) && !evalApiProperties.isStubToolsEnabled();
    }

    private boolean isStubToolEvalRequest(EvalChatRequest request) {
        if (!evalApiProperties.isStubToolsEnabled()) {
            return false;
        }
        if (!isExpectedBehaviorTool(request)) {
            return false;
        }
        return "stub".equals(normalizedToolPolicy(request.getToolPolicy()));
    }

    private boolean isToolExpectedWithoutStubEvalExecution(EvalChatRequest request) {
        if (!isExpectedBehaviorTool(request)) {
            return false;
        }
        return !isStubToolEvalRequest(request) && !isRealToolEvalRequest(request);
    }

    private static String normalizedToolPolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return "disabled";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void applySafetyShortCircuitMeta(Map<String, Object> meta, EvalChatSafetyGate.Outcome outcome) {
        meta.put("retrieve_hit_count", 0);
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        meta.put("eval_safety_rule_id", outcome.ruleId());
        if ("deny".equals(outcome.behavior())) {
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
            meta.put("low_confidence_gate", "pre_retrieval_safety");
        } else {
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("SAFETY_QUERY_GATE"));
            meta.put("low_confidence_gate", "pre_retrieval_safety");
        }
    }

    /**
     * 前 N 条上限：请求头 {@code X-Eval-Membership-Top-N}（合法正整数）与 {@code vagent.eval.api.membership-top-n} 取有效值，
     * 再与引擎上限 {@value #RETRIEVAL_CANDIDATE_LIMIT_N} 取最小。
     */
    private int resolveMembershipCap(String headerValue) {
        int fallback =
                Math.max(1, Math.min(evalApiProperties.getMembershipTopN(), RETRIEVAL_CANDIDATE_LIMIT_N));
        if (headerValue == null || headerValue.isBlank()) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(headerValue.trim());
            if (v <= 0) {
                return fallback;
            }
            return Math.min(v, RETRIEVAL_CANDIDATE_LIMIT_N);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 明文命中 id 仅允许在「服务端打开 debug-enabled」「请求 mode=EVAL_DEBUG」「allow-cidrs 为空或客户端 IP 命中」时写入 meta；
     * 其他情况不向 meta 放入 {@code retrieval_hit_ids} 键，避免 eval 判 {@code SECURITY_BOUNDARY_VIOLATION}。
     */
    private void maybeAttachDebugRetrievalHitIds(
            Map<String, Object> meta, String mode, HttpServletRequest httpRequest, List<RetrieveHit> hits) {
        if (!evalApiProperties.isDebugEnabled() || !"EVAL_DEBUG".equals(mode)) {
            return;
        }
        if (!debugNetworkPolicy.allowsPlaintextRetrievalHitIds(httpRequest)) {
            return;
        }
        if (hits == null || hits.isEmpty()) {
            meta.put("retrieval_hit_ids", List.of());
            return;
        }
        List<String> ids =
                hits.stream()
                        .map(RetrievalMembershipHasher::canonicalHitId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
        meta.put("retrieval_hit_ids", ids);
    }

    /** 防止未来分支误写：只要不满足完整 debug 条件，强制移除明文命中 id 键。 */
    private void enforceRetrievalHitIdBoundary(Map<String, Object> meta, String mode, HttpServletRequest httpRequest) {
        if (!evalApiProperties.isDebugEnabled()
                || !"EVAL_DEBUG".equals(mode)
                || !debugNetworkPolicy.allowsPlaintextRetrievalHitIds(httpRequest)) {
            meta.remove("retrieval_hit_ids");
        }
    }

    private static List<String> canonicalIdsFromHits(List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(RetrievalMembershipHasher::canonicalHitId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    private EvalChatResponse.Capabilities capabilitiesEffective(EvalChatRequest request) {
        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        boolean toolsSupported = toolsEffectiveSupported(request);
        Boolean toolSub = toolsSupported ? Boolean.TRUE : null;
        return new EvalChatResponse.Capabilities(
                new EvalChatResponse.CapabilityFlag(retrievalSupported, false, null),
                new EvalChatResponse.CapabilityFlag(toolsSupported, toolSub, toolSub),
                new EvalChatResponse.StreamingFlag(false),
                EvalCapabilitiesObjects.guardrailsFromProperties(guardrailsProperties));
    }

    private boolean toolsEffectiveSupported(EvalChatRequest request) {
        String pol = normalizedToolPolicy(request != null ? request.getToolPolicy() : null);
        if ("stub".equals(pol)) {
            return evalApiProperties.isStubToolsEnabled();
        }
        if ("real".equals(pol)) {
            return mcpProperties != null
                    && mcpProperties.isEnabled()
                    && mcpProperties.getAllowedTools() != null
                    && !mcpProperties.getAllowedTools().isBlank();
        }
        return false;
    }

    private boolean shouldRunQuoteOnly(EvalChatRequest request, List<RetrieveHit> candidates, String behavior) {
        if (guardrailsProperties.getQuoteOnly() == null
                || !guardrailsProperties.getQuoteOnly().isEnabled()
                || !Boolean.TRUE.equals(request.getQuoteOnly())) {
            return false;
        }
        if (!"answer".equals(behavior) || candidates == null || candidates.isEmpty()) {
            return false;
        }
        return true;
    }

    private static Set<String> allowedHitIdsFromCandidates(List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        return candidates.stream()
                .map(RetrievalMembershipHasher::canonicalHitId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<EvalChatResponse.Source> hitsToSources(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .map(h -> new EvalChatResponse.Source(
                        RetrievalMembershipHasher.canonicalHitId(h),
                        canonicalTitle(h),
                        truncateSnippet(h != null ? h.getContent() : null)))
                .toList();
    }

    private static List<EvalChatResponse.RetrievalHit> hitsToRetrievalHits(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .map(h -> new EvalChatResponse.RetrievalHit(RetrievalMembershipHasher.canonicalHitId(h), h.getDistance()))
                .toList();
    }

    private static String canonicalTitle(RetrieveHit h) {
        // 当前 RetrieveHit 没有 title；Day2 用 document_id/source 做可读占位，Day3 再补齐真正 title
        if (h == null) {
            return "";
        }
        String doc = h.getDocumentId() != null ? h.getDocumentId().trim() : "";
        if (!doc.isEmpty()) {
            return doc;
        }
        String src = h.getSource() != null ? h.getSource().trim() : "";
        return !src.isEmpty() ? src : "kb";
    }

    private static String truncateSnippet(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() <= 300) {
            return s;
        }
        return s.substring(0, 300);
    }

    private record EvalFullAnswerResult(String answer, String errorCode) {}

    /**
     * 与主链路共用 {@link RagKnowledgeSystemPrompts} + {@link LlmClient}；失败或超时仍返回已聚合前缀（可为空）。
     */
    private EvalFullAnswerResult generateEvalFullAnswer(
            String userQuery, List<RetrieveHit> hitsForPrompt, Map<String, Object> meta) {
        meta.put("eval_full_answer", true);
        String system = RagKnowledgeSystemPrompts.buildFromHits(hitsForPrompt);
        List<LlmMessage> msgs =
                List.of(
                        new LlmMessage(LlmMessage.Role.SYSTEM, system),
                        new LlmMessage(LlmMessage.Role.USER, userQuery != null ? userQuery : ""));
        String model = llmProperties.getDefaultModel() != null ? llmProperties.getDefaultModel() : "";
        LlmChatRequest req = new LlmChatRequest(msgs, model);
        StringBuilder buf = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        long timeoutMs = evalApiProperties.getFullAnswerTimeoutMs();
        llmClient.streamChat(
                req,
                new LlmStreamSink() {
                    @Override
                    public void onChunk(String text) {
                        if (text != null) {
                            buf.append(text);
                        }
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable t) {
                        err.set(t);
                        latch.countDown();
                    }
                });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                meta.put("eval_full_answer_outcome", "timeout");
                return new EvalFullAnswerResult(buf.toString(), "TIMEOUT");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meta.put("eval_full_answer_outcome", "interrupted");
            return new EvalFullAnswerResult(buf.toString(), "TIMEOUT");
        }
        if (err.get() != null) {
            meta.put("eval_full_answer_outcome", "error");
            return new EvalFullAnswerResult(buf.toString(), "UPSTREAM_UNAVAILABLE");
        }
        meta.put("eval_full_answer_outcome", "ok");
        return new EvalFullAnswerResult(buf.toString(), null);
    }
}

