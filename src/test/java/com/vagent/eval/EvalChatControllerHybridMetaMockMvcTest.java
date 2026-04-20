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

/**
 * P1-0b：评测 {@code meta} 与 {@link RagRetrieveResult#putRetrievalTrace} 对齐（hybrid / rerank 归因字段），
 * 便于同一 {@code dataset_id} 下 A/B compare 读两侧 {@code meta}（见 {@code scripts/README-hybrid-rerank-ab.md}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "vagent.eval.api.enabled=true",
            "vagent.eval.api.token-hash=9768cbb10efdc9a0cec74fbe4314cadd8885ba4993e19fa02e554902b2ce7533",
            "vagent.eval.api.safety-rules-enabled=false",
            "vagent.guardrails.reflection.enabled=false",
            "vagent.rag.enabled=true"
        })
class EvalChatControllerHybridMetaMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void evalMetaReflectsHybridAndRerankTraceFromRetrieveResult() throws Exception {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("c-hybrid-1");
        h.setDocumentId("d1");
        h.setContent("hybrid trace contract");
        h.setDistance(0.11);

        RagRetrieveResult trace =
                new RagRetrieveResult(
                        List.of(h),
                        true,
                        "ok",
                        "ilike",
                        4,
                        2,
                        3,
                        0.375,
                        true,
                        "skipped",
                        5L);

        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(trace);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content("{\"query\":\"hello hybrid meta\",\"mode\":\"EVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.meta.hybrid_enabled").value(true))
                .andExpect(jsonPath("$.meta.hybrid_lexical_outcome").value("ok"))
                .andExpect(jsonPath("$.meta.hybrid_lexical_mode").value("ilike"))
                .andExpect(jsonPath("$.meta.hybrid_primary_chunk_id_count").value(4))
                .andExpect(jsonPath("$.meta.hybrid_lexical_chunk_id_count").value(2))
                .andExpect(jsonPath("$.meta.hybrid_fused_chunk_id_count").value(3))
                .andExpect(jsonPath("$.meta.hybrid_chunk_id_delta_rate").value(0.375))
                .andExpect(jsonPath("$.meta.rerank_enabled").value(true))
                .andExpect(jsonPath("$.meta.rerank_outcome").value("skipped"))
                .andExpect(jsonPath("$.meta.rerank_latency_ms").value(5));
    }
}
