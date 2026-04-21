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
import static org.mockito.Mockito.never;
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
            "vagent.mcp.allowed-tools=echo,ping"
        })
class EvalChatControllerRealToolMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private McpClient mcpClient;

    @Test
    void realToolPolicyInvokesMcpWhenBeanPresent() throws Exception {
        when(mcpClient.callTool(eq("echo"), any()))
                .thenReturn(Map.of("content", "echo-result"));

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"hello-echo\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\","
                                                + "\"tool_stub_id\":\"echo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("tool"))
                .andExpect(jsonPath("$.answer").value("echo-result"))
                .andExpect(jsonPath("$.tool.used").value(true))
                .andExpect(jsonPath("$.tool.succeeded").value(true))
                .andExpect(jsonPath("$.tool.name").value("echo"))
                .andExpect(jsonPath("$.meta.eval_real_tools").value(true))
                .andExpect(jsonPath("$.meta.tool_version").value("1.0.0"))
                .andExpect(jsonPath("$.meta.tool_schema_hash").isString())
                .andExpect(jsonPath("$.meta.tool_arg_schema_validated").value(true))
                .andExpect(jsonPath("$.meta.tool_result_schema_required").value(true))
                .andExpect(jsonPath("$.meta.tool_result_schema_validated").value(true))
                .andExpect(jsonPath("$.capabilities.tools.supported").value(true));
    }

    @Test
    void realToolEchoEmptyQuerySchemaInvalidDoesNotCallMcp() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"schema-invalid-harness\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\","
                                                + "\"tool_stub_id\":\"echo\","
                                                + "\"mcp_tool_arguments\":{\"message\":\"\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("tool"))
                .andExpect(jsonPath("$.error_code").value("TOOL_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.tool.used").value(false))
                .andExpect(jsonPath("$.tool.succeeded").value(false))
                .andExpect(jsonPath("$.tool.outcome").value("error"))
                .andExpect(jsonPath("$.meta.tool_error_code").value("TOOL_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.meta.tool_schema_violations").isArray())
                .andExpect(jsonPath("$.meta.tool_arg_schema_validated").value(false))
                .andExpect(jsonPath("$.meta.tool_result_schema_required").value(true))
                .andExpect(jsonPath("$.meta.tool_result_schema_validated").value(false));

        verify(mcpClient, never()).callTool(any(), any());
    }

    @Test
    void realToolResultSchemaInvalidMarksErrorCode() throws Exception {
        when(mcpClient.callTool(eq("echo"), any()))
                .thenReturn(Map.of("not_content", "x"));

        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"hello-echo\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\","
                                                + "\"tool_stub_id\":\"echo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("tool"))
                .andExpect(jsonPath("$.error_code").value("TOOL_RESULT_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.tool.used").value(true))
                .andExpect(jsonPath("$.tool.succeeded").value(false))
                .andExpect(jsonPath("$.tool.outcome").value("error"))
                .andExpect(jsonPath("$.meta.tool_error_code").value("TOOL_RESULT_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.meta.tool_schema_violations").isArray())
                .andExpect(jsonPath("$.meta.tool_arg_schema_validated").value(true))
                .andExpect(jsonPath("$.meta.tool_result_schema_required").value(true))
                .andExpect(jsonPath("$.meta.tool_result_schema_validated").value(false));
    }

    @Test
    void realToolWithoutToolStubIdClarifies() throws Exception {
        mockMvc.perform(
                        post("/api/v1/eval/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Eval-Token", TOKEN_PLAINTEXT)
                                .content(
                                        "{\"query\":\"x\",\"mode\":\"EVAL\",\"requires_citations\":false,"
                                                + "\"expected_behavior\":\"tool\",\"tool_policy\":\"real\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.tool.used").value(false));
    }
}
