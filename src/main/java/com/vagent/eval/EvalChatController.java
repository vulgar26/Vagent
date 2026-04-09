package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.eval.dto.EvalChatRequest;
import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.dto.RetrieveHit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * P0：评测专用对话接口（非流式 + snake_case）。
 *
 * <p>Day1：请求/响应形态、读取 X-Eval-*、enabled + token-hash 校验。</p>
 * <p>Day2：sources[] 服务端生成、snippet 规则截断（≤300）。</p>
 * <p>Day3：空命中/低置信门控（retrieve_hit_count、low_confidence、low_confidence_reasons、error_code）。</p>
 * <p>Day4：EVAL_DEBUG + vagent.eval.api.debug-enabled 时可在 meta 输出明文 {@code retrieval_hit_ids[]}；否则绝不输出。</p>
 */
@RestController
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    /** 与 vagent-upgrade P0 默认 minQueryChars 对齐：过短则低置信（相对特征）。 */
    private static final int MIN_QUERY_CHARS = 3;

    private final EvalApiProperties evalApiProperties;
    private final RagProperties ragProperties;
    private final KnowledgeRetrieveService knowledgeRetrieveService;
    private final EvalTokenVerifier tokenVerifier;

    public EvalChatController(
            EvalApiProperties evalApiProperties,
            RagProperties ragProperties,
            KnowledgeRetrieveService knowledgeRetrieveService) {
        this.evalApiProperties = evalApiProperties;
        this.ragProperties = ragProperties;
        this.knowledgeRetrieveService = knowledgeRetrieveService;
        this.tokenVerifier = new EvalTokenVerifier(evalApiProperties);
    }

    @PostMapping("/chat")
    public EvalChatResponse chat(
            @RequestHeader(value = "X-Eval-Token", required = false) String xEvalToken,
            @RequestHeader(value = "X-Eval-Run-Id", required = false) String xEvalRunId,
            @RequestHeader(value = "X-Eval-Dataset-Id", required = false) String xEvalDatasetId,
            @RequestHeader(value = "X-Eval-Case-Id", required = false) String xEvalCaseId,
            @RequestHeader(value = "X-Eval-Target-Id", required = false) String xEvalTargetId,
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

        if (!tokenVerifier.verifyOrFalse(xEvalToken)) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(),
                    meta,
                    List.of(),
                    "AUTH");
        }

        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        List<RetrieveHit> hits = List.of();
        List<EvalChatResponse.Source> sources = List.of();

        if (!retrievalSupported || knowledgeRetrieveService == null) {
            meta.put("retrieve_hit_count", 0);
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
            meta.put("disabled_reason", "RETRIEVAL_DISABLED");
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new EvalChatResponse(
                    "检索能力未启用。",
                    "deny",
                    latencyMs,
                    capabilitiesEffective(),
                    meta,
                    sources,
                    "POLICY_DISABLED");
        }

        UUID evalUserId = stableEvalUserId(xEvalTargetId);
        hits = knowledgeRetrieveService.searchForRag(evalUserId, request.getQuery(), ragProperties);
        sources = hitsToSources(hits);
        int hitCount = hits.size();
        meta.put("retrieve_hit_count", hitCount);
        meta.put("canonical_hit_id_scheme", "kb_chunk_id");

        String q = request.getQuery() == null ? "" : request.getQuery().trim();

        String answer = "OK";
        String behavior = "answer";
        String errorCode = null;

        if (hitCount == 0) {
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("EMPTY_HITS"));
            answer = "知识库未检索到相关片段，请尝试补充关键词或更具体的问题描述。";
            behavior = "clarify";
            errorCode = "RETRIEVE_EMPTY";
        } else if (q.length() < MIN_QUERY_CHARS) {
            meta.put("low_confidence", true);
            meta.put("low_confidence_reasons", List.of("QUERY_TOO_SHORT"));
            answer = "你的问题描述过短，请补充更多上下文或关键词。";
            behavior = "clarify";
            errorCode = "RETRIEVE_LOW_CONFIDENCE";
        } else {
            meta.put("low_confidence", false);
            meta.put("low_confidence_reasons", List.of());
        }

        maybeAttachDebugRetrievalHitIds(meta, mode, hits);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        return new EvalChatResponse(
                answer,
                behavior,
                latencyMs,
                capabilitiesEffective(),
                meta,
                sources,
                errorCode);
    }

    /**
     * 明文命中 id 仅允许在「服务端打开 debug-enabled」且「请求 mode=EVAL_DEBUG」时写入 meta；
     * 其他情况不向 meta 放入 {@code retrieval_hit_ids} 键，避免 eval 判 {@code SECURITY_BOUNDARY_VIOLATION}。
     */
    private void maybeAttachDebugRetrievalHitIds(Map<String, Object> meta, String mode, List<RetrieveHit> hits) {
        if (!evalApiProperties.isDebugEnabled() || !"EVAL_DEBUG".equals(mode)) {
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

    private EvalChatResponse.Capabilities capabilitiesEffective() {
        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        return new EvalChatResponse.Capabilities(
                new EvalChatResponse.CapabilityFlag(retrievalSupported, false, null),
                // supported=false 时，子能力字段不适用，置为 null
                new EvalChatResponse.CapabilityFlag(false, null, null),
                new EvalChatResponse.StreamingFlag(false),
                new EvalChatResponse.GuardrailsFlag(false, false, false)
        );
    }

    private static UUID stableEvalUserId(String xEvalTargetId) {
        String t = xEvalTargetId != null ? xEvalTargetId.trim() : "";
        String seed = "eval-user|target=" + t;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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

