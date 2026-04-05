package com.vagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3：流式对话 SSE 与取消任务的集成测试（默认 noop LLM，不依赖外网）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class M3StreamChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void streamReturns404WhenConversationNotOwned() throws Exception {
        String registerJson = """
                {"username":"m3_stream_user","password":"password12"}
                """;
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        String token = root.get("token").asText();

        String randomConv = UUID.randomUUID().toString().replace("-", "");
        mockMvc.perform(post("/api/v1/conversations/" + randomConv + "/chat/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelUnknownTaskReturns404() throws Exception {
        String registerJson = """
                {"username":"m3_cancel_user","password":"password12"}
                """;
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        String token = root.get("token").asText();

        mockMvc.perform(post("/api/v1/chat/tasks/not-a-real-task-id/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
