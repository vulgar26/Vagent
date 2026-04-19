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
            "vagent.eval.api.stub-tools-enabled=false"
        })
class EvalChatControllerStubToolDisabledMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stubPolicyWithFeatureOffDoesNotAdvertiseTools() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .header("X-Eval-Case-Id", "p0_v0_tool_001")
                                .content(
                                        "{\"query\":\"现在北京天气怎么样？\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"stub\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.tools.supported").value(false))
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.meta.stub_tools_disabled").value(true))
                .andExpect(jsonPath("$.tool.used").value(false))
                .andExpect(jsonPath("$.tool.succeeded").value(false));
    }
}
