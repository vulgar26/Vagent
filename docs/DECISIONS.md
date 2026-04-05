# Vagent 与策划书 §3（Ragent 主链路）的差异说明

本文档记录 **Vagent 相对《Vagent 项目策划书》§3 参考实现拆解的刻意取舍**，便于评审与后续对齐；**不等于**对 Ragent 仓库的逐行对比。

---

## 1. 检索与生成

| §3 / Ragent 行为 | Vagent 当前实现 | 原因 / 后续 |
|------------------|-----------------|-------------|
| 检索为空时固定提示后结束，**不再调用大模型** | 检索为空时仍构造含「未命中说明」的 SYSTEM，**继续调用** `LlmClient` | 降低首版分支复杂度；便于依赖历史与常识作答。可通过配置开关在后续版本对齐 §3。 |
| 检索引擎聚合 KB + MCP 等 | 仅 **pgvector + `KnowledgeRetrieveService`** | MVP 单通道；工具协议进 Backlog。 |
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
| 请求 ID、阶段耗时 | **未**统一埋点；可用日志与后续 Micrometer 扩展 |
| Docker Compose | **可选** `docker-compose.yml`（PostgreSQL + pgvector 镜像），见 README |

---

## 5. 修订

| 日期 | 说明 |
|------|------|
| 2026-04 | 初稿，对应 M6 文档交付 |
