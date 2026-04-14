package com.vagent.eval;

import com.vagent.chat.rag.RagProperties;
import com.vagent.kb.KnowledgeRetrieveService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
import static com.vagent.eval.EvalChatContractTestSupport.assertTopLevelEvalChatContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-D4：主要 200 分支的 JSON 满足 {@link EvalChatContractTestSupport}（对齐 vagent-eval 契约顶层）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "vagent.eval.api.enabled=true",
            "vagent.eval.api.token-hash=9768cbb10efdc9a0cec74fbe4314cadd8885ba4993e19fa02e554902b2ce7533",
            "vagent.eval.api.membership-top-n=8",
            "vagent.rag.enabled=true",
            "vagent.guardrails.reflection.enabled=false"
        })
class EvalChatControllerContractMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void authFailureStillMatchesContract() throws Exception {
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", "wrong-token")
                                        .content("{\"query\":\"hello world\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("deny"))
                        .andExpect(jsonPath("$.error_code").value("AUTH"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }

    @Test
    void emptyHitsMatchesContract() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(List.of());

        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                        .content("{\"query\":\"long enough query\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("clarify"))
                        .andExpect(jsonPath("$.error_code").value("RETRIEVE_EMPTY"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }

    @Test
    void queryTooShortMatchesContract() throws Exception {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("c1");
        h.setDocumentId("d1");
        h.setContent("x");
        h.setDistance(0.2);
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(List.of(h));

        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                        .content("{\"query\":\"ab\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("clarify"))
                        .andExpect(jsonPath("$.error_code").value("RETRIEVE_LOW_CONFIDENCE"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }

    @Test
    void happyPathMatchesContract() throws Exception {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("c1");
        h.setDocumentId("d1");
        h.setContent("snippet text");
        h.setDistance(0.1);
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(List.of(h));

        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                        .content("{\"query\":\"hello world\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("answer"))
                        .andExpect(jsonPath("$.retrieval_hits.length()").value(1))
                        .andExpect(jsonPath("$.retrieval_hits[0].id").value("c1"))
                        .andExpect(jsonPath("$.sources[0].id").value("c1"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }
}
