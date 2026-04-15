package com.vagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vagent.chat.rag.RagProperties;
import com.vagent.kb.KbChunkMapper;
import com.vagent.kb.KnowledgeRetrieveService;
import com.vagent.kb.RagRetrieveResult;
import com.vagent.user.UserIdFormats;
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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * hybrid 词法通道 {@code tsvector}：在真实 PostgreSQL + pgvector + {@code content_tsv}（见 {@code schema-vector.sql} / Flyway V4）下验证
 * {@link KbChunkMapper#searchLexicalTsvector} 与 {@link KnowledgeRetrieveService#searchForRag}。
 * <p>
 * 默认从 Surefire 排除（与 {@link M2KnowledgeVectorIntegrationTest} 相同），需本机 Docker；CI 无 Docker 时 {@link EnabledIf} 跳过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIf("com.vagent.HybridTsvectorRetrieveIntegrationTest#dockerAvailable")
class HybridTsvectorRetrieveIntegrationTest {

    private static final String MARKER = "VAGENT_P1_TSV_9c4e1a";

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

    @Autowired
    private KbChunkMapper kbChunkMapper;

    @Autowired
    private KnowledgeRetrieveService knowledgeRetrieveService;

    @Test
    void mapper_searchLexicalTsvector_finds_chunk() throws Exception {
        UUID userId = registerAndIngestSingleChunkDoc();
        String uid = UserIdFormats.canonical(userId);

        var hits = kbChunkMapper.searchLexicalTsvector(uid, MARKER, 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getContent()).contains(MARKER);
    }

    @Test
    void searchForRag_hybrid_tsvector_ok() throws Exception {
        UUID userId = registerAndIngestSingleChunkDoc();
        RagProperties rag = new RagProperties();
        rag.setTopK(5);
        rag.getHybrid().setEnabled(true);
        rag.getHybrid().setLexicalMode("tsvector");
        rag.getHybrid().setLexicalTopK(10);
        rag.getHybrid().setRrfK(60);

        RagRetrieveResult result = knowledgeRetrieveService.searchForRag(userId, MARKER, rag);
        assertThat(result.hybridLexicalOutcome()).isEqualTo("ok");
        assertThat(result.hybridLexicalMode()).isEqualTo("tsvector");
        assertThat(result.hits()).anyMatch(h -> h.getContent() != null && h.getContent().contains(MARKER));
    }

    /** 单块文档：主路向量与词法路都只有同一 chunk，避免 RRF 同分排序不确定。 */
    private UUID registerAndIngestSingleChunkDoc() throws Exception {
        String user = "tsv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String registerJson = "{\"username\":\"" + user + "\",\"password\":\"password12\"}";
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        UUID userId = UUID.fromString(root.get("userId").asText());

        String content = "锚点说明 " + MARKER + " 用于 hybrid tsvector 集成测试，正文长度远小于 chunk 窗口以保证仅一块。";
        String ingestJson = "{\"title\":\"tsv-int\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";

        mockMvc.perform(post("/api/v1/kb/documents")
                        .header("Authorization", "Bearer " + root.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestJson))
                .andExpect(status().isCreated());

        return userId;
    }
}
