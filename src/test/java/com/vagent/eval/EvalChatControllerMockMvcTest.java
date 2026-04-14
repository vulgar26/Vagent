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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
import static com.vagent.eval.EvalChatContractTestSupport.assertTopLevelEvalChatContract;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-D3：{@code X-Eval-Membership-Top-N} 与根级 {@code retrieval_hits} / {@code sources} 同口径。
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
class EvalChatControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void membershipTopNHeaderLimitsSourcesAndRetrievalHits() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(fiveChunkHits());

        String body =
                """
                {"query":"hello world test","mode":"EVAL","requires_citations":false}
                """;

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .header("X-Eval-Membership-Top-N", "2")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.retrieval_hits", hasSize(2)))
                .andExpect(jsonPath("$.sources[0].id").value("chunk-0"))
                .andExpect(jsonPath("$.retrieval_hits[0].id").value("chunk-0"))
                .andExpect(jsonPath("$.retrieval_hits[0].score").value(0.0))
                .andExpect(jsonPath("$.meta.retrieval_candidate_limit_n").value(2))
                .andExpect(jsonPath("$.meta.x_eval_membership_top_n").value("2"))
                .andDo(
                        r ->
                                assertTopLevelEvalChatContract(
                                        r.getResponse().getContentAsString()));
    }

    @Test
    void defaultMembershipTopNWhenHeaderAbsent() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(fiveChunkHits());

        String body =
                """
                {"query":"hello world test","mode":"EVAL","requires_citations":false}
                """;

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(5)))
                .andExpect(jsonPath("$.retrieval_hits", hasSize(5)))
                .andExpect(jsonPath("$.meta.retrieval_candidate_limit_n").value(5))
                .andDo(
                        r ->
                                assertTopLevelEvalChatContract(
                                        r.getResponse().getContentAsString()));
    }

    private static List<RetrieveHit> fiveChunkHits() {
        List<RetrieveHit> hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RetrieveHit h = new RetrieveHit();
            h.setChunkId("chunk-" + i);
            h.setDocumentId("doc-" + i);
            h.setContent("body " + i);
            h.setDistance(0.1 * i);
            hits.add(h);
        }
        return hits;
    }
}
