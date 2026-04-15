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
import static com.vagent.eval.EvalChatContractTestSupport.assertTopLevelEvalChatContract;
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
            "vagent.eval.api.low-confidence-query-substrings[0]=这个东西",
            "vagent.eval.api.safety-rules-enabled=false",
            "vagent.rag.enabled=true",
            "vagent.guardrails.reflection.enabled=false"
        })
class EvalChatControllerVagueSubstringLowConfidenceMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void vagueSubstringTriggersClarifyEvenWhenDistanceGood() throws Exception {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("c1");
        h.setDocumentId("d1");
        h.setContent("body");
        h.setDistance(0.05);
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(List.of(h)));

        var r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                        .content(
                                                "{\"query\":\"帮我看一下这个东西怎么弄？\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("clarify"))
                        .andExpect(jsonPath("$.error_code").value("RETRIEVE_LOW_CONFIDENCE"))
                        .andExpect(jsonPath("$.meta.low_confidence").value(true))
                        .andExpect(jsonPath("$.meta.low_confidence_reasons[0]").value("VAGUE_QUERY_REFERENCE"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }
}
