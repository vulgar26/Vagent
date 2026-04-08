package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.eval.dto.EvalChatRequest;
import com.vagent.eval.dto.EvalChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * P0：评测专用对话接口（非流式 + snake_case）。
 *
 * <p>Day1 骨架：仅完成请求/响应形态、读取 X-Eval-*、enabled + token-hash 校验。</p>
 */
@RestController
@RequestMapping("/api/v1/eval")
public class EvalChatController {

    private final EvalApiProperties evalApiProperties;
    private final RagProperties ragProperties;
    private final EvalTokenVerifier tokenVerifier;

    public EvalChatController(EvalApiProperties evalApiProperties, RagProperties ragProperties) {
        this.evalApiProperties = evalApiProperties;
        this.ragProperties = ragProperties;
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
                    "AUTH");
        }

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        // Day1：骨架实现，不接入真实编排；Day2 起接 RAG/门控/sources/引用闭环
        return new EvalChatResponse(
                "OK (skeleton): " + request.getQuery(),
                "answer",
                latencyMs,
                capabilitiesEffective(),
                meta,
                null);
    }

    private EvalChatResponse.Capabilities capabilitiesEffective() {
        boolean retrievalSupported = ragProperties != null && ragProperties.isEnabled();
        return new EvalChatResponse.Capabilities(
                new EvalChatResponse.CapabilityFlag(retrievalSupported, false, null),
                new EvalChatResponse.CapabilityFlag(false, null, true),
                new EvalChatResponse.StreamingFlag(false),
                new EvalChatResponse.GuardrailsFlag(false, false, false)
        );
    }
}

