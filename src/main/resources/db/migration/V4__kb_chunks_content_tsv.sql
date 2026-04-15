-- P1-0b+：关键词通道升级为 PostgreSQL 全文检索（tsvector），供 hybrid 与向量 RRF 融合使用。
-- 依赖：PostgreSQL（H2 单测不跑此迁移）。

ALTER TABLE kb_chunks
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_kb_chunks_content_tsv ON kb_chunks USING gin (content_tsv);
