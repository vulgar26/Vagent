# Vagent 升级策划书（对齐 Ragent）

| 文档版本 | 日期 | 说明 |
|----------|------|------|
| v0.1 | 2026-04-04 | 初稿：承接 M0–M6 交付后的演进路线；默认大模型 API 为**阿里云通义千问（DashScope）** |

---

## 1. 文档说明

**本文档回答的问题**：在 [Vagent-项目策划书.md](Vagent-项目策划书.md) 与 [DECISIONS.md](DECISIONS.md) 所界定能力之上，如何**分阶段向 Ragent 靠拢**；技术选型（**聊天 / 嵌入：千问**）、里程碑、验收标准、待与你确认的细节。

**与主策划书的关系**：主策划书仍是立项与 **§3 主链路规格** 的基线；**本文件专门描述 M6 之后的升级迭代**，不替代 `DECISIONS.md` 中已记录的「刻意简化」，但会标注**哪些差异计划在后续版本收敛**。

**参考仓库**：Ragent（[nageoffer/ragent](https://github.com/nageoffer/ragent)）；对照说明亦可参考其文档中的主链路、多通道检索、`infra-ai` 模型层等（不要求逐模块同名）。

---

## 2. 目标与原则

### 2.1 总体目标

| 目标 | 说明 |
|------|------|
| **可演示** | 使用**真实流式大模型**（千问）完成端到端 RAG 对话，替代仅 `noop` / `fake-stream` 的占位实现。 |
| **可对齐** | 在可控成本下，逐项缩小与 Ragent 的差异（见 §4 对照表）。 |
| **可演进** | 保持现有包结构与 `LlmClient` / `EmbeddingClient` 抽象，新能力以**新增实现类 + 配置切换**为主，避免推倒重来。 |

### 2.2 原则

- **配置与密钥分离**：API Key 仅环境变量或受管控的配置注入，不入库、不进 Git。
- **测试仍可无网**：保留 `noop` / `fake-stream` / `HashEmbeddingClient` 作为 CI 与本地默认 profile。
- **差异有文档**：每收敛一项 `DECISIONS.md` 中的偏差，同步更新该文件或本文件「修订记录」。

---

## 3. 大模型与嵌入：通义千问（DashScope）

> 你已申请千问额度，下列项为常见接入方式，**以阿里云控制台与官方文档为准**；实现时以当时有效的兼容接口为准。

### 3.1 选型说明

| 能力 | 建议 | 说明 |
|------|------|------|
| **对话（流式）** | OpenAI **兼容模式** Chat Completions | 路径通常为 `https://dashscope.aliyuncs.com/compatible-mode/v1`，便于在 Vagent 内实现单一 **HTTP + SSE** 客户端，与 Ragent 侧「多厂商 OpenAI 风格」思路一致。 |
| **文本嵌入（可选升级）** | DashScope **Embeddings API** | 用于替换 `HashEmbeddingClient`，提升检索语义质量；需与 `kb_chunks` 的 **向量维度**一致（见 §3.3）。 |
| **重排序（远期）** | 与 Ragent 类似可接独立 Rerank 或云侧能力 | 非 U1–U2 必选项。 |

### 3.2 配置项（建议形态，实现时落 `application.yml`）

| 配置键（示例） | 含义 |
|----------------|------|
| `vagent.llm.provider` | 增加枚举值，如 `dashscope` 或 `qwen-compatible` |
| `vagent.llm.dashscope.base-url` | 默认 `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `vagent.llm.dashscope.api-key` | **引用** `DASHSCOPE_API_KEY` 或 `${DASHSCOPE_API_KEY}` |
| `vagent.llm.dashscope.chat-model` | 如 `qwen-turbo`、`qwen-plus` 等（以控制台可用名为准） |

### 3.3 嵌入维度（已落地 U2）

**结论**：默认采用 **`text-embedding-v3` + `dimensions=1024`**，与 `kb_chunks.embedding vector(1024)`、`vagent.embedding.dimensions` 对齐；请求体显式传 `dimensions`，避免与模型默认不一致。

若将来改为其他维度，须同步修改 **`schema-vector.sql`、`KbChunkMapper` 中 `CAST(... AS vector(N))`**、配置并重嵌入（见 [U2-实现说明.md](U2-实现说明.md)）。

### 3.4 流式与取消

- 兼容模式下行协议多为 **SSE**（`text/event-stream`）或分块 JSON，需接入现有 `LlmSseStreamingBridge` / `LlmClient#stream` 抽象。  
- **取消**：将 HTTP 客户端与 `LlmChatRequest` 上的取消信号打通（与现有 `taskId` / `LlmStreamTaskRegistry` 一致），行为对齐 M3 已有语义。

---

## 4. 与 Ragent 能力对照（升级路线图）

| Ragent 能力 | Vagent 当前（M6） | 建议升级阶段 | 备注 |
|-------------|-------------------|--------------|------|
| 真实 LLM 流式 | `noop` / `fake-stream` | **U1** | 千问兼容客户端 |
| 真实 Embedding | 默认仍可选 `hash`；**U2** 已实现 `dashscope` | **U2** | `vector(1024)` + [U2-实现说明.md](U2-实现说明.md) |
| 空检索不调 LLM | **U3** 起可 `no-llm`；默认 `allow-llm`（见 DECISIONS） | **U3** | [U3-实现说明.md](U3-实现说明.md) |
| 多通道检索 + 后处理 | **U5** 第二路全局向量 + 合并（可关） | **U5** | [U5-实现说明.md](U5-实现说明.md) |
| 模型路由 / 健康度 / 首包 | 无 | **U5**（可选） | 对齐 `infra-ai` 思想，非单应用必需 |
| MCP 工具 | 无 | **U6**（远期） | 独立进程或进程内注册表 |
| Trace 落库与查询 API | **U4** 已实现 MDC + Micrometer；**落库查询 API** 仍无 | **U4+** | [U4-实现说明.md](U4-实现说明.md) |

---

## 5. 分阶段里程碑（U 系列）

### U1：通义千问流式对话（必做）

**状态（实现）**：已完成，见源码 `DashScopeCompatibleStreamingLlmClient`、配置 `vagent.llm.dashscope.*`、文档 [U1-实现说明.md](U1-实现说明.md)。

**交付物**

- 新 `LlmClient` 实现：调用 DashScope 兼容接口，**流式**解析并写入现有 `LlmStreamSink`。  
- `application.yml` + 环境变量说明；README 增加「千问演示」步骤。  
- 单测：SSE JSON 行解析（`DashScopeOpenAiStreamParserTest`）；默认 CI 仍不依赖外网。

**验收**

- 登录 → 入库 → `chat/stream` 可见**真实模型**输出；`cancel` 仍可用（在可取消范围内）。

### U2：千问嵌入 + 向量表一致（推荐）

**状态（实现）**：已完成，见 `DashScopeEmbeddingClient`、`EmbeddingDashScopeProperties`、`vector(1024)` DDL、[U2-实现说明.md](U2-实现说明.md)。

**交付物**

- `EmbeddingClient` 新实现；入库与检索走同一模型与维度。  
- DDL 与 `KbChunkMapper` 同步为 1024；文档说明从 128 升级的破坏性变更。

**验收**

- `M2KnowledgeVectorIntegrationTest` 在 **Testcontainers PG+vector** 下使用 **hash + 1024 维** 可跑；启用 `dashscope` 时需 Key 与网络。

### U3：对齐策划书 §3「空检索」行为（可选但推荐）

**状态（实现）**：已完成，见 `RagProperties#emptyHitsBehavior`、`RagStreamChatService` 中 U3 分支、[U3-实现说明.md](U3-实现说明.md)。

**交付物**

- 配置项：`vagent.rag.empty-hits-behavior: no-llm | allow-llm`（默认 `allow-llm`）；可选 `empty-hits-no-llm-message`。  
- `no-llm`：固定提示 + SSE `done`，与 Ragent / §3 一致。  
- 更新 `DECISIONS.md` 对应行。

**验收**

- `RagPropertiesEmptyHitsBehaviorTest` 校验 `no-llm` / `allow-llm` 绑定；`allow-llm` 为默认兼容旧版。

### U4：可观测与工程化

**状态（实现）**：已完成，见 `TraceIdMdcFilter`、`MdcTaskDecorator`、`KnowledgeRetrieveService` / `LlmSseStreamingBridge` 中 Timer、[U4-实现说明.md](U4-实现说明.md)。

**交付物**

- 请求级 **traceId**（MDC），响应头 `X-Trace-Id`；异步流式线程继承 MDC。  
- **Micrometer**：`vagent.rag.retrieve`、`vagent.chat.stream`（`outcome` 标签）。  
- **Flyway / Liquibase**：生产建议，文档说明；本仓库仍用 `spring.sql.init` 便于开发。

**验收**

- `TraceIdMdcFilterTest`；`GET /actuator/metrics` 可见上述指标名（需暴露 `metrics`）。

### U5：多路检索（简化版 Ragent）

**状态（实现）**：已完成，见 `KnowledgeRetrieveService#searchForRag`、`KbChunkMapper#searchNearestGlobal`、`RetrieveHitMerge`、[U5-实现说明.md](U5-实现说明.md)。

**交付物**

- 在「无命中 / 主路命中偏少 / 检索 query 过短」时可触发**第二路**全表向量召回；**去重 / 合并**后截断为 `top-k`。  
- 默认关闭（`second-path.enabled` / `cross-tenant`）；`GET /kb/retrieve` 仍仅主路。

**验收**

- `RetrieveHitMergeTest`；配置开启后对话流可合并 `primary` 与 `global` 片段。

### U6：MCP 或工具协议（远期）

**交付物**

- 与 Ragent `RetrievalEngine` 中 MCP 上下文类似的能力，规模可缩小为 1～2 个示例工具。

**状态（实现，最小）**：已完成（Vagent 作为 MCP Client，HTTP 调用独立 Python Server），见 [U6-实现说明.md](U6-实现说明.md)。

### U7：MCP 工具并入主链路（编排闭环，规则触发）

**目标**

- 将 MCP 从“联调入口”升级为可用于主对话链路的能力：在 **RAG 编排**中按规则/意图触发工具调用，并将结果并入 Prompt 上下文（对齐策划书 §3 的“检索聚合 KB + 工具侧上下文”表述）。

**范围（第一版，面试/演示友好）**

- **触发策略（B）**：仅当意图/规则命中时才允许工具调用；默认不调用工具。
- **工具白名单**：仅允许配置白名单内的工具；即使 LLM/提示词诱导，也会在服务端强校验拦截。
- **上下文注入**：工具结果以结构化片段加入 Prompt（与 KB 命中区分），并在 `meta` 中标记“是否使用工具/使用了哪些工具”。
- **超时与降级（最小生产化）**：工具调用有超时；失败时降级为“仅 KB RAG”或“无工具继续回答”，并记录指标与日志。

**验收**

- 无工具触发：行为与当前完全一致（回归不破坏）。
- 工具触发：可在一次对话中看到“工具结果进入上下文 → LLM 使用该信息回答”的可演示闭环。
- 指标：`vagent.mcp.call` 能按 tool/outcome 观察耗时与失败；主链路 `traceId` 可串起一次工具调用。

---

## 6. 风险与应对

| 风险 | 应对 |
|------|------|
| 千问接口变更 | 封装在单一 `LlmClient` 实现内；版本锁定与集成测试 |
| Embedding 维度与历史数据 | U2 前明确模型；提供迁移或清空说明 |
| 成本 | 开发环境限流、小模型、低 `topK`；Key 分级 |
| 范围蔓延 | 严格按 U1→U2…顺序；U4+ 可拆迭代 |

---

## 7. 交付物清单（升级迭代）

- 千问流式客户端与配置文档  
- （可选）千问嵌入与 DDL 说明  
- 更新后的 `DECISIONS.md`、README、`docs/M*-实现说明.md` 补篇（按需）  
- 本文件修订记录  

---

## 8. 待与你确认的细节（可逐项拍板）

下列问题不影响 U1 文档定稿，但实现前建议确认：

1. **兼容模式 Base URL**：是否使用官方 **OpenAI 兼容** 端点；是否需走企业内网代理。  
2. **默认对话模型**：`qwen-turbo` / `qwen-plus` / 其他（成本与效果权衡）。  
3. **U2 是否必做**：若暂不做真嵌入，是否接受检索仍以 Hash 为主、仅聊天接真模型。  
4. **向量维度**：若上 text-embedding-v3（或 v2），是否接受 **重建** `kb_chunks` 或接受一次性迁移窗口。  
5. **合规**：密钥是否仅用环境变量、是否需审计日志脱敏。  

确认后可把结论简短写入 `DECISIONS.md` 或本文件修订记录。

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-04-04 | 初稿：对齐路线、千问 API 规划、U1–U6 里程碑、待确认项 |
