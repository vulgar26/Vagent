package com.vagent.eval;

import com.vagent.kb.KnowledgeRetrieveService;
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

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
import static com.vagent.eval.EvalChatContractTestSupport.assertTopLevelEvalChatContract;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-D4：检索关闭（POLICY_DISABLED）仍须满足契约顶层。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "vagent.eval.api.enabled=true",
            "vagent.eval.api.token-hash=9768cbb10efdc9a0cec74fbe4314cadd8885ba4993e19fa02e554902b2ce7533",
            "vagent.rag.enabled=false",
            "vagent.guardrails.reflection.enabled=false"
        })
class EvalChatControllerRagDisabledMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void ragDisabledReturnsPolicyDeniedWithContract() throws Exception {
        MvcResult r =
                mockMvc.perform(
                                post("/api/v1/eval/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                        .content("{\"query\":\"hello world\",\"mode\":\"EVAL\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.behavior").value("deny"))
                        .andExpect(jsonPath("$.error_code").value("POLICY_DISABLED"))
                        .andReturn();
        assertTopLevelEvalChatContract(r.getResponse().getContentAsString());
    }
}
