package com.vagent.embedding.dashscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeEmbeddingsResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesDataEmbedding() throws IOException {
        String json =
                """
                {"data":[{"embedding":[0.1,0.2,0.3],"index":0}],"model":"x"}
                """;
        float[] v = DashScopeEmbeddingsResponseParser.parseEmbeddingArray(mapper, json, 3);
        assertThat(v).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void wrongLength_throws() {
        String json = "{\"data\":[{\"embedding\":[1,2]}]}";
        assertThatThrownBy(() -> DashScopeEmbeddingsResponseParser.parseEmbeddingArray(mapper, json, 3))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    void errorField_throws() {
        String json = "{\"error\":{\"message\":\"rate limit\"}}";
        assertThatThrownBy(() -> DashScopeEmbeddingsResponseParser.parseEmbeddingArray(mapper, json, 4))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("rate limit");
    }
}
