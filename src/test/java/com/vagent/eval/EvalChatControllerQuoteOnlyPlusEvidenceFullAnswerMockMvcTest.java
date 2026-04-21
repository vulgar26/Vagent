package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.RagRetrieveResult;
import com.vagent.kb.dto.RetrieveHit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * full-answer 路径下 {@code scope=digits_plus_tokens_plus_evidence}：数字须在 corpus，且能落到 evidence_map /
 * snippet 绑定（与 {@link EvalQuoteOnlyGuard} 单测互补的 HTTP 回归）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "vagent.eval.api.enabled=true",
            "vagent.eval.api.token-hash=9768cbb10efdc9a0cec74fbe4314cadd8885ba4993e19fa02e554902b2ce7533",
            "vagent.eval.api.membership-top-n=8",
            "vagent.eval.api.full-answer-enabled=true",
            "vagent.eval.api.safety-rules-enabled=false",
            "vagent.llm.provider=fake-stream",
            "vagent.llm.fake-stream-chunk-delay-ms=0",
            "vagent.rag.enabled=true",
            "vagent.guardrails.reflection.enabled=false",
            "vagent.guardrails.quote-only.enabled=true",
            "vagent.guardrails.quote-only.strictness=relaxed",
            "vagent.guardrails.quote-only.scope=digits_plus_tokens_plus_evidence"
        })
class EvalChatControllerQuoteOnlyPlusEvidenceFullAnswerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void fullAnswer_quoteOnlyPlusEvidence_deniesWhenSnippetTruncationHidesDigits() throws Exception {
        // corpus 用全文含 333；sources.snippet 截断 ≤300 后不含 333 → evidence 无法绑定
        String longPrefix = "a".repeat(300);
        String content = longPrefix + " 333";
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHit("chunk-trunc", content)));

        String q = "eval-full-qo-plus-ev-fail 333";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":false,"quote_only":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("deny"))
                .andExpect(jsonPath("$.error_code").value("GUARDRAIL_TRIGGERED"))
                .andExpect(jsonPath("$.meta.quote_only_scope").value("digits_plus_tokens_plus_evidence"))
                .andExpect(jsonPath("$.meta.guardrail_triggered").value(true))
                .andExpect(jsonPath("$.meta.reflection_reasons[0]").value("QUOTE_ONLY_EVIDENCE_UNBOUND"));
    }

    @Test
    void fullAnswer_quoteOnlyPlusEvidence_passesWhenNumericBoundInSnippet() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(
                        RagRetrieveResult.vectorOnly(
                                singleHit("chunk-pe-pass", "含税价 1200 元 详见附件说明")));

        String q = "eval-full-qo-plus-ev-pass 推荐含税价 1200 元";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":false,"quote_only":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.answer").value(q))
                .andExpect(jsonPath("$.meta.quote_only_passed").value(true))
                .andExpect(jsonPath("$.meta.quote_only_scope").value("digits_plus_tokens_plus_evidence"));
    }

    private static List<RetrieveHit> singleHit(String chunkId, String content) {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId(chunkId);
        h.setDocumentId("doc-" + chunkId);
        h.setContent(content);
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }
}
