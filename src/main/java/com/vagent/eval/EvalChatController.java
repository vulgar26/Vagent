package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.eval.dto.EvalChatRequest;
import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.kb.KnowledgeRetrieveService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vagent.guardrails.GuardrailsProperties;

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
 *
 * <p>P0+：读取 {@code X-Eval-Membership-Top-N}（缺省用 {@code vagent.eval.api.membership-top-n}），使 {@code sources} 与根级
 * {@code retrieval_hits} 同前 N 条候选，与 vagent-eval {@code verifyCitationMembership} 的 top_n 对齐（§16.4）。</p>
 * <p>可选：{@code vagent.eval.api.low-confidence-cosine-distance-threshold} 与 {@code low-confidence-query-substrings}
 * 在命中非空时收紧为 {@code clarify}，与 P0 {@code rag/low_conf} 对齐（默认关闭）。</p>
 * <p>P0+ B 线：{@code vagent.eval.api.safety-rules-enabled} 为 true 时，检索前经 {@link EvalChatSafetyGate} 做拒答/澄清短路。</p>
 */
/**
 * Bean 名显式指定，避免与包内其他 {@code EvalChatController}（若存在）默认名 {@code evalChatController} 冲突。
 */
@RestController("vagentEvalChatController")
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    /** 与 vagent-upgrade P0 默认 minQueryChars 对齐：过短则低置信（相对特征）。 */
    private static final int MIN_QUERY_CHARS = 3;

    /** Day5：hashed membership 候选集上限（强制 N≤50）。 */
    private static final int RETRIEVAL_CANDIDATE_LIMIT_N = 50;

    private final EvalApiProperties evalApiProperties;
    private final EvalDebugNetworkPolicy debugNetworkPolicy;
    private final GuardrailsProperties guardrailsProperties;
    private final RagProperties ragProperties;
    private final KnowledgeRetrieveService knowledgeRetrieveService;
    private final EvalTokenVerifier tokenVerifier;

    public EvalChatController(
            EvalApiProperties evalApiProperties,
            EvalDebugNetworkPolicy debugNetworkPolicy,
            GuardrailsProperties guardrailsProperties,
            RagProperties ragProperties,
            KnowledgeRetrieveService knowledgeRetrieveService) {
        this.evalApiProperties = evalApiProperties;
        this.debugNetworkPolicy = debugNetworkPolicy;
        this.guardrailsProperties = guardrailsProperties;
        this.ragProperties = ragProperties;
        this.knowledgeRetrieveService = knowledgeRetrieveService;
        this.tokenVerifier = new EvalTokenVerifier(evalApiProperties);
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
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(),
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
                enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
                long latencyMsEarly = (System.nanoTime() - startNs) / 1_000_000L;
                EvalChatSafetyGate.Outcome o = safety.get();
                return new EvalChatResponse(
                        o.answer(),
                        o.behavior(),
                        latencyMsEarly,
                        capabilitiesEffective(),
                        meta,
                        List.of(),
                        List.of(),
                        o.errorCode());
            }
        }

        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        List<RetrieveHit> hits = List.of();
        List<EvalChatResponse.Source> sources = List.of();

        if (!retrievalSupported || knowledgeRetrieveService == null) {
            meta.put("retrieve_hit_count", 0);
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
            meta.put("disabled_reason", "RETRIEVAL_DISABLED");
            enforceRetrievalHitIdBoundary(meta, mode, httpRequest);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "检索能力未启用。",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(),
                    meta,
                    sources,
                    List.of(),
                    "POLICY_DISABLED");
        }

        UUID evalUserId = EvalStableUserId.fromEvalTargetId(xEvalTargetId);
        hits = knowledgeRetrieveService.searchForRag(evalUserId, request.getQuery(), ragProperties);
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

        boolean distanceLowConfidence = isDistanceLowConfidence(hits);
        boolean vagueSubstringLowConfidence = isVagueQuerySubstringLowConfidence(q);

        String answer = "OK";
        String behavior = "answer";
        String errorCode = null;
        boolean lowConfidence;

        if (hitCount == 0) {
            lowConfidence = true;
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("EMPTY_HITS"));
            answer = "知识库未检索到相关片段，请尝试补充关键词或更具体的问题描述。";
            behavior = "clarify";
            errorCode = "RETRIEVE_EMPTY";
        } else if (q.length() < MIN_QUERY_CHARS) {
            lowConfidence = true;
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("QUERY_TOO_SHORT"));
            answer = "你的问题描述过短，请补充更多上下文或关键词。";
            behavior = "clarify";
            errorCode = "RETRIEVE_LOW_CONFIDENCE";
        } else if (distanceLowConfidence || vagueSubstringLowConfidence) {
            lowConfidence = true;
            meta.put("low_confidence", true);
            ArrayList<String> reasons = new ArrayList<>(2);
            if (distanceLowConfidence) {
                reasons.add("WEAK_TOP_HIT_DISTANCE");
            }
            if (vagueSubstringLowConfidence) {
                reasons.add("VAGUE_QUERY_REFERENCE");
            }
            meta.put("low_confidence_reasons", List.copyOf(reasons));
            answer = "当前检索结果置信度不足，或问题指代不够明确；请补充具体对象、范围或关键词后再试。";
            behavior = "clarify";
            errorCode = "RETRIEVE_LOW_CONFIDENCE";
        } else {
            lowConfidence = false;
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
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

        // Day4：debug 模式可返回明文 hit ids（同 Day5 “候选集前 N” 口径）；Day6：受 allow-cidrs 约束
        maybeAttachDebugRetrievalHitIds(meta, mode, httpRequest, candidates);
        enforceRetrievalHitIdBoundary(meta, mode, httpRequest);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        return new EvalChatResponse(
                answer,
                behavior,
                latencyMs,
                capabilitiesEffective(),
                meta,
                sources,
                retrievalHits,
                errorCode);
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

    private EvalChatResponse.Capabilities capabilitiesEffective() {
        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        boolean reflectionOn =
                guardrailsProperties != null && guardrailsProperties.getReflection().isEnabled();
        return new EvalChatResponse.Capabilities(
                new EvalChatResponse.CapabilityFlag(retrievalSupported, false, null),
                // supported=false 时，子能力字段不适用，置为 null
                new EvalChatResponse.CapabilityFlag(false, null, null),
                new EvalChatResponse.StreamingFlag(false),
                new EvalChatResponse.GuardrailsFlag(false, false, reflectionOn)
        );
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

    /**
     * 首条命中余弦距离弱于阈值则低置信（{@link RetrieveHit} 注释：距离越小越相似）。
     */
    private boolean isDistanceLowConfidence(List<RetrieveHit> orderedHits) {
        Double th = evalApiProperties.getLowConfidenceCosineDistanceThreshold();
        if (th == null || orderedHits == null || orderedHits.isEmpty()) {
            return false;
        }
        double d = orderedHits.get(0).getDistance();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        return d > th;
    }

    private boolean isVagueQuerySubstringLowConfidence(String query) {
        List<String> subs = evalApiProperties.getLowConfidenceQuerySubstrings();
        if (subs == null || subs.isEmpty() || query == null) {
            return false;
        }
        for (String s : subs) {
            if (s != null && !s.isBlank() && query.contains(s.strip())) {
                return true;
            }
        }
        return false;
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
}

