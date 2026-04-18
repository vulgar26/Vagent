# Vagent 项目介绍

本文档从**定位、架构、数据、接口、配置与里程碑**几方面系统介绍本仓库；与「按里程碑拆分的实现说明（M0–M6）」互为补充：**这里偏总览与串联，Mx 文档偏该阶段的细节与自测**。

---

## 1. 项目是什么

**Vagent** 是一个用 **Java / Spring Boot** 实现的、偏**企业工程化**风格的**对话式 RAG（检索增强生成）**示例项目：用户登录后拥有独立会话与知识库，可在会话内向量检索文档片段，并通过 **SSE** 流式获取模型回复（当前模型侧为可插拔实现，含 `noop` / `fake-stream` 等占位，便于无密钥环境跑通全链路）。

**它不追求**与某开源仓库逐文件对齐，而是对照《[Vagent-项目策划书.md](Vagent-项目策划书.md)》中对 **Ragent 主链路（§3）** 的能力拆解，在 Vagent 中**自研实现**可观察行为相近的一条流水线，便于学习与面试叙述。与参考实现的**刻意差异**集中写在 [DECISIONS.md](DECISIONS.md)。

---

## 2. 技术栈一览

| 类别 | 选型 | 说明 |
|------|------|------|
| 语言与构建 | Java 17、Maven | 与 `pom.xml` 一致 |
| 框架 | Spring Boot 3.3.x | Web、Actuator、Validation |
| 安全 | Spring Security + JWT | 无 Session，API 带 `Authorization: Bearer` |
| 持久化 | MyBatis-Plus、PostgreSQL | 业务表 + 向量扩展 |
| 向量检索 | pgvector | 文档分块嵌入后写入，检索时同用户隔离 |
| 嵌入 | `hash`（可复现）或 **`dashscope`**（U2，`text-embedding-v3`×**1024 维**） | 维度须与 `vector(1024)` 一致；见 [U2-实现说明.md](U2-实现说明.md) |
| 流式输出 | `SseEmitter` | 事件体为 JSON，`type` 区分 meta/chunk/done 等 |
| 大模型（U1） | `noop` / `fake-stream` / **`dashscope`**（通义千问兼容 HTTP 流式） | 见 `LlmClientConfiguration`、 [U1-实现说明.md](U1-实现说明.md) |
| MCP（U6） | HTTP JSON-RPC（Streamable HTTP 的 JSON-only 响应模式） | Vagent 作为 **MCP Client** 联调独立进程 Server；见 [U6-实现说明.md](U6-实现说明.md) |
| 测试 | JUnit 5、Spring Boot Test、H2（PostgreSQL 模式） | 默认不测真实向量；pgvector 有单独集成测试类 |

---

## 3. 源码包结构（`com.vagent`）

按**领域**划分，便于对照阅读：

| 包路径 | 职责 |
|--------|------|
| `auth` | 注册、登录，签发 JWT |
| `security` | `JwtAuthenticationFilter`、`SecurityConfig`、`JwtService`、登录态主体 |
| `user` | 用户实体与 Mapper、用户 id 格式（紧凑 UUID 字符串） |
| `conversation` | 会话 CRUD、归属校验（`findOwnedByUser`） |
| `chat` | 流式对话入口（`StreamChatController`）、`StreamChatService`、**RAG 编排**（`RagStreamChatService`）、SSE 与任务（`LlmSseStreamingBridge`、`LlmStreamTaskRegistry`）、`message` 子包（多轮落库） |
| `rag`（在 `chat.rag`） | `RagProperties`：`vagent.rag.*` |
| `kb` | 知识库入库、检索 API、分块、向量 Mapper 与 TypeHandler |
| `embedding` | 嵌入客户端接口与配置（如 hash 实现） |
| `llm` | `LlmClient` / `LlmChatRequest` / `LlmMessage` / `LlmStreamSink`；`impl` 下 `noop`、`fake-stream` |
| `orchestration` | M5：**检索前改写**、**规则意图**（`RAG` / `SYSTEM_DIALOG` / `CLARIFICATION`） |
| `mcp` | U6：MCP Client（HTTP）与联调 API（`/api/v1/mcp/*`）；当前不直接并入 RAG 主链路 |
| `api` | 全局异常处理等横切 |

**编排 heart**：`StreamChatService` 根据 `vagent.rag.enabled` 决定走 **M3 单条 USER** 还是 **`RagStreamChatService` 全链路**。

---

## 4. 核心主链路（RAG 开启时）

下面描述 **`vagent.rag.enabled=true`** 且走 **`RagStreamChatService`** 时的逻辑顺序（Servlet 线程与异步线程分工与 [M4-实现说明.md](M4-实现说明.md)、[M5-实现说明.md](M5-实现说明.md) 一致）。

1. **鉴权**：JWT 解析出 `userId`；校验 `conversationId` 属于该用户。  
2. **读历史**：从 `messages` 表取该会话最近 N 条 **USER/ASSISTANT**（不含本轮尚未插入的句子）。  
3. **落库本轮用户**：插入一条 **USER** 行。  
4. **改写（M5）**：得到 **`retrievalQuery`**（可透传本轮，或拼接历史 USER 供向量检索）。  
5. **意图（M5，可关）**：  
   - **CLARIFICATION**：不调检索、不调 `LlmClient`，SSE 推送固定引导文案并落库 **ASSISTANT**。  
   - **SYSTEM_DIALOG**：不调检索，专用 SYSTEM + 历史 + 本轮，仍走 `LlmClient`。  
   - **RAG**：用 **`retrievalQuery`** 做向量检索。若 **0 条**且 **`vagent.rag.empty-hits-behavior=no-llm`**（U3），不调 `LlmClient`，仅推送固定 **`chunk`** 与 **`done`** 并落库 **ASSISTANT**；否则（含默认 **`allow-llm`**）将命中片段或「未命中」说明写入 **SYSTEM**，再拼 **历史 + 本轮 USER**，走 `LlmClient`。  
6. **任务与 SSE**：注册 `taskId`，`SseEmitter` 返回客户端；在**线程池**中先发 **`meta`**（含 `taskId`、`hitCount`、`branch`），再走 **`LlmSseStreamingBridge`** 将模型流映射为 **`chunk`**（U3 空命中 `no-llm` 路径不经 Bridge），正常结束时 **`done`** 并**回调**写入 **ASSISTANT**。  
7. **取消**：`POST .../chat/tasks/{taskId}/cancel` 校验属主后标记取消；Bridge 与 `LlmChatRequest` 上的取消语义结合，通常不再执行「成功落助手」的回调。

**要点**：**SYSTEM（含知识或寒暄/未命中说明）不落 `messages` 表**；表里只持久化双方**真实发言**（USER/ASSISTANT）。

---

## 5. 数据模型（关系库）

| 表 / 概念 | 说明 |
|-----------|------|
| `users` | 用户账号 |
| `conversations` | 会话，外键到用户 |
| `messages` | 会话内多轮消息，**仅 USER/ASSISTANT**；删会话级联删消息 |
| `kb_documents` / `kb_chunks` | 知识库文档与分块；向量列与 `pgvector` 一致；检索带 **用户隔离** |

DDL 入口：`src/main/resources/schema-core.sql`（核心业务）、`schema-vector.sql`（扩展与向量表）。测试 profile 通常**只加载** `schema-core.sql`，并关闭 RAG，避免 H2 缺向量表。

---

## 6. 主要 HTTP API（前缀 `/api/v1`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register`、`/auth/login` | 注册与登录，返回 `token` |
| GET | `/conversations` | 列表（需 Bearer） |
| POST | `/conversations` | 创建会话 |
| DELETE | `/conversations/{id}` | 删除本会话（级联删 `messages`；并标记取消本会话下进行中的 SSE 任务） |
| POST | `/kb/documents` | 文档入库（分块+嵌入） |
| POST | `/kb/retrieve` | 仅检索，非流式对话 |
| POST | `/conversations/{id}/chat/stream` | **SSE 流式对话**（RAG 编排入口之一） |
| POST | `/chat/tasks/{taskId}/cancel` | 取消流式任务 |
| GET | `/mcp/tools` | U6：列出 MCP 工具（联调入口） |
| POST | `/mcp/tools/{name}` | U6：调用 MCP 工具（联调入口，body 为 `arguments` JSON object） |

详细请求体与演示步骤见根目录 [README.md](../README.md)。

---

## 7. 配置项索引（`application.yml`）

| 前缀 | 作用 |
|------|------|
| `vagent.rag.*` | 是否启用 RAG、检索 topK、历史条数上限；**U3** `empty-hits-behavior`；**U5** `second-path.*`（默认关） |
| `vagent.orchestration.*` | 意图开关、改写策略、寒暄前缀、澄清模板等（M5） |
| `vagent.llm.*` | 模型提供方（`noop` / `fake-stream` / **`dashscope`**）、默认模型名、假流式参数 |
| `vagent.llm.dashscope.*` | U1：兼容模式基址、API Key、对话模型（仅 `provider=dashscope` 时生效） |
| `vagent.embedding.*` | 嵌入实现（`hash`/`dashscope`）与维度（默认 **1024**）、分块长度 |
| `vagent.embedding.dashscope.*` | U2：嵌入 API 基址、Key、模型名 |
| `vagent.mcp.*` | U6：MCP Client（HTTP）开关、base url、token、协议版本；以及联调 API 行为 |
| `vagent.security.jwt.*` | JWT 密钥与过期时间 |

生产环境务必将密钥改为环境变量或外部配置，**勿**提交真实密钥。

---

## 8. 测试与质量

- **默认 `mvn test`**：H2 + `test` profile，覆盖认证、会话、消息持久化、编排单测等；**不**默认跑需 Docker 的 pgvector 集成测试。  
- **专项**：`M2KnowledgeVectorIntegrationTest` 需本机 Docker，由 Surefire 默认排除，按需 `-Dtest=...` 运行。  
- **Bridge / 任务注册表**：`LlmSseStreamingBridgeTest`、`LlmStreamTaskRegistryTest` 固定取消与 done 回调语义。

---

## 9. 里程碑与文档地图

| 文档 | 内容 |
|------|------|
| [Vagent-项目策划书.md](Vagent-项目策划书.md) | 立项、§3 参考拆解、里程碑表、交付物 |
| [DECISIONS.md](DECISIONS.md) | 与 §3 的差异及原因 |
| [M0-实现说明.md](M0-实现说明.md) ~ [M6-实现说明.md](M6-实现说明.md) | 各阶段实现与自测 |
| [面试准备.md](面试准备.md) | 口述架构与问答（个人向） |

**M0–M6 一句话**：骨架与 LLM 接口 → 用户/会话/JWT → pgvector 与 KB API → SSE 与取消 → 多轮消息与 RAG 编排 → 改写与意图分支 → 单测/DECISIONS/Compose 与文档收尾。

**U1（升级）**：通义千问 DashScope OpenAI 兼容流式，见 [U1-实现说明.md](U1-实现说明.md) 与 [Vagent-升级策划书.md](Vagent-升级策划书.md)。

**U2（升级）**：通义千问兼容嵌入、**1024** 维向量表，见 [U2-实现说明.md](U2-实现说明.md)。

**U3（升级）**：空检索是否调 LLM（`empty-hits-behavior`），见 [U3-实现说明.md](U3-实现说明.md)。

**U4（升级）**：MDC `traceId`、检索与 LLM 流式 Micrometer 指标，见 [U4-实现说明.md](U4-实现说明.md)。

**U5（升级）**：第二路全局向量召回与主路合并，见 [U5-实现说明.md](U5-实现说明.md)。

---

## 10. 后续可演进方向（非承诺）

- 真实厂商 **流式 HTTP** `LlmClient`、密钥与超时配置化。  
- **U4** 已提供 traceId 与基础 Timer；**Trace 落库 / 多路检索 / MCP** 等见 [Vagent-升级策划书.md](Vagent-升级策划书.md) U5+。  
- **LLM 改写 / 子问题拆分** 替换当前规则实现。  
- 请求追踪、各阶段耗时指标（Micrometer / Trace）。  
- Flyway/Liquibase 替代 `spring.sql.init.mode=always`。

---

若你维护本仓库，建议**更新本文档**的时机：新增对外 API、改变主链路顺序、或调整与 §3 的对齐策略时，同步改一节并指向 [DECISIONS.md](DECISIONS.md)。
