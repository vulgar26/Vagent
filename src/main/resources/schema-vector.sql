-- 向量知识库（仅真实 PostgreSQL + pgvector；勿加入仅 H2 的 test profile）。
-- 需超级用户或已授权角色先安装扩展：CREATE EXTENSION IF NOT EXISTS vector;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_documents (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

-- 维度与 vagent.embedding.dimensions 一致（U2 默认 1024）；检索与入库使用同一度量（余弦 <=>）。
CREATE TABLE IF NOT EXISTS kb_chunks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    document_id CHAR(36) NOT NULL REFERENCES kb_documents (id) ON DELETE CASCADE,
    user_id CHAR(36) NOT NULL REFERENCES users (id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_chunks_user_id ON kb_chunks (user_id);
-- 数据量大时再建 ANN 索引（ivfflat/hnsw）；M2 开发期小数据量顺序扫描即可。
