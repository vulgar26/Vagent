# Vagent

基于 **Java / Spring Boot** 的企业风格 **对话式 RAG（检索增强生成）** 示例项目：包含 **JWT 登录**、**会话与消息落库**、**pgvector 知识库**、**SSE 流式对话与取消**，并支持通义千问（DashScope）与 MCP（HTTP）联调能力。

**详细总览**（架构、包结构、主链路、数据与配置）：见 [docs/Vagent-项目介绍.md](docs/Vagent-项目介绍.md)。

**M6 之后：对齐 Ragent 的升级路线**（通义千问 API、分阶段里程碑、与 Ragent 能力对照）：见 **[docs/Vagent-升级策划书.md](docs/Vagent-升级策划书.md)**。

| 阶段 | 内容 |
|------|------|
| **M0** | Spring Boot 可运行骨架、Actuator、可替换 LLM 接口（`noop`） |
| **M1** | 用户注册/登录（JWT）、会话 API；**PostgreSQL + MyBatis-Plus**；测试使用 H2（无需本机 PG） |
| **M2** | **pgvector** 知识库、hash 嵌入占位、分块入库、向量检索 API（`/api/v1/kb/*`） |
| **M3** | **SSE** 流式对话（会话维度）、**任务取消**；`LlmClient` 支持 `noop` / `fake-stream` |
| **M4** | **`messages` 表**、**RAG 编排**（历史 + 检索 + SYSTEM）；`vagent.rag.*`；`meta.hitCount` |
| **M5** | **改写**（透传 / 历史 USER 拼接检索 query）、**规则意图**（寒暄不经检索、过短澄清）；`meta.branch` |
| **M6** | **单测补强**（任务注册表、SSE Bridge）、**DECISIONS**、可选 **Docker Compose**、文档收尾 |
| **U1** | **通义千问**（`dashscope`）：OpenAI 兼容流式 Chat Completions；`vagent.llm.dashscope.*` |
| **U2** | **通义千问嵌入**（`embedding.provider=dashscope`）；**向量 1024 维**；`vagent.embedding.dashscope.*` |
| **U3** | **空检索策略**（`vagent.rag.empty-hits-behavior`：`no-llm` / `allow-llm` 默认） |
| **U4** | **可观测**：MDC `traceId`、`vagent.rag.retrieve` / `vagent.chat.stream` 指标；生产 DDL 建议 Flyway（见 U4 文档） |
| **U5** | **第二路检索**：全表向量 + 与主路合并去重（`vagent.rag.second-path.*`，默认关） |
| **U6** | **MCP（独立进程）**：Vagent 作为 MCP Client（HTTP），提供 `/api/v1/mcp/*` 联调入口（默认关） |
| **U7** | **工具进主链路**：`tool=`、`/tool`、`工具:` 显式触发 + 白名单；`GET /api/v1/mcp/settings` 看 MCP 开关与白名单 |

- [docs/Vagent-项目介绍.md](docs/Vagent-项目介绍.md)（**项目详细介绍**：定位、模块、主链路、数据、API、配置）
- [docs/Vagent-项目策划书.md](docs/Vagent-项目策划书.md)（立项与 §3 主链路规格）
- [docs/Vagent-升级策划书.md](docs/Vagent-升级策划书.md)（**M6 后**：对齐 Ragent、**通义千问**、U1–U6 阶段）
- [docs/DECISIONS.md](docs/DECISIONS.md)（与策划书 §3 / Ragent 主链路的差异说明，**必读**）
- [docs/工程折中与生产对照.md](docs/工程折中与生产对照.md)（教学简化、最小改动 vs 生产常见做法，**决策清单**）
- [docs/M0-实现说明.md](docs/M0-实现说明.md)（含 LLM 模块流程图与表）
- [docs/M1-实现说明.md](docs/M1-实现说明.md)（含 M1 各模块图与表）
- [docs/M2-实现说明.md](docs/M2-实现说明.md)（M2：向量表、嵌入、KB API、测试说明）
- [docs/M3-实现说明.md](docs/M3-实现说明.md)（M3：SSE、取消、`fake-stream`）
- [docs/M4-实现说明.md](docs/M4-实现说明.md)（M4：多轮消息、RAG、`vagent.rag`）
- [docs/M5-实现说明.md](docs/M5-实现说明.md)（M5：改写、意图分支、`vagent.orchestration`）
- [docs/M6-实现说明.md](docs/M6-实现说明.md)（M6：测试、DECISIONS、Compose）
- [docs/U1-实现说明.md](docs/U1-实现说明.md)（U1：通义千问 DashScope 流式、`provider=dashscope`）
- [docs/U2-实现说明.md](docs/U2-实现说明.md)（U2：DashScope 嵌入、`vector(1024)`、迁移说明）
- [docs/U3-实现说明.md](docs/U3-实现说明.md)（U3：空检索是否调 LLM、`empty-hits-behavior`）
- [docs/U4-实现说明.md](docs/U4-实现说明.md)（U4：traceId、Micrometer、Flyway 建议）
- [docs/U5-实现说明.md](docs/U5-实现说明.md)（U5：第二路全局向量、合并、安全说明）
- [docs/U6-实现说明.md](docs/U6-实现说明.md)（U6：MCP Client（HTTP）、联调 API）
- [docs/U7-实现说明.md](docs/U7-实现说明.md)（U7：MCP 工具并入主链路（显式触发））
- [docs/面试准备.md](docs/面试准备.md)（架构口述、追问答法；面试相关内容持续更新）

## 可选：Docker Compose（PostgreSQL + pgvector）

若本机未装 PostgreSQL，可用仓库根目录 **`docker-compose.yml`** 启动与 `application.yml` 默认一致的库（**`ragent` / `postgres`**）：

```bash
docker compose up -d
```

待健康检查通过后执行 `mvn spring-boot:run`（默认连接 `localhost:5432`）。首次需保证镜像能拉取 `pgvector/pgvector:pg16`。

## 通义千问（DashScope，U1）

1. 在阿里云开通 DashScope，创建 API Key（**勿提交到 Git**）。  
2. 启动前设置环境变量：`DASHSCOPE_API_KEY=你的Key`（Windows 可用 `set` / PowerShell `$env:`）。  
3. 在 `application.yml`（或 `application-local.yml`）中设置：
   - `vagent.llm.provider: dashscope`
   - 按需调整 `vagent.llm.dashscope.chat-model`（如 `qwen-turbo`、`qwen-plus`）。  
4. 启动应用后，走与普通流式相同的 `POST .../chat/stream`；SSE 将输出**真实模型**增量。  
5. CI / 无网环境保持 `provider: noop`（见 `application-test.yml`）。

详见 [docs/U1-实现说明.md](docs/U1-实现说明.md) 与 [docs/Vagent-升级策划书.md](docs/Vagent-升级策划书.md)。

### 通义千问嵌入（U2）

1. 与 U1 相同，使用 `DASHSCOPE_API_KEY`（可在 yml 中配置 `vagent.embedding.dashscope.api-key`）。  
2. 设置 `vagent.embedding.provider: dashscope`，`dimensions` 保持 **1024**（与 DDL 一致）。  
3. **若库表仍为旧版 `vector(128)`**，须按 [docs/U2-实现说明.md](docs/U2-实现说明.md) 做表重建或迁移后再入库。  
4. 开发期可继续用 **`provider: hash`** 仅测检索链路，不配 Key。

### 空检索（U3）

- `vagent.rag.empty-hits-behavior: no-llm`：RAG 分支检索 **0 条**时不调 LLM，只推送固定文案并 `done`（对齐策划书 §3）。  
- 默认 **`allow-llm`**：与旧版一致，未命中时仍调 LLM（见 [DECISIONS.md](docs/DECISIONS.md)）。  
- 详见 [docs/U3-实现说明.md](docs/U3-实现说明.md)。

### 可观测（U4）

- 每个 HTTP 请求设置 MDC **`traceId`**，响应头 **`X-Trace-Id`**；SSE 异步线程通过 `llmStreamExecutor` 的 **TaskDecorator** 继承 MDC。  
- Micrometer：**`vagent.rag.retrieve`**（检索耗时）、**`vagent.chat.stream`**（LLM→SSE 流耗时，`outcome`=`success|cancelled|error`）；见 `GET /actuator/metrics`（需已暴露 `metrics`）。  
- 详见 [docs/U4-实现说明.md](docs/U4-实现说明.md)。

### 多路检索（U5）

- 对话 RAG 使用 **`searchForRag`**：先主路（用户隔离），满足条件时可合并 **全表**第二路；**默认关闭**，防跨租户泄露。  
- **`POST /kb/retrieve`** 仍为仅主路。详见 [docs/U5-实现说明.md](docs/U5-实现说明.md)。

### MCP（U6）

- Vagent 作为 **MCP Client（HTTP）**，用于联调独立进程的 MCP Server（当前为 **JSON-only** 响应模式）。默认关闭：`vagent.mcp.enabled=false`。
- 联调 API：`GET /api/v1/mcp/tools`、`POST /api/v1/mcp/tools/{name}`（需登录，且 MCP 启用时才有 Client Bean）。  
- **`GET /api/v1/mcp/settings`**：返回开关、`baseUrl`、协议版本、**白名单**等（不依赖 MCP Server 可达）。详见 [docs/U6-实现说明.md](docs/U6-实现说明.md)、[docs/U7-实现说明.md](docs/U7-实现说明.md)。

### 简易前端（静态页）

- 启动后浏览器打开 **`http://localhost:8080/`**（或 **`/index.html`**）：注册 / 登录、创建会话、用 **fetch** 读 **SSE** 流（带 `Authorization`）。  
- 源码在 `src/main/resources/static/`（`css/`、`js/`），与后端同域部署，无需单独 CORS。

### 数据库迁移（Flyway）

- **PostgreSQL**：启动时由 **Flyway** 执行 `src/main/resources/db/migration/V1__*.sql`、`V2__*.sql`；`spring.sql.init.mode` 默认为 **`never`**，避免与 migration 重复建表。  
- **H2 单测**：`test` profile 关闭 Flyway，仍用 `schema-core.sql` 初始化。  
- **Testcontainers（M2）**：动态属性仍会执行 `schema-core.sql` + `schema-vector.sql`。  
- 若库中**已有旧表**且从未跑过 Flyway，需**空库重建**或对当前库执行一次 **`flyway baseline`**（见 [Flyway 文档](https://documentation.red-gate.com/flyway)），否则会与 `V1` 冲突。

### 容器镜像与 Kubernetes（演示）

1. **构建 JAR**：`mvn -DskipTests package`  
2. **镜像**：仓库根目录 `Dockerfile`，`docker build -t vagent:local .`（需先有 `target/vagent-0.1.0-SNAPSHOT.jar`）。  
3. **K8s 示例清单**：`deploy/k8s/`（命名空间 `vagent-demo`、PostgreSQL + Vagent Deployment/Service）。**先修改 Secret 中的密码与 JWT 密钥**，再：

```bash
kubectl apply -f deploy/k8s/
kubectl -n vagent-demo wait --for=condition=available deployment/vagent --timeout=120s
kubectl -n vagent-demo port-forward svc/vagent 8080:8080
```

**K8s** 是把应用在**多副本 Pod**里跑、用 **Service** 做内网发现、用 **Deployment** 做滚动升级的编排工具；简历里常与 **Docker**、**Helm**、**CI/CD** 一起出现。本仓库清单仅作学习与演示，生产需 Ingress、持久卷、密钥管理与资源配额等（见 `deploy/k8s/README.md`）。

## 环境

- JDK 17+
- Maven 3.8+
- **本地运行**：PostgreSQL 14+（或兼容版本），已创建库与用户（与 `application.yml` 一致）；**M2** 需能执行 `CREATE EXTENSION vector`（通常需超级用户先装扩展一次）
- **JWT**：`vagent.security.jwt.remap-subject-by-username-when-user-missing` 默认为 **`false`**（`sub` 无对应用户即 401，需重新登录）；本地清库联调可在 `application-local.yml` 中设为 `true`，见 `application-local.example.yml`
- **生产**：`application-prod.yml`（`--spring.profiles.active=prod`）要求 **`VAGENT_SECURITY_JWT_SECRET`**、收紧 **CORS**、仅暴露 **`health`**、默认 **DashScope** + **`empty-hits-behavior: no-llm`**；K8s 示例已为 Deployment 设置 `SPRING_PROFILES_ACTIVE=prod`，并须自行配置 **`DASHSCOPE_API_KEY`** 等密钥

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

**PostgreSQL 运行时 DDL** 以 **`db/migration`** 为准；`schema-core.sql` / `schema-vector.sql` 仍保留作说明与 H2 / 集成测试引用。

**M2 / U2**：`V2__pgvector_kb.sql` / `schema-vector.sql` 内含 `CREATE EXTENSION IF NOT EXISTS vector` 与 `kb_*` 表；向量维度默认 **1024**，与 `vagent.embedding.dimensions`、`KbChunkMapper` 中 `vector(1024)` 须一致（从旧版 128 升级须见 [U2-实现说明.md](docs/U2-实现说明.md)）。

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
- `GET /api/v1/mcp/settings` — MCP 开关与白名单（需 Bearer）
- `POST /api/v1/conversations/{conversationId}/chat/stream` — `{"message":"..."}`，响应 **SSE**（`text/event-stream`）；首条 JSON 含 `taskId`；**M4** 起 RAG 模式含 **`hitCount`**；**M5** 起另含 **`branch`**（`RAG` / `SYSTEM_DIALOG` / `CLARIFICATION`）
- `POST /api/v1/chat/tasks/{taskId}/cancel` — 取消对应流式任务（204，任务不存在或无权则 404）

流式演示可将 `vagent.llm.provider` 设为 **`fake-stream`**（本地按块回显用户消息，不调用外网）；默认 **`noop`** 无输出仅结束，适合测试。

`application-pg.yml` 为可选 profile，用于覆盖数据源 URL（如 Docker 服务名）；默认连接已在 `application.yml` 中配置。

## 许可证

（待补充）
