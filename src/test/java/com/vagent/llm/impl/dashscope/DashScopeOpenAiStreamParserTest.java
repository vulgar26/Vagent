package com.vagent.llm.impl.dashscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeOpenAiStreamParserTest {

    private final DashScopeOpenAiStreamParser parser = new DashScopeOpenAiStreamParser(new ObjectMapper());

    @Test
    void extractsDeltaContent() throws IOException {
        String json =
                "{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}";
        assertThat(parser.parseDataLineJson(json)).contains("你好");
    }

    @Test
    void emptyDelta_returnsEmptyOptional() throws IOException {
        String json = "{\"choices\":[{\"delta\":{}}]}";
        assertThat(parser.parseDataLineJson(json)).isEmpty();
    }

    @Test
    void errorField_throwsIOExceptionWithMessage() {
        String json = "{\"error\":{\"message\":\"insufficient balance\"}}";
        assertThatThrownBy(() -> parser.parseDataLineJson(json))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("insufficient balance");
    }
}
