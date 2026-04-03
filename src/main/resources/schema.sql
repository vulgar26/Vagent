-- PostgreSQL DDL（本地开发）；test profile 下 H2 使用 MODE=PostgreSQL 执行同一脚本。
-- 主键为 CHAR(36) UUID 字符串，由 MyBatis-Plus IdType.ASSIGN_UUID 写入。
-- M2 接 pgvector 时在同一库执行：CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS conversations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL REFERENCES users (id),
    title VARCHAR(256) NULL,
    created_at TIMESTAMP(6) NOT NULL
);
