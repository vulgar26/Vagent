package com.vagent.mcp.audit;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public final class McpToolInvocationAuditService {

    private final McpToolInvocationAuditMapper mapper;

    public McpToolInvocationAuditService(McpToolInvocationAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void record(McpToolInvocationAudit row) {
        if (row.getId() == null) {
            row.setId(UUID.randomUUID());
        }
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDateTime.now());
        }
        mapper.insert(row);
    }
}
