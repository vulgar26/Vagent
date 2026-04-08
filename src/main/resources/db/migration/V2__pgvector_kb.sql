-- pgvector 知识库；需库级已允许创建扩展（镜像 postgres 超级用户一般可用）。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_documents (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE kb_chunks (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES kb_documents (id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL
);

CREATE INDEX idx_kb_chunks_user_id ON kb_chunks (user_id);
