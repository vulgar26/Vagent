package com.vagent.eval;

import com.vagent.mcp.client.McpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.vagent.eval.EvalChatContractTestSupport.TOKEN_PLAINTEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
            "vagent.eval.api.safety-rules-enabled=false",
            "vagent.guardrails.reflection.enabled=false",
            "vagent.eval.api.stub-tools-enabled=true",
            "vagent.mcp.enabled=true",
            "vagent.mcp.allowed-tools=echo,ping",
            "vagent.mcp.quota.enabled=true",
            "vagent.mcp.quota.window=10m",
            "vagent.mcp.quota.max-invocations-per-user-per-tool-per-window=2",
            "vagent.mcp.quota.max-invocations-per-conversation-per-tool-per-window=0"
        })
class EvalChatControllerRealToolQuotaMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private McpClient mcpClient;

    @Test
    void thirdRealToolCallHitsRateLimitWithoutCallingMcpAgain() throws Exception {
        when(mcpClient.callTool(eq("echo"), any())).thenReturn(Map.of("content", "ok"));

        String body =
                "{\"query\":\"q\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                        + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\","
                        + "\"tool_stub_id\":\"echo\"}";

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool.succeeded").value(true));

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool.succeeded").value(true));

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("tool"))
                .andExpect(jsonPath("$.error_code").value("TOOL_RATE_LIMITED"))
                .andExpect(jsonPath("$.tool.succeeded").value(false))
                .andExpect(jsonPath("$.tool.outcome").value("error"))
                .andExpect(jsonPath("$.meta.tool_error_code").value("TOOL_RATE_LIMITED"));

        verify(mcpClient, times(2)).callTool(eq("echo"), any());
    }
}
