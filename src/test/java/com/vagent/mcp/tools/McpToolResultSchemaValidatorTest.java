package com.vagent.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolResultSchemaValidatorTest {

    private final McpToolResultSchemaValidator validator =
            new McpToolResultSchemaValidator(new ObjectMapper(), new ToolRegistry());

    @Test
    void echoMissingContentFailsSchema() {
        Optional<List<String>> v = validator.validate("echo", Map.of("not_content", "x"));
        assertTrue(v.isPresent());
        assertFalse(v.get().isEmpty());
    }

    @Test
    void echoValidPasses() {
        assertTrue(validator.validate("echo", Map.of("content", "ok")).isEmpty());
    }

    @Test
    void pingValidPasses() {
        assertTrue(validator.validate("ping", Map.of("content", "pong")).isEmpty());
    }

    @Test
    void unknownToolSkipsValidation() {
        assertTrue(validator.validate("custom_tool", Map.of("x", 1)).isEmpty());
    }

    @Test
    void echoCaseInsensitiveLookup() {
        assertTrue(validator.validate("Echo", Map.of("content", "ok")).isEmpty());
    }
}

