package com.vagent.eval.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对 {@code stub_weather} / {@code stub_train} / {@code stub_search} 的结构化 payload 做 JSON Schema
 *（Draft 2020-12）校验；schema 位于 {@code classpath:/eval/stub-schemas/&lt;toolKey&gt;.schema.json}。
 */
@Component
public final class EvalStubToolPayloadValidator {

    private static final String SCHEMA_ROOT = "/eval/stub-schemas/";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public EvalStubToolPayloadValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return 空表示通过；否则为首个校验错误信息（用于日志或排障）
     */
    public Optional<String> validate(String toolKey, JsonNode payload) {
        if (!requiresSchema(toolKey)) {
            return Optional.empty();
        }
        JsonSchema schema = schemaCache.computeIfAbsent(toolKey, this::loadSchema);
        Set<ValidationMessage> errors = schema.validate(payload);
        if (errors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(errors.iterator().next().getMessage());
    }

    private static boolean requiresSchema(String toolKey) {
        return "stub_weather".equals(toolKey)
                || "stub_train".equals(toolKey)
                || "stub_search".equals(toolKey);
    }

    private JsonSchema loadSchema(String toolKey) {
        String path = SCHEMA_ROOT + toolKey + ".schema.json";
        try (InputStream in = EvalStubToolPayloadValidator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing JSON Schema resource: " + path);
            }
            JsonNode schemaNode = objectMapper.readTree(in);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            return factory.getSchema(schemaNode);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JSON Schema: " + path, e);
        }
    }
}
