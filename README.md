# Vagent

Vagent 是一个 **企业风格对话式 RAG 后端**，重点展示 Java 后端在认证、多轮会话、知识库检索、流式输出、可观测与回归评测上的工程实现。它不是一次大模型 API 调用的薄封装，而是一个可运行、可测试、可解释的 RAG 服务骨架。

## 核心链路

```text
Auth -> Conversation -> Query Rewrite -> Hybrid Retrieve -> Gate -> SSE
```

链路含义：

- `Auth`：JWT 登录与用户身份解析。
- `Conversation`：会话与多轮消息持久化。
- `Query Rewrite`：检索前 query 透传或拼接历史上下文。
- `Hybrid Retrieve`：向量召回 + 可选关键词召回，并用 RRF 融合。
- `Gate`：空命中、低置信和评测 guardrail 子集。
- `SSE`：流式响应、任务取消和首帧元信息。

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
- 混合检索：向量召回 + 可选关键词召回，并用 RRF 融合；关键词通道异常时回退。
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

无外部模型密钥时，默认 `noop` / `hash` provider 仍可用于启动、接口联调和大部分测试。更多架构、接口与 eval 说明见 [docs/README.md](docs/README.md)。

## 已知限制

- 默认 LLM provider 为 `noop`，Embedding provider 为 `hash`；真实语义效果需配置 DashScope 并重建向量数据。
- `rerank` 当前是配置化扩展点与归因字段，默认关闭，不等同于已接入完整外部重排模型。
- MCP 工具调用默认关闭；主链路只支持显式工具意图、白名单与 schema 校验的受控子集。
- Eval 能力内置在本仓库，用于回归验证；完整多 target 调度、run/result/report 落库通常由外部评测服务承担。
- 简易前端用于本地演示，不是完整产品 UI。

## 后续计划

- 完善真实 rerank provider 与更稳定的检索质量评估。
- 补充 LLM 首包耗时、工具耗时、检索阶段耗时拆分。
- 将 eval run/report 的长期管理放到独立评测服务或更清晰的外部流程。
- 在真实数据量下评估 pgvector HNSW/IVFFLAT 索引与参数。
- 补充许可证与更多最小可运行示例。
