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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-D4：eval API 关闭时 404（无 EvalChatResponse 体，契约不适用）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "vagent.eval.api.enabled=false")
class EvalChatControllerEvalDisabledMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void evalDisabledReturns404() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", "any")
                                .content("{\"query\":\"hello world\",\"mode\":\"EVAL\"}"))
                .andExpect(status().isNotFound());
    }
}
