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
 * {@code vagent.eval.api.full-answer-enabled=true} 时，eval/chat 在未短路路径下聚合 LLM 输出为 {@code answer}。
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
            "vagent.guardrails.reflection.enabled=false"
        })
class EvalChatControllerFullAnswerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void fullAnswer_echoesLastUserMessageViaFakeStream() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHit()));

        String q = "hello-full-answer 42";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(q))
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.meta.eval_full_answer").value(true))
                .andExpect(jsonPath("$.meta.eval_full_answer_outcome").value("ok"))
                .andExpect(jsonPath("$.capabilities.guardrails.evidence_map").value(true))
                .andExpect(jsonPath("$.evidence_map").isArray())
                .andExpect(jsonPath("$.evidence_map.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.evidence_map[0].claim_type").value("numeric"))
                .andExpect(jsonPath("$.evidence_map[0].claim_value").value("42"))
                .andExpect(jsonPath("$.evidence_map[0].source_ids").isArray());
    }

    @Test
    void requiresCitationsButNoExtractableClaims_deniesWithEvidenceNotSupported() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHitNoNumericOrDate()));

        String q = "hello-full-answer-no-claims";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("deny"))
                .andExpect(jsonPath("$.error_code").value("EVIDENCE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.meta.evidence_map_required").value(true))
                .andExpect(jsonPath("$.meta.evidence_map_outcome").value("missing"))
                .andExpect(jsonPath("$.evidence_map").isArray())
                .andExpect(jsonPath("$.evidence_map.length()").value(0));
    }

    @Test
    void enumClaimExtractedAndMappedToSnippet() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHitEnumOnly()));

        String q = "weather is RAIN";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.evidence_map").isArray())
                .andExpect(jsonPath("$.evidence_map.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.evidence_map[0].claim_type").value("enum"))
                .andExpect(jsonPath("$.evidence_map[0].claim_value").value("RAIN"))
                .andExpect(jsonPath("$.evidence_map[0].source_ids[0]").value("chunk-enum"));
    }

    @Test
    void weatherEnum_fromChineseAnswer_supportedByEnglishSnippetKeyword() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHitEnumPrecipitationOnly()));

        String q = "今天会下雨";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.evidence_map").isArray())
                .andExpect(jsonPath("$.evidence_map.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.evidence_map[0].claim_type").value("enum"))
                .andExpect(jsonPath("$.evidence_map[0].claim_value").value("RAIN"))
                .andExpect(jsonPath("$.evidence_map[0].source_ids[0]").value("chunk-precip"));
    }

    @Test
    void weatherEnum_fromEnglishToken_supportedByChineseSnippetKeyword() throws Exception {
        when(knowledgeRetrieveService.searchForRag(any(UUID.class), any(String.class), any(RagProperties.class)))
                .thenReturn(RagRetrieveResult.vectorOnly(singleHitEnumChineseRainOnly()));

        String q = "weather is RAIN";
        String body =
                """
                {"query":"%s","mode":"EVAL","requires_citations":true}
                """
                        .formatted(q);

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.evidence_map").isArray())
                .andExpect(jsonPath("$.evidence_map.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.evidence_map[0].claim_type").value("enum"))
                .andExpect(jsonPath("$.evidence_map[0].claim_value").value("RAIN"))
                .andExpect(jsonPath("$.evidence_map[0].source_ids[0]").value("chunk-cn-rain"));
    }

    private static List<RetrieveHit> singleHit() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-a");
        h.setDocumentId("doc-a");
        h.setContent("snippet body: price is 42");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }

    private static List<RetrieveHit> singleHitNoNumericOrDate() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-b");
        h.setDocumentId("doc-b");
        h.setContent("snippet body without numbers or dates");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }

    private static List<RetrieveHit> singleHitEnumOnly() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-enum");
        h.setDocumentId("doc-enum");
        h.setContent("forecast: RAIN");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }

    private static List<RetrieveHit> singleHitEnumPrecipitationOnly() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-precip");
        h.setDocumentId("doc-precip");
        h.setContent("forecast: precipitation expected");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }

    private static List<RetrieveHit> singleHitEnumChineseRainOnly() {
        RetrieveHit h = new RetrieveHit();
        h.setChunkId("chunk-cn-rain");
        h.setDocumentId("doc-cn-rain");
        h.setContent("天气预报：有降雨");
        h.setDistance(0.05);
        return new ArrayList<>(List.of(h));
    }
}
