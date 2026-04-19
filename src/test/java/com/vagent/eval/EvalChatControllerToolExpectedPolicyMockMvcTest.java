package com.vagent.eval;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
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
            "vagent.eval.api.safety-rules-enabled=false",
            "vagent.guardrails.reflection.enabled=false",
            "vagent.eval.api.stub-tools-enabled=true"
        })
class EvalChatControllerToolExpectedPolicyMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void toolExpectedWithDisabledPolicyDoesNotReturnAnswer() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"任意\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.meta.tool_eval_non_stub").value(true))
                .andExpect(jsonPath("$.meta.tool_policy").value("disabled"))
                .andExpect(jsonPath("$.tool.required").value(true))
                .andExpect(jsonPath("$.tool.used").value(false))
                .andExpect(jsonPath("$.tool.succeeded").value(false));
    }

    @Test
    void toolExpectedWithRealPolicyDoesNotReturnAnswer() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"任意\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.meta.tool_eval_non_stub").value(true))
                .andExpect(jsonPath("$.meta.tool_policy").value("real"))
                .andExpect(jsonPath("$.tool.used").value(false));
    }
}
