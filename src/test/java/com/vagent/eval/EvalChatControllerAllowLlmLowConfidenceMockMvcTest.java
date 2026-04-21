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
            "vagent.eval.api.token-hash=" + EvalChatContractTestSupport.TOKEN_HASH,
            "vagent.eval.api.membership-top-n=8",
            "vagent.rag.enabled=true",
            "vagent.rag.low-confidence-behavior=allow-llm",
            "vagent.rag.low-confidence-rule-set=query_too_short",
            "vagent.guardrails.reflection.enabled=false"
        })
class EvalChatControllerAllowLlmLowConfidenceMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void queryTooShort_allowsAnswerButMarksLowConfidence() throws Exception {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("c1");
        h.setDocumentId("d1");
        h.setContent("snippet text");
        h.setDistance(0.1);
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(List.of(h)));

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content("{\"query\":\"ab\",\"mode\":\"EVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.meta.low_confidence").value(true))
                .andExpect(jsonPath("$.meta.low_confidence_gate").value("post_retrieve_allow_llm"))
                .andExpect(jsonPath("$.meta.low_confidence_reasons[0]").value("QUERY_TOO_SHORT"));
    }
}

