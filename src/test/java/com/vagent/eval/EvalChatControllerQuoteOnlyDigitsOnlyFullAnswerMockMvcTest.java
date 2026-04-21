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
 * full-answer 路径下 {@code scope=digits_only}：moderate 档位不卡长 token，仅卡数字串是否在 corpus。
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
            "vagent.guardrails.quote-only.strictness=moderate",
            "vagent.guardrails.quote-only.scope=digits_only"
        })
class EvalChatControllerQuoteOnlyDigitsOnlyFullAnswerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void fullAnswer_quoteOnlyDigitsOnly_passesWhenLongTokenNotInCorpusButDigitsGrounded() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHitBaseonlyAnd999()));

        String q =
                "eval-full-qo-digits-only SUPERMODELX999WORD 999"; // ≥8 字母数字 token 不在 corpus；999 在 corpus
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
                .andExpect(jsonPath("$.meta.quote_only").value(true))
                .andExpect(jsonPath("$.meta.quote_only_scope").value("digits_only"))
                .andExpect(jsonPath("$.meta.quote_only_strictness").value("moderate"))
                .andExpect(jsonPath("$.meta.quote_only_passed").value(true))
                .andExpect(jsonPath("$.meta.guardrail_triggered").value(false))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scope").value("digits_only"))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported[2]")
                        .value("digits_plus_tokens_plus_evidence"));
    }

    private static List<RetrieveHit> singleHitBaseonlyAnd999() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-digits-only");
        h.setDocumentId("doc-digits-only");
        h.setContent("型号 BASEONLY 官方说明 编号 999");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }
}
