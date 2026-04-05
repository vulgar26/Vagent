package com.vagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 向量知识库集成测试：真实 PostgreSQL + pgvector（Testcontainers，需本机 Docker）。
 * <p>
 * 覆盖注册 → 入库多段分块 → 检索命中。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIf("com.vagent.M2KnowledgeVectorIntegrationTest#dockerAvailable")
class M2KnowledgeVectorIntegrationTest {

    /** 无 Docker 时跳过本类，避免 CI/本地未装 Docker 失败。 */
    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("vagent")
            .withUsername("vagent")
            .withPassword("vagent");

    @DynamicPropertySource
    static void registerPg(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-core.sql,classpath:schema-vector.sql");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerIngestAndRetrieve() throws Exception {
        String user = "m2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String registerJson = "{\"username\":\"" + user + "\",\"password\":\"password12\"}";
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        String token = root.get("token").asText();

        String longContent = "第一节内容。".repeat(80);
        String ingestJson = """
                {"title":"测试文档","content":"%s"}
                """.formatted(longContent);

        mockMvc.perform(post("/api/v1/kb/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.chunkCount", greaterThan(1)));

        mockMvc.perform(post("/api/v1/kb/retrieve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"第一节\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].chunkId").exists())
                .andExpect(jsonPath("$.hits[0].distance").exists());
    }
}
