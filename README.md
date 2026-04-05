# Vagent

基于 Java 的企业风格对话式 RAG 系统（规划中）。

**详细总览**（架构、包结构、主链路、数据与配置）：见 [docs/Vagent-项目介绍.md](docs/Vagent-项目介绍.md)。

| 阶段 | 内容 |
|------|------|
| **M0** | Spring Boot 可运行骨架、Actuator、可替换 LLM 接口（`noop`） |
| **M1** | 用户注册/登录（JWT）、会话 API；**PostgreSQL + MyBatis-Plus**；测试使用 H2（无需本机 PG） |
| **M2** | **pgvector** 知识库、hash 嵌入占位、分块入库、向量检索 API（`/api/v1/kb/*`） |
| **M3** | **SSE** 流式对话（会话维度）、**任务取消**；`LlmClient` 支持 `noop` / `fake-stream` |
| **M4** | **`messages` 表**、**RAG 编排**（历史 + 检索 + SYSTEM）；`vagent.rag.*`；`meta.hitCount` |
| **M5** | **改写**（透传 / 历史 USER 拼接检索 query）、**规则意图**（寒暄不经检索、过短澄清）；`meta.branch` |
| **M6** | **单测补强**（任务注册表、SSE Bridge）、**DECISIONS**、可选 **Docker Compose**、文档收尾 |

- [docs/Vagent-项目介绍.md](docs/Vagent-项目介绍.md)（**项目详细介绍**：定位、模块、主链路、数据、API、配置）
- [docs/DECISIONS.md](docs/DECISIONS.md)（与策划书 §3 / Ragent 主链路的差异说明，**必读**）
- [docs/M0-实现说明.md](docs/M0-实现说明.md)（含 LLM 模块流程图与表）
- [docs/M1-实现说明.md](docs/M1-实现说明.md)（含 M1 各模块图与表）
- [docs/M2-实现说明.md](docs/M2-实现说明.md)（M2：向量表、嵌入、KB API、测试说明）
- [docs/M3-实现说明.md](docs/M3-实现说明.md)（M3：SSE、取消、`fake-stream`）
- [docs/M4-实现说明.md](docs/M4-实现说明.md)（M4：多轮消息、RAG、`vagent.rag`）
- [docs/M5-实现说明.md](docs/M5-实现说明.md)（M5：改写、意图分支、`vagent.orchestration`）
- [docs/M6-实现说明.md](docs/M6-实现说明.md)（M6：测试、DECISIONS、Compose）
- [docs/面试准备.md](docs/面试准备.md)（架构口述、追问答法；面试相关内容持续更新）

## 可选：Docker Compose（PostgreSQL + pgvector）

若本机未装 PostgreSQL，可用仓库根目录 **`docker-compose.yml`** 启动与 `application.yml` 默认一致的库：

```bash
docker compose up -d
```

待健康检查通过后执行 `mvn spring-boot:run`（默认连接 `localhost:5432`）。首次需保证镜像能拉取 `pgvector/pgvector:pg16`。

## 环境

- JDK 17+
- Maven 3.8+
- **本地运行**：PostgreSQL 14+（或兼容版本），已创建库与用户（与 `application.yml` 一致）；**M2** 需能执行 `CREATE EXTENSION vector`（通常需超级用户先装扩展一次）

### PostgreSQL 准备示例

```sql
CREATE USER vagent WITH PASSWORD 'vagent';
CREATE DATABASE vagent OWNER vagent ENCODING 'UTF8';
```

若库已存在、用户非 owner，需对 `public` schema 授权（示例）：

```sql
\c vagent
GRANT ALL ON SCHEMA public TO vagent;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO vagent;
```

启动应用时会依次执行 `src/main/resources/schema-core.sql`、`schema-vector.sql`（`spring.sql.init.mode=always`）；**生产建议改为迁移工具并关闭 always**。

**M2**：`schema-vector.sql` 内含 `CREATE EXTENSION IF NOT EXISTS vector` 与 `kb_*` 表；向量维度默认 **128**，与 `vagent.embedding.dimensions` 及表中 `vector(128)` 须一致。

## 构建与测试

```bash
mvn test
```

测试使用 **Spring profile `test`** + 内存 H2（`MODE=PostgreSQL`），**仅加载** `schema-core.sql`（无 pgvector 表）。

验证 **真实 pgvector** 时（需本机 **Docker**），单独运行：

```bash
mvn test -Dtest=M2KnowledgeVectorIntegrationTest
```

（默认 Surefire 已排除该类，避免首次拉取 `pgvector/pgvector` 镜像耗时过长。）

补充单测：**`LlmStreamTaskRegistryTest`**、**`LlmSseStreamingBridgeTest`**（取消与 done 回调语义）；编排改写/意图见 **`com.vagent.orchestration`** 包内测试。

## 最小演示（RAG + fake-stream）

1. 启动 DB（本机 PG 或 `docker compose up -d`），`mvn spring-boot:run`。  
2. `application.yml` 中设 `vagent.llm.provider: fake-stream`（可选，便于看到分块）。  
3. `POST /api/v1/auth/register` → `POST /api/v1/conversations` → `POST /api/v1/kb/documents` 入库 → `POST .../chat/stream` 读 SSE（首包 `meta` 含 `taskId`、`branch`、`hitCount`）。

## 运行

```bash
mvn spring-boot:run
```

健康检查：<http://localhost:8080/actuator/health>（端口以 `application.yml` 为准）。

### M1 API 速览（默认 `http://localhost:8080`）

- `POST /api/v1/auth/register` — JSON：`{"username":"...","password":"..."}`（密码 ≥8 位）
- `POST /api/v1/auth/login` — 同上
- `GET /api/v1/conversations` — Header：`Authorization: Bearer <token>`
- `POST /api/v1/conversations` — 可选 body：`{"title":"..."}`
- `POST /api/v1/kb/documents` — `{"title":"...","content":"..."}`（需 Bearer）
- `POST /api/v1/kb/retrieve` — `{"query":"...","topK":5}`（需 Bearer）
- `POST /api/v1/conversations/{conversationId}/chat/stream` — `{"message":"..."}`，响应 **SSE**（`text/event-stream`）；首条 JSON 含 `taskId`；**M4** 起 RAG 模式含 **`hitCount`**；**M5** 起另含 **`branch`**（`RAG` / `SYSTEM_DIALOG` / `CLARIFICATION`）
- `POST /api/v1/chat/tasks/{taskId}/cancel` — 取消对应流式任务（204，任务不存在或无权则 404）

流式演示可将 `vagent.llm.provider` 设为 **`fake-stream`**（本地按块回显用户消息，不调用外网）；默认 **`noop`** 无输出仅结束，适合测试。

`application-pg.yml` 为可选 profile，用于覆盖数据源 URL（如 Docker 服务名）；默认连接已在 `application.yml` 中配置。

## 许可证

（待补充）
