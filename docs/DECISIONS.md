# Vagent 与策划书 §3（Ragent 主链路）的差异说明

本文档记录 **Vagent 相对《Vagent 项目策划书》§3 参考实现拆解的刻意取舍**，便于评审与后续对齐；**不等于**对 Ragent 仓库的逐行对比。

---

## 1. 检索与生成

| §3 / Ragent 行为 | Vagent 当前实现 | 原因 / 后续 |
|------------------|-----------------|-------------|
| 真实 LLM 流式 | 默认 `noop` / `fake-stream`；**U1** 起可选 **`vagent.llm.provider=dashscope`**（通义千问兼容模式），见 [U1-实现说明.md](U1-实现说明.md) | 无密钥环境可启动；有 Key 时走真实 HTTP 流式。 |
| 真实 Embedding | 默认 `hash`；**U2** 起可选 **`vagent.embedding.provider=dashscope`**，向量默认 **1024 维**，见 [U2-实现说明.md](U2-实现说明.md) | 与 `vector(1024)` DDL 绑定；改维度须迁移并重嵌入。 |
| 检索为空时固定提示后结束，**不再调用大模型** | **U3** 起可选 **`vagent.rag.empty-hits-behavior=no-llm`**（不调 `LlmClient`，固定文案 + SSE `done`）；默认 **`allow-llm`** 仍为旧行为（未命中说明 + 继续调用 LLM），见 [U3-实现说明.md](U3-实现说明.md) | 产品可选对齐 §3 或保留常识作答。 |
| 检索引擎聚合 KB + MCP 等 | **U5**：主路用户隔离 + 可选第二路全表（`searchForRag`）。**U6**：MCP Client（HTTP）与 `/api/v1/mcp/*` 联调入口。**U7**：在 RAG 编排中引入“显式工具意图触发 + 工具白名单”，将工具结果并入 system prompt（不默认开启） | 以“默认不调用工具 + 白名单 + 超时降级”控制风险，避免 prompt 注入。 |
| 子问题拆分、多路检索合并 | **单 query**（经 M5 改写可为拼接文本） | 后续可扩展 `RewriteResult` 与子检索循环。 |

---

## 2. 理解阶段（改写 / 意图 / 引导）

| §3 | Vagent | 说明 |
|----|--------|------|
| `QueryRewriteService` LLM 改写 / 拆分子问题 | **规则**：透传或 **历史 USER 拼接** | 无额外模型调用、可测；可换实现类。 |
| `IntentResolver` 得分与多意图 | **前缀 + 长度规则** → `RAG` / `SYSTEM_DIALOG` / `CLARIFICATION` | 可替换为 `IntentResolutionService` 另一实现。 |
| 歧义时流式引导 | **CLARIFICATION** 分支：固定模板 SSE，**不调** `LlmClient` | 与「提前结束主回答」一致；文案可配置。 |

---

## 3. 记忆与数据

| §3 | Vagent |
|----|--------|
| 记忆服务抽象 + 摘要策略 | **`messages` 表** + `MessageService` 最近 N 条；**无**单独摘要表 |
| SYSTEM 与工具上下文入 Prompt | **SYSTEM** 仅 KB 或寒暄/未命中模板；**不落库** `messages` |

---

## 4. 可观测与部署

| §3 建议 | Vagent |
|---------|--------|
| 请求 ID、阶段耗时 | **U4**：MDC `traceId`（`TraceIdMdcFilter`）、日志 pattern；Micrometer `vagent.rag.retrieve`、`vagent.chat.stream`（tag `outcome`）；见 [U4-实现说明.md](U4-实现说明.md) |
| Docker Compose | **可选** `docker-compose.yml`（PostgreSQL + pgvector 镜像），见 README |

---

## 5. 修订

| 日期 | 说明 |
|------|------|
| 2026-04 | 初稿，对应 M6 文档交付 |
| 2026-04 | U3：`empty-hits-behavior` 可对齐 §3「空检索不调 LLM」 |
| 2026-04 | U4：traceId + 检索/流式 Timer；生产 DDL 仍建议 Flyway（见 U4 文档） |
| 2026-04 | U5：第二路全局向量默认关；跨租户见 [U5-实现说明.md](U5-实现说明.md) |
