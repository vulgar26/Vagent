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
            "vagent.guardrails.quote-only.strictness=moderate"
        })
class EvalChatControllerQuoteOnlyMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void quoteOnlyDeniesWhenAnswerContainsUngroundedLongDigitRun() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHit("官方说明：价格以门店公示为准。")));

        String q = "price is 999888777 dollars";
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
                .andExpect(jsonPath("$.meta.quote_only").value(true))
                .andExpect(jsonPath("$.meta.guardrail_triggered").value(true))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only").value(true))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scope").value("digits_plus_tokens"))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported").isArray())
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported.length()").value(3))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported[0]").value("digits_only"))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported[1]").value("digits_plus_tokens"))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only_scopes_supported[2]")
                        .value("digits_plus_tokens_plus_evidence"));
    }

    @Test
    void quoteOnlyPassesWhenEchoMatchesCorpus() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHit("echo official line for SKU")));

        String q = "echo official";
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
                .andExpect(jsonPath("$.meta.quote_only_passed").value(true));
    }

    private static List<RetrieveHit> singleHit(String content) {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-quote-only");
        h.setDocumentId("doc-1");
        h.setContent(content);
        List<RetrieveHit> list = new ArrayList<>();
        list.add(h);
        return list;
    }
}
