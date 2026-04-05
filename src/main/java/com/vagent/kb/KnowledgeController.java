package com.vagent.kb;

import com.vagent.kb.dto.IngestDocumentRequest;
import com.vagent.kb.dto.IngestDocumentResponse;
import com.vagent.kb.dto.RetrieveRequest;
import com.vagent.kb.dto.RetrieveResponse;
import com.vagent.security.VagentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库 REST：入库与向量检索（需 JWT）。
 */
@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeController {

    private final KnowledgeIngestService knowledgeIngestService;
    private final KnowledgeRetrieveService knowledgeRetrieveService;

    public KnowledgeController(
            KnowledgeIngestService knowledgeIngestService,
            KnowledgeRetrieveService knowledgeRetrieveService) {
        this.knowledgeIngestService = knowledgeIngestService;
        this.knowledgeRetrieveService = knowledgeRetrieveService;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestDocumentResponse ingest(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @Valid @RequestBody IngestDocumentRequest request) {
        return knowledgeIngestService.ingest(principal.getUserId(), request.getTitle(), request.getContent());
    }

    @PostMapping("/retrieve")
    public RetrieveResponse retrieve(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @Valid @RequestBody RetrieveRequest request) {
        return new RetrieveResponse(
                knowledgeRetrieveService.search(principal.getUserId(), request.getQuery(), request.getTopK()));
    }
}
