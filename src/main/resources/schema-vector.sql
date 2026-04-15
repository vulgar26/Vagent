-- 向量知识库（真实 PostgreSQL + pgvector；M2 Testcontainers 等）。
-- 依赖 users 表为 uuid；权威迁移见 db/migration。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_documents (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS kb_chunks (
    id UUID NOT NULL PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_documents (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_chunks_user_id ON kb_chunks (user_id);

-- 全文检索列（与 Flyway V4 一致）；已有表时用 ALTER 补齐
ALTER TABLE kb_chunks
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_kb_chunks_content_tsv ON kb_chunks USING gin (content_tsv);
