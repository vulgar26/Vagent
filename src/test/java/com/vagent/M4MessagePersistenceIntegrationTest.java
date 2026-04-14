package com.vagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vagent.chat.message.Message;
import com.vagent.chat.message.MessageService;
import com.vagent.user.UserIdFormats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4：验证 {@code messages} 表与 {@link MessageService} 在 H2 测试库上的读写（不依赖 RAG / 向量表）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class M4MessagePersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageService messageService;

    @Test
    void saveUserAndAssistant_roundTripOrdered() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String registerJson =
                "{\"username\":\"m4_msg_" + suffix + "\",\"password\":\"password12\"}";

        String authBody =
                mockMvc.perform(
                                post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(registerJson))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode auth = objectMapper.readTree(authBody);
        String userIdCompact = auth.get("userId").asText();
        String token = auth.get("token").asText();

        String convBody =
                mockMvc.perform(
                                post("/api/v1/conversations")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String conversationId = objectMapper.readTree(convBody).get("id").asText();

        var userUuid = UserIdFormats.parseUuid(userIdCompact);
        messageService.saveUserMessage(conversationId, userUuid, "你好");
        messageService.saveAssistantMessage(conversationId, userUuid, "我在");

        List<Message> recent = messageService.listRecentForConversation(conversationId, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getRole()).isEqualTo(Message.ROLE_USER);
        assertThat(recent.get(0).getContent()).isEqualTo("你好");
        assertThat(recent.get(1).getRole()).isEqualTo(Message.ROLE_ASSISTANT);
        assertThat(recent.get(1).getContent()).isEqualTo("我在");
    }
}
