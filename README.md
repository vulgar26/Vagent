# Vagent

Vagent 是一个 **企业风格对话式 RAG 后端**，重点展示 Java 后端在认证、多轮会话、知识库检索、流式输出、可观测与回归评测上的工程实现。项目面向“可运行、可测试、可解释”的 RAG 服务骨架，而不是只封装一次大模型调用。

核心链路：

```text
Auth -> Conversation -> Query Rewrite -> Hybrid Retrieve -> Gate -> SSE
```

## 项目定位

- 用户通过 JWT 登录后创建会话，消息与会话持久化。
- 知识库文档分块后写入 PostgreSQL/pgvector，检索时按用户隔离。
- 对话链路支持 query rewrite、规则化意图分支、向量检索与可选关键词检索融合。
- 检索后通过低置信/空命中门控控制是否继续生成，降低无依据回答风险。
- 使用 SSE 返回流式响应，并支持按任务取消。
- 内置评测接口与脚本，用于本地回归验证检索、引用约束、低置信门控和工具策略。

更细的 eval、meta、guardrail 字段说明见 [docs/README.md](docs/README.md)。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言与框架 | Java 17, Spring Boot 3.3 |
| Web 与安全 | Spring MVC, Spring Security, JWT, SSE |
| 数据访问 | PostgreSQL, pgvector, MyBatis-Plus, Flyway |
| 检索 | pgvector, PostgreSQL tsvector, Lucene BM25, RRF |
| AI 接入 | 可插拔 LLM/Embedding 接口, DashScope 兼容 OpenAI 接口 |
| 可观测 | Actuator, Micrometer, MDC traceId |
| 测试与工程化 | JUnit 5, Spring Boot Test, Testcontainers, Docker Compose, GitHub Actions |

## 已实现功能

- 认证与用户隔离：注册、登录、JWT 校验，知识库与会话按用户过滤。
- 会话与消息：会话 CRUD，多轮消息落库，删除会话时取消进行中的流式任务。
- 知识库：文档入库、文本分块、Embedding 写入、向量检索。
- RAG 编排：query rewrite、规则意图分支、RAG/澄清/寒暄分支。
- 混合检索：向量召回 + 可选关键词召回，并用 RRF 融合；关键词通道异常时可回退。
- 门控：空命中策略、检索后低置信门控、评测链路 quote-only/reflection guardrail 子集。
- SSE：首帧 meta、chunk/done/cancel/error 事件、任务取消。
- MCP 工具联调：HTTP MCP client、工具 schema 校验、白名单控制，默认关闭。
- 回归评测：`POST /api/v1/eval/chat`、固定题集脚本、compare 脚本与可选 CI 对接。
- 部署演示：Dockerfile、Docker Compose、Kubernetes 示例清单。

## 快速启动

环境要求：

- JDK 17+
- Docker 或本地 PostgreSQL 14+
- Maven Wrapper：`mvnw` / `mvnw.cmd`

启动 PostgreSQL + pgvector：

```bash
docker compose up -d
```

启动应用：

```bash
./mvnw spring-boot:run
```

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

访问：

- 健康检查：<http://localhost:8080/actuator/health>
- 简易前端：<http://localhost:8080/>

无外部模型密钥时，默认 `noop` / `hash` provider 仍可用于启动、接口联调和大部分测试。

## 环境变量

| 变量 | 用途 | 必填 |
| --- | --- | --- |
| `VAGENT_SECURITY_JWT_SECRET` | 生产 JWT 密钥，建议 32 字符以上 | 生产必填 |
| `DASHSCOPE_API_KEY` | DashScope 对话与嵌入 API Key | 使用 DashScope 时必填 |
| `VAGENT_EVAL_API_TOKEN_HASH` | Eval 接口 `X-Eval-Token` 的 SHA-256 hex | 启用 eval 时必填 |
| `MCP_TOKEN` | 出站调用 MCP Server 的 Bearer token | 启用 MCP 时按需 |
| `VAGENT_RAG_SSE_MEMBERSHIP_HMAC_SECRET` | SSE 检索命中哈希的 HMAC secret | 按需 |

本地私有配置可放在 `src/main/resources/application-local.yml`，该文件已被 `.gitignore` 忽略。

## 核心接口

统一前缀：`/api/v1`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/register` | 注册用户 |
| `POST` | `/auth/login` | 登录并返回 JWT |
| `GET` | `/conversations` | 查询当前用户会话列表 |
| `POST` | `/conversations` | 创建会话 |
| `DELETE` | `/conversations/{id}` | 删除会话并取消相关流式任务 |
| `POST` | `/kb/documents` | 写入知识库文档 |
| `POST` | `/kb/retrieve` | 仅检索知识库 |
| `POST` | `/conversations/{id}/chat/stream` | SSE 流式 RAG 对话 |
| `POST` | `/chat/tasks/{taskId}/cancel` | 取消流式任务 |
| `GET` | `/mcp/settings` | 查看 MCP 配置状态，默认关闭 |
| `GET` | `/mcp/tools` | MCP 工具列表联调，默认关闭 |
| `POST` | `/eval/chat` | 评测专用非流式接口，默认关闭 |

## 测试方式

运行默认测试：

```bash
./mvnw test
```

默认测试使用 H2 与轻量配置，不依赖本机 PostgreSQL。需要真实 pgvector 的集成测试可单独运行：

```bash
./mvnw test -Dtest=M2KnowledgeVectorIntegrationTest
./mvnw test -Dtest=HybridTsvectorRetrieveIntegrationTest
```

CI 配置见 [.github/workflows/ci.yml](.github/workflows/ci.yml)。远程 eval 对接与 compare 流程见 [plans/ci-eval-github-actions.md](plans/ci-eval-github-actions.md) 和 [plans/regression-compare-standard-runbook.md](plans/regression-compare-standard-runbook.md)。

## 文档导航

- [docs/README.md](docs/README.md)：文档地图，区分已实现说明、设计计划、面试准备与归档材料。
- [docs/Vagent-项目介绍.md](docs/Vagent-项目介绍.md)：较完整的项目说明。
- [docs/DECISIONS.md](docs/DECISIONS.md)：关键取舍与当前边界。
- [scripts/README-eval-kb.md](scripts/README-eval-kb.md)：评测知识库与脚本说明。
- [scripts/README-hybrid-rerank-ab.md](scripts/README-hybrid-rerank-ab.md)：hybrid / rerank A/B 对比流程。

## 已知限制

- 默认 LLM provider 为 `noop`，Embedding provider 为 `hash`；真实语义效果需配置 DashScope 并重建向量数据。
- `rerank` 当前是配置化扩展点与归因字段，默认关闭，不等同于已接入完整外部重排模型。
- MCP 工具调用默认关闭；主链路只支持显式工具意图、白名单与 schema 校验的受控子集。
- Eval 能力内置在本仓库，用于回归验证；完整多 target 调度、run/result/report 落库通常由外部评测服务承担。
- 简易前端用于本地演示，不是完整产品 UI。

## 后续计划

- 完善真实 rerank provider 与更稳定的检索质量评估。
- 补充更细的 LLM 首包耗时、工具耗时、检索阶段耗时拆分。
- 将 eval run/report 的长期管理放到独立评测服务或更清晰的外部流程。
- 在真实数据量下评估 pgvector HNSW/IVFFLAT 索引与参数。
- 完善许可证与更多最小可运行示例。

## 许可证

待补充。
