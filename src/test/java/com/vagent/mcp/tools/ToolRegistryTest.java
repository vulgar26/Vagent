package com.vagent.mcp.tools;

import com.vagent.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void builtinsAlwaysPresent() {
        ToolRegistry reg = new ToolRegistry(new McpProperties());
        assertTrue(reg.find("echo").isPresent());
        assertTrue(reg.find("ping").isPresent());
        assertEquals("1.0.0", reg.toolVersion("echo").orElseThrow());
    }

    @Test
    void mergesRegistryToolsFromClasspathTestResources() {
        McpProperties p = new McpProperties();
        McpProperties.RegisteredTool rt = new McpProperties.RegisteredTool();
        rt.setName("regtest");
        rt.setVersion("2.0.0");
        p.getRegistryTools().add(rt);

        ToolRegistry reg = new ToolRegistry(p);
        assertTrue(reg.find("regtest").isPresent());
        assertEquals("2.0.0", reg.toolVersion("regtest").orElseThrow());
        assertEquals("regtest", reg.argSchemaKey("regtest").orElseThrow());
        assertTrue(reg.toolSchemaHash("regtest").isPresent());
    }

    @Test
    void duplicateNameSkipsLaterEntry() {
        McpProperties p = new McpProperties();
        McpProperties.RegisteredTool dup = new McpProperties.RegisteredTool();
        dup.setName("echo");
        dup.setVersion("9.9.9");
        p.getRegistryTools().add(dup);

        ToolRegistry reg = new ToolRegistry(p);
        assertEquals("1.0.0", reg.toolVersion("echo").orElseThrow());
    }

    @Test
    void missingSchemaResourceFailsFast() {
        McpProperties p = new McpProperties();
        McpProperties.RegisteredTool rt = new McpProperties.RegisteredTool();
        rt.setName("no_such_tool_xyz");
        p.getRegistryTools().add(rt);

        assertThrows(IllegalStateException.class, () -> new ToolRegistry(p));
    }
}
