package com.vagent.kb;

import com.vagent.embedding.EmbeddingClient;
import com.vagent.kb.dto.IngestDocumentResponse;
import com.vagent.user.User;
import com.vagent.user.UserIdFormats;
import com.vagent.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 知识库入库：分块、嵌入、按用户隔离写入。
 */
@Service
public class KnowledgeIngestService {

    private final UserMapper userMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final TextChunkingService textChunkingService;
    private final EmbeddingClient embeddingClient;

    public KnowledgeIngestService(
            UserMapper userMapper,
            KbDocumentMapper kbDocumentMapper,
            KbChunkMapper kbChunkMapper,
            TextChunkingService textChunkingService,
            EmbeddingClient embeddingClient) {
        this.userMapper = userMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.textChunkingService = textChunkingService;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public IngestDocumentResponse ingest(UUID userId, String title, String content) {
        String uid = UserIdFormats.canonical(userId);
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new IllegalStateException("用户不存在: " + uid);
        }
        KbDocument doc = new KbDocument();
        doc.setUserId(uid);
        doc.setTitle(title.trim());
        doc.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        kbDocumentMapper.insert(doc);

        List<String> chunks = textChunkingService.split(content);
        int idx = 0;
        for (String chunkText : chunks) {
            KbChunk row = new KbChunk();
            row.setDocumentId(doc.getId());
            row.setUserId(uid);
            row.setChunkIndex(idx++);
            row.setContent(chunkText);
            row.setEmbedding(embeddingClient.embed(chunkText));
            kbChunkMapper.insert(row);
        }
        return new IngestDocumentResponse(doc.getId(), chunks.size());
    }
}
