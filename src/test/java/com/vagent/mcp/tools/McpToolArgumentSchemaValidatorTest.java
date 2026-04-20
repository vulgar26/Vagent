package com.vagent.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolArgumentSchemaValidatorTest {

    private final McpToolArgumentSchemaValidator validator =
            new McpToolArgumentSchemaValidator(new ObjectMapper());

    @Test
    void echoEmptyMessageFailsSchema() {
        Optional<List<String>> v = validator.validate("echo", Map.of("message", ""));
        assertTrue(v.isPresent());
        assertFalse(v.get().isEmpty());
    }

    @Test
    void echoValidPasses() {
        assertTrue(validator.validate("echo", Map.of("message", "x")).isEmpty());
    }

    @Test
    void pingEmptyObjectPasses() {
        assertTrue(validator.validate("ping", Map.of()).isEmpty());
    }

    @Test
    void unknownToolSkipsValidation() {
        assertTrue(validator.validate("custom_tool", Map.of("x", 1)).isEmpty());
    }

    @Test
    void echoCaseInsensitiveLookup() {
        assertTrue(validator.validate("Echo", Map.of("message", "ok")).isEmpty());
    }

    @Test
    void pingRejectsExtraProperties() {
        Optional<List<String>> v = validator.validate("ping", Map.of("extra", "nope"));
        assertTrue(v.isPresent());
    }
}
