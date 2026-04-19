package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.eval.dto.EvalChatRequest;
import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.RagRetrieveResult;
import com.vagent.kb.dto.RetrieveHit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
import com.vagent.mcp.config.McpProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 * <p>Quote-only：{@code vagent.guardrails.quote-only.enabled} 且请求 {@code quote_only=true} 时，由 {@link EvalQuoteOnlyGuard} 做子串核对；
 * 档位见 {@code plans/quote-only-guardrails.md}。</p>
 *
 * <p>P0+：读取 {@code X-Eval-Membership-Top-N}（缺省用 {@code vagent.eval.api.membership-top-n}），使 {@code sources} 与根级
 * {@code retrieval_hits} 同前 N 条候选，与 vagent-eval {@code verifyCitationMembership} 的 top_n 对齐（§16.4）。</p>
 * <p>可选：检索后低置信门控阈值与子串见 {@code vagent.rag.gate.low-confidence-*}（主 SSOT）；未配置时回退
 * {@code vagent.eval.api.low-confidence-*}（已弃用别名），与 P0 {@code rag/low_conf} 对齐（默认关闭）。</p>
 * <p>P0+ B 线：{@code vagent.eval.api.safety-rules-enabled} 为 true 时，检索前经 {@link EvalChatSafetyGate} 做拒答/澄清短路。</p>
 * <p>可选：{@code vagent.eval.api.full-answer-enabled=true} 时，在未门控短路且仍为 {@code answer} 路径下调用 {@link com.vagent.llm.LlmClient}
 * 生成正文（与主链路同款提示词）；默认 false 保持占位 {@code "OK"} 以控成本与 CI 稳定性。</p>
 */
/**
 * Bean 名显式指定，避免与包内其他 {@code EvalChatController}（若存在）默认名 {@code evalChatController} 冲突。
 */
@RestController("vagentEvalChatController")
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    /** Day5：hashed membership 候选集上限（强制 N≤50）。 */
    private static final int RETRIEVAL_CANDIDATE_LIMIT_N = 50;

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
            McpProperties mcpProperties) {
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
                buildRetrievalHitIdHashes(xEvalToken, xEvalTargetId, xEvalDatasetId, xEvalCaseId, candidates));

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
                        null);
        if (gate.isPresent()) {
            RagPostRetrieveGate.ShortCircuit sc = gate.get();
            answer = sc.answer();
            behavior = sc.behavior();
            errorCode = sc.errorCode();
            lowConfidence = sc.lowConfidence();
            meta.put("low_confidence", sc.lowConfidence());
            meta.put("low_confidence_reasons", sc.lowConfidenceReasons());
        } else {
            lowConfidence = false;
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
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

        if (shouldRunQuoteOnly(request, candidates, behavior)) {
            String qs =
                    guardrailsProperties.getQuoteOnly().getStrictness() != null
                            ? guardrailsProperties.getQuoteOnly().getStrictness().trim().toLowerCase(Locale.ROOT)
                            : "moderate";
            meta.put("quote_only", true);
            meta.put("quote_only_strictness", qs);
            if (!meta.containsKey("guardrail_triggered")) {
                meta.put("guardrail_triggered", false);
            }
            Optional<EvalReflectionOneShotGuard.Patch> quotePatch =
                    EvalQuoteOnlyGuard.evaluate(
                            EvalQuoteOnlyGuard.Strictness.fromConfig(guardrailsProperties.getQuoteOnly().getStrictness()),
                            answer,
                            corpusFromCandidates(candidates));
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
                errorCode);
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
                toolBlock);
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
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");
        meta.put("retrieval_candidate_limit_n", 0);
        meta.put("retrieval_candidate_total", 0);
        meta.put("retrieval_hit_id_hashes", List.of());
        String pol = normalizedToolPolicy(request.getToolPolicy());
        meta.put("tool_policy", pol);
        meta.put("tool_eval_non_stub", true);
        String msg =
                "real".equals(pol)
                        ? "本题为工具题（expected_behavior=tool），但 tool_policy=real：评测接口仅保证 stub 桩路径可执行；请改用 stub 或在评测侧跳过。"
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
                toolBlock);
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
                toolBlock);
    }

    private boolean isStubToolRequiredButStubDisabled(EvalChatRequest request) {
        String exp = request.getExpectedBehavior();
        if (exp == null || exp.isBlank() || !"tool".equalsIgnoreCase(exp.trim())) {
            return false;
        }
        return "stub".equals(normalizedToolPolicy(request.getToolPolicy())) && !evalApiProperties.isStubToolsEnabled();
    }

    private boolean isStubToolEvalRequest(EvalChatRequest request) {
        if (!evalApiProperties.isStubToolsEnabled()) {
            return false;
        }
        String exp = request.getExpectedBehavior();
        if (exp == null || exp.isBlank() || !"tool".equalsIgnoreCase(exp.trim())) {
            return false;
        }
        return "stub".equals(normalizedToolPolicy(request.getToolPolicy()));
    }

    private boolean isToolExpectedWithoutStubEvalExecution(EvalChatRequest request) {
        String exp = request.getExpectedBehavior();
        if (exp == null || exp.isBlank() || !"tool".equalsIgnoreCase(exp.trim())) {
            return false;
        }
        return !isStubToolEvalRequest(request);
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
        } else {
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("SAFETY_QUERY_GATE"));
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
                        .map(EvalChatController::canonicalHitId)
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

    private static List<String> buildRetrievalHitIdHashes(
            String xEvalToken,
            String xEvalTargetId,
            String xEvalDatasetId,
            String xEvalCaseId,
            List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String token = xEvalToken != null ? xEvalToken.trim() : "";
        if (token.isEmpty()) {
            return List.of();
        }
        String targetId = xEvalTargetId != null ? xEvalTargetId.trim() : "";
        String datasetId = xEvalDatasetId != null ? xEvalDatasetId.trim() : "";
        String caseId = xEvalCaseId != null ? xEvalCaseId.trim() : "";

        byte[] kCase = hmacSha256(token.getBytes(StandardCharsets.UTF_8),
                ("hitid-key/v1|" + targetId + "|" + datasetId + "|" + caseId).getBytes(StandardCharsets.UTF_8));

        return candidates.stream()
                .map(EvalChatController::canonicalHitId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> toHexLower(hmacSha256(kCase, id.getBytes(StandardCharsets.UTF_8))))
                .toList();
    }

    private static byte[] hmacSha256(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        char[] hex = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }

    private EvalChatResponse.Capabilities capabilitiesEffective(EvalChatRequest request) {
        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        boolean reflectionOn =
                guardrailsProperties != null && guardrailsProperties.getReflection().isEnabled();
        boolean quoteOnlyOn =
                guardrailsProperties != null && guardrailsProperties.getQuoteOnly().isEnabled();
        boolean toolsSupported = toolsEffectiveSupported(request);
        Boolean toolSub = toolsSupported ? Boolean.TRUE : null;
        return new EvalChatResponse.Capabilities(
                new EvalChatResponse.CapabilityFlag(retrievalSupported, false, null),
                new EvalChatResponse.CapabilityFlag(toolsSupported, toolSub, toolSub),
                new EvalChatResponse.StreamingFlag(false),
                new EvalChatResponse.GuardrailsFlag(quoteOnlyOn, false, reflectionOn)
        );
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

    private static List<String> corpusFromCandidates(List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(RetrieveHit::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();
    }

    private static Set<String> allowedHitIdsFromCandidates(List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        return candidates.stream()
                .map(EvalChatController::canonicalHitId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<EvalChatResponse.Source> hitsToSources(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .map(h -> new EvalChatResponse.Source(
                        canonicalHitId(h),
                        canonicalTitle(h),
                        truncateSnippet(h != null ? h.getContent() : null)))
                .toList();
    }

    private static List<EvalChatResponse.RetrievalHit> hitsToRetrievalHits(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .map(h -> new EvalChatResponse.RetrievalHit(canonicalHitId(h), h.getDistance()))
                .toList();
    }

    private static String canonicalHitId(RetrieveHit h) {
        if (h == null) {
            return "";
        }
        String cid = h.getChunkId() != null ? h.getChunkId().trim() : "";
        if (!cid.isEmpty()) {
            return cid;
        }
        String doc = h.getDocumentId() != null ? h.getDocumentId().trim() : "";
        return !doc.isEmpty() ? doc : "";
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

