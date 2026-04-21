package com.vagent.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主链路 / eval real 工具在 {@code McpClient#callTool} 之前对<strong>已收敛</strong>的 arguments 做 JSON Schema（Draft 2020-12）校验。
 * Schema 资源位于 {@code classpath:/mcp/tool-arg-schemas/&lt;toolNameLower&gt;.schema.json}；未注册 schema 的工具跳过校验。
 */
@Component
public final class McpToolArgumentSchemaValidator {

    private static final String SCHEMA_ROOT = "/mcp/tool-arg-schemas/";

    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public McpToolArgumentSchemaValidator(ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
    }

    /**
     * @return 空表示通过或未配置 schema；否则为校验错误信息列表（顺序稳定）
     */
    public Optional<List<String>> validate(String toolName, Map<String, Object> arguments) {
        Optional<String> keyOpt = toolRegistry != null ? toolRegistry.argSchemaKey(toolName) : Optional.empty();
        if (keyOpt.isEmpty()) return Optional.empty();
        String key = keyOpt.get();
        JsonSchema schema = schemaCache.computeIfAbsent(key, this::loadSchema);
        JsonNode instance = objectMapper.valueToTree(arguments != null ? arguments : Map.of());
        Set<ValidationMessage> errors = schema.validate(instance);
        if (errors.isEmpty()) {
            return Optional.empty();
        }
        List<String> messages = new ArrayList<>(errors.size());
        for (ValidationMessage vm : errors) {
            messages.add(vm.getMessage());
        }
        messages.sort(String::compareTo);
        return Optional.of(List.copyOf(messages));
    }

    private JsonSchema loadSchema(String toolKey) {
        String path = SCHEMA_ROOT + toolKey + ".schema.json";
        try (InputStream in = McpToolArgumentSchemaValidator.class.getResourceAsStream(path)) {
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
