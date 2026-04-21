# Vagent

**Vagent** 是一个 **Java / Spring Boot** 的**对话式 RAG（检索增强生成）**服务：用户 **JWT** 登录后会话与多轮消息落库，**PostgreSQL + pgvector** 做文档分块与向量检索，对话通过 **SSE** 流式返回并支持 **按任务取消**。大模型与嵌入可切换为占位实现、本地假流式，或 **阿里云 DashScope（OpenAI 兼容接口）**；可选 **MCP HTTP 客户端** 做工具联调，并在 RAG 主链路中按白名单注入工具结果。另提供 **HTTP 评测接口**（默认关闭），用于自动化回归与门控验证。

---

## 已实现能力

- **认证与会话**：注册 / 登录签发 JWT；会话 CRUD；删除会话时取消该会话下进行中的流式任务。
- **知识库**：文档分块、嵌入写入、按用户隔离的向量检索；`POST /api/v1/kb/documents` 入库，`POST /api/v1/kb/retrieve` 仅检索。
- **对话与 RAG**：多轮 `messages` 持久化；流式入口拼历史、检索片段与系统提示后调用 **`LlmClient`**；SSE 首包 **`meta`** 含 **`taskId`**、**`branch`**（如 RAG / 寒暄 / 澄清）、**`hitCount`** 等。
- **编排**：检索前 **query 改写**（如透传、拼接历史用户句）；**规则意图**（过短澄清、前缀寒暄不经检索、正常走 RAG）。
- **空检索**：**`vagent.rag.empty-hits-behavior`** 控制检索 0 条时是否仍调用大模型（`no-llm` 则固定文案结束）。
- **第二路检索（可选）**：在满足配置时合并**全表**向量召回（默认关闭，避免跨租户误用）。
- **混合检索（可选）**：向量与关键词路 **RRF** 融合；关键词路支持 **ILIKE**、**PostgreSQL tsvector（GIN）** 或 **Lucene BM25**（`vagent.rag.hybrid.*`）。
- **重排序（可选）**：`vagent.rag.rerank.*`，默认关闭。
- **门控**：检索前 **`EvalChatSafetyGate`**（与 SSE、Eval 共用开关 **`vagent.eval.api.safety-rules-enabled`**）；检索后 **`RagPostRetrieveGate`**（主配置 **`vagent.rag.gate.*`**）。
- **可观测**：请求级 **MDC `traceId`**、响应头 **`X-Trace-Id`**；异步流式线程传递 MDC；Micrometer 计时 **`vagent.rag.retrieve`**、**`vagent.chat.stream`**。
- **DashScope**：流式对话 **`vagent.llm.provider=dashscope`**；嵌入 **`vagent.embedding.provider=dashscope`**（默认 **1024** 维，须与库表一致）。
- **MCP**：**`vagent.mcp.enabled`** 打开后提供 **`/api/v1/mcp/*`** 联调；**`vagent.orchestration.tool-intent-enabled`** 与 **`vagent.mcp.allowed-tools`** 控制主链路是否在 RAG 分支显式调用工具并把结果写入系统上下文，**`meta`** 可出现 **`toolUsed`**、**`toolName`** 等。
- **评测接口**：**`POST /api/v1/eval/chat`**（snake_case、非流式）；**`vagent.eval.api.enabled=false`** 时整段 **`/api/v1/eval/**`** 返回 **404**；启用后校验 **`X-Eval-Token`**（配置为明文 token 的 **SHA-256 小写 hex**，勿把明文提交进仓库）。支持 debug 模式下的命中 id 透出限制、**`full-answer-enabled`** 聚合真实回答、与 **`vagent.guardrails.reflection.*`** 配合的一次性门控（Eval **`meta`** 中相关字段）；可选 **`vagent.guardrails.quote-only.*`**（**`strictness`** + **`scope`**）+ 请求体 **`quote_only: true`** 的 **quote-only** 子串门控（语义见 **`plans/quote-only-guardrails.md`**）；**`tool_policy=stub`** 时的进程内桩工具（**`vagent.eval.api.stub-tools-enabled`**）；**`tool_policy=real`** 时在 **`vagent.mcp.enabled`** 且 **`McpClient`** 就绪下用 **`tool_stub_id`** 作为 MCP 工具名同步调用（**`vagent.mcp.tool-call-timeout`**，与主链路一致；非 **`stub-tool-timeout-ms`**；否则澄清短路）；**`expected_behavior=tool`** 与非可执行工具策略的澄清短路。
- **运维与演示**：Actuator（健康、指标等）；根目录 **Docker Compose**（PostgreSQL + pgvector）；**Dockerfile** 与 **`deploy/k8s/`** 演示清单。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 运行时 / 构建 | **Java 17**、**Maven**（仓库内 **Wrapper**：`mvnw` / `mvnw.cmd`） |
| 框架 | **Spring Boot 3.3.x**（Web、Security、Validation、Actuator） |
| 持久化 | **MyBatis-Plus**、**PostgreSQL**；**Flyway**（`src/main/resources/db/migration`） |
| 向量 | **pgvector**；默认向量维 **1024**（须与表结构一致） |
| 安全 | **Spring Security** + **JWT**（`jjwt`） |
| 测试 | JUnit 5、Spring Boot Test、**H2**（PostgreSQL 兼容模式）、可选 **Testcontainers** |

---

## 仓库结构（高层）

| 路径 | 说明 |
|------|------|
| `src/main/java/com/vagent/` | `auth`、`security`、`user`、`conversation`、`chat`（RAG / SSE）、`kb`、`embedding`、`llm`、`orchestration`、`mcp`、`eval`、`observability`、`rag` 等 |
| `src/main/resources/application*.yml` | 配置；**`application-prod.yml`** 收紧 CORS 与 Actuator 暴露，并默认 **`empty-hits-behavior: no-llm`** 与 DashScope |
| `src/main/resources/db/migration/` | 数据库迁移（核心表、向量表、UUID 调整、`kb_chunks.content_tsv` 供全文检索通道等） |
| `src/main/resources/static/` | 简易单页：注册 / 登录、会话、**fetch + SSE** |
| `scripts/` | 评测数据与辅助脚本（如 **`scripts/README-eval-kb.md`**） |
| `deploy/k8s/` | Kubernetes 演示清单（部署前修改 Secret） |

---

## 环境要求

- **JDK 17+**
- 全功能本地运行：**PostgreSQL**（建议 14+），且具备 **`CREATE EXTENSION vector`** 权限（或已由 DBA 装好扩展）
- 构建：优先 **`./mvnw`** 或 **`mvnw.cmd`**

---

## 快速开始（PostgreSQL）

### 1. 准备数据库

与 **`src/main/resources/application.yml`** 默认一致：库名 **`vagent`**，用户 **`postgres`** / 密码 **`postgres`**（生产务必改掉）。

```sql
CREATE DATABASE vagent ENCODING 'UTF8';
-- 在 vagent 库中由 Flyway 或超级用户完成 vector 扩展与建表；若权限不足，可手动：
-- CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 可选：Docker Compose

根目录 **`docker-compose.yml`** 使用 **pgvector/pgvector:pg16**，端口 **5432**，库 **`vagent`**：

```bash
docker compose up -d
```

若与本机其他 PostgreSQL **端口冲突**，或与其他项目共用同一 PG 实例：在同一实例上 **`CREATE DATABASE vagent`** 即可，**勿**在别的业务库混跑两套 Flyway。

### 3. 启动应用

```bash
./mvnw spring-boot:run
```

- 健康检查：<http://localhost:8080/actuator/health>  
- 简易前端：<http://localhost:8080/>

### 4. 联调大模型与嵌入（DashScope）

1. 设置环境变量 **`DASHSCOPE_API_KEY`**（勿写入仓库）。  
2. 在配置中设置 **`vagent.llm.provider: dashscope`**、**`vagent.embedding.provider: dashscope`**，对话模型与嵌入模型名见 **`application.yml`** 中 **`vagent.llm.dashscope.*`**、**`vagent.embedding.dashscope.*`**。  
3. 若库表向量维度曾低于 **1024**，需先按当前表结构迁移后再写入向量。

### 5. 无外网时的最小演示

将 **`vagent.llm.provider`** 设为 **`fake-stream`**（分块假流）或 **`noop`**，按「注册 → 建会话 →（可选）`POST /api/v1/kb/documents` → `POST .../chat/stream`」走通 SSE。

---

## 核心配置前缀（`vagent.*`）

| 前缀 | 作用 |
|------|------|
| `vagent.security.jwt.*` | JWT 密钥、过期、`sub` 与用户缺失时的策略等 |
| `vagent.rag.*` | RAG 开关、topK、历史条数、空命中策略、第二路检索、hybrid、rerank、检索后门控 **`gate.*`** |
| `vagent.orchestration.*` | 改写策略、寒暄 / 澄清规则、**工具意图**（显式触发语法与白名单配合 MCP） |
| `vagent.llm.*` / `vagent.embedding.*` | 模型与嵌入提供方、DashScope 参数、分块长度 |
| `vagent.mcp.*` | MCP Client 开关、URL、协议版本、**主链路允许的工具名列表** |
| `vagent.eval.api.*` | Eval 开关、token 哈希、debug 与 IP 限制、full-answer、membership top-N 等 |
| `vagent.guardrails.reflection.*` | Eval 路径可选的一次性门控（默认关闭） |
| `vagent.guardrails.quote-only.*` | Eval **quote-only**：**`strictness`**（`relaxed` / `moderate` / `strict`）与 **`scope`**（`digits_only` / `digits_plus_tokens` / `digits_plus_tokens_plus_evidence`）；须与请求 **`quote_only`** 同开；可选 **`apply-to-sse-stream`** 使主对话 SSE 缓冲全文后与 eval 同源门控 |

生产建议使用 **`--spring.profiles.active=prod`**，并设置 **`VAGENT_SECURITY_JWT_SECRET`**（长度与强度满足 **`application-prod.yml`** 要求）及各类密钥。

---

## HTTP API 速览（前缀 `/api/v1`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register`、`/auth/login` | 注册 / 登录，返回 JWT |
| GET / POST / DELETE | `/conversations`、`/conversations/{id}` | 会话列表、创建、删除（删除会取消进行中的流式任务） |
| POST | `/kb/documents` | 文档入库（分块 + 嵌入） |
| POST | `/kb/retrieve` | 仅检索（与 RAG 内 `searchForRag` 的策略集合不必完全一致） |
| POST | `/conversations/{id}/chat/stream` | **SSE** 流式对话；首包 **`meta`** 含 **`taskId`**、**`branch`**、**`hitCount`**；启用工具意图且调用成功时可有 **`toolUsed`** 等 |
| POST | `/chat/tasks/{taskId}/cancel` | 取消流式任务 |
| GET / POST | `/mcp/settings`、`/mcp/tools`、`/mcp/tools/{name}` | MCP 联调（需登录且 **`vagent.mcp.enabled=true`**） |
| POST | `/eval/chat` | 评测专用（需 **`vagent.eval.api.enabled=true`** + **`X-Eval-Token`**；未启用时 **404**） |

---

## 评测接口（Eval）

- **路径**：`POST /api/v1/eval/chat`  
- **启用**：**`vagent.eval.api.enabled=true`**，并配置 **`vagent.eval.api.token-hash`**（明文 token 的 **SHA-256 小写 hex**；支持逗号分隔多哈希）。未启用时 **`/api/v1/eval/**`** 对外 **404**。  
- **调试**：**`vagent.eval.api.debug-enabled=true`** 且请求 **`mode=EVAL_DEBUG`** 时，`meta` 才可能含明文 **`retrieval_hit_ids[]`**；可配合 **`allow-cidrs`**、**`trust-forwarded-headers`** 收紧。  
- **行为**：与主线共享检索与门控；**`vagent.eval.api.full-answer-enabled=true`** 时可在通过门控后调用 **`LlmClient`** 生成正文（默认占位以降低 CI 成本与外网依赖）。  
- **工具题**：**`tool_policy=stub`** 走进程内桩（结构化 payload 默认经 **`classpath:/eval/stub-schemas/*.schema.json`** 校验，可用 **`vagent.eval.api.stub-tool-json-schema-validation-enabled`** 关闭）；**`tool_policy=real`** 在 MCP 就绪时用 **`tool_stub_id`** 调 **`McpClient`**（否则澄清）；详见 **`scripts/README-eval-kb.md`** §5。  
- **Quote-only**：服务端 **`vagent.guardrails.quote-only.enabled=true`** 且 JSON **`"quote_only": true`** 时，对 **`behavior=answer`** 做门控；**`strictness`** 控制敏感度，**`scope`** 控制只卡数字、数字+token、或再要求 **evidence_map** 数字绑定；**`meta.quote_only_scope`** 回显生效范围。与 **reflection** 的顺序及可选 **SSE 缓冲**（**`quote-only.apply-to-sse-stream`**）见 **`plans/quote-only-guardrails.md`**。  
- **数据脚本**：**`scripts/README-eval-kb.md`**；混合检索 / rerank A/B 与 compare 契约门禁见 **`scripts/README-hybrid-rerank-ab.md`**。

---

## 构建与测试

```bash
./mvnw test
```

- **默认**：`test` profile + **H2**（**`schema-core.sql`**），不跑 Flyway；RAG 在测试配置中通常关闭。  
- **门控 / quote-only 等变更**：合并前建议在**本机**跑一次**完整** `./mvnw test`（勿只跑 `EvalQuoteOnlyGuardTest` 等子集），以免其它 Spring 集成用例回归未被发现；约定见 **`plans/quote-only-guardrails.md`**（「合并后与 CI 回归建议」）。  
- **需本机 Docker 的 pgvector 集成测**：Surefire 默认排除部分类以免拉镜像过慢，可按需单独运行：

```bash
./mvnw test -Dtest=M2KnowledgeVectorIntegrationTest
./mvnw test -Dtest=HybridTsvectorRetrieveIntegrationTest
```

**CI（本仓库）**  
- **[.github/workflows/ci.yml](.github/workflows/ci.yml)**：在 **JDK 17** 下**分两阶段**跑测试：先 **`./mvnw -B test -P eval-smoke`**（仅 `com.vagent.eval` 包内评测相关用例），再 **`./mvnw -B test -P skip-eval-in-ci`**（其余模块，排除 eval 包），减轻单次 job 内连续启动多个 Spring 上下文时的内存压力；说明见 **`plans/eval-ci-smoke.md`**。  
- **[.github/workflows/eval-remote.yml](.github/workflows/eval-remote.yml)**（可选）：定时或手动触发，向 **已可达的 vagent-eval** 提交一次完整 run；需在 GitHub **Repository secrets** 中配置 **`EVAL_BASE_URL`**、**`EVAL_RUN_PAYLOAD_JSON`**，可选 **`EVAL_HTTP_TOKEN`**。未配置时步骤会跳过且 job 仍为绿；配置后若 run 失败、报告门禁为 `false` 或轮询超时，job **失败**（退出码见 **`plans/ci-eval-github-actions.md`**）。工作流会尝试上传 **`eval-remote-report.json`** 为 artifact（有文件才上传）。公网 / 自托管 Runner / 隧道等说明见 **`plans/ci-eval-github-actions.md`**。
- **[.github/workflows/hybrid-ab-compare.yml](.github/workflows/hybrid-ab-compare.yml)**（可选，P1-0b）：对已存在的 **`base_run_id` / `cand_run_id`** 跑 **`compare-eval-runs.ps1`**（**同一 `dataset_id`** + **`StrictContractGate`**）；需 **`EVAL_BASE_URL`**（及可选 **`EVAL_HTTP_TOKEN`**）。说明见 **`scripts/README-hybrid-rerank-ab.md`** §5。  
- **评测回归留证**：`base` / `cand` run 与 compare 流程见 **`plans/regression-compare-standard-runbook.md`**。

---

## 容器与 Kubernetes（演示）

1. 打包：`./mvnw -DskipTests package`（Windows：`mvnw.cmd`）  
2. 镜像：根目录 **`Dockerfile`**，`docker build -t vagent:local .`（需先有 **`target/vagent-0.1.0-SNAPSHOT.jar`**）  
3. 清单：**`deploy/k8s/`** — 部署前修改 Secret；说明见 **`deploy/k8s/README.md`**

---

## 许可证

（待补充）
