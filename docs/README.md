# Vagent 文档导航

本文档帮助面试官或维护者快速判断：哪些内容是当前已实现，哪些是设计计划，哪些是面试准备或历史归档。

## 已实现说明

这些文档优先作为代码阅读入口。

| 文档 | 内容 |
| --- | --- |
| [Vagent-项目介绍.md](Vagent-项目介绍.md) | 项目总览、包结构、核心链路、接口与配置 |
| [DECISIONS.md](DECISIONS.md) | 与参考主链路的差异、取舍和边界 |
| [M0-实现说明.md](M0-实现说明.md) | Spring Boot 地基与 LLM 抽象 |
| [M1-实现说明.md](M1-实现说明.md) | 用户、JWT、会话 API |
| [M2-实现说明.md](M2-实现说明.md) | pgvector 知识库、嵌入、检索 API |
| [M3-实现说明.md](M3-实现说明.md) | SSE 流式输出与任务取消 |
| [M4-实现说明.md](M4-实现说明.md) | 多轮消息与 RAG 编排 |
| [M5-实现说明.md](M5-实现说明.md) | query rewrite、规则意图、澄清分支 |
| [M6-实现说明.md](M6-实现说明.md) | 测试、决策文档、可复现环境 |
| [U1-实现说明.md](U1-实现说明.md) | DashScope 流式对话 |
| [U2-实现说明.md](U2-实现说明.md) | DashScope embedding 与 1024 维向量 |
| [U3-实现说明.md](U3-实现说明.md) | 空检索策略：`allow-llm` / `no-llm` |
| [U4-实现说明.md](U4-实现说明.md) | traceId、Micrometer、Flyway 口径 |
| [U5-实现说明.md](U5-实现说明.md) | 第二路检索与合并 |
| [U6-实现说明.md](U6-实现说明.md) | MCP HTTP Client 与联调入口 |
| [U7-实现说明.md](U7-实现说明.md) | MCP 主链路受控调用：显式意图 + 白名单 |

## 设计计划

这些文档包含路线图、历史计划或更高阶目标。阅读时以“计划/约束/背景”为主，最终实现状态以源码、README 和已实现说明为准。

| 文档 | 内容 |
| --- | --- |
| [Vagent-项目策划书.md](Vagent-项目策划书.md) | 原始立项与里程碑设计 |
| [Vagent-升级策划书.md](Vagent-升级策划书.md) | M6 之后的升级路线与状态对照 |
| [工程折中与生产对照.md](工程折中与生产对照.md) | 当前工程实现与生产级方案的差异 |

更多评测、回归、hybrid/rerank A/B、CI 对接计划位于仓库根目录的 [plans/](../plans/) 与 [scripts/](../scripts/)。

## 面试准备/归档材料

| 文档 | 内容 |
| --- | --- |
| [面试准备.md](面试准备.md) | 项目口述稿、常见追问与回答边界 |

## 当前口径提醒

- Flyway 已引入，PostgreSQL 环境以 `src/main/resources/db/migration/` 为主要 DDL 来源。
- `empty-hits-behavior` 可配置：默认 `allow-llm`，`no-llm` 时空命中不调用 LLM。
- `rerank` 当前是配置与归因扩展点，默认关闭；未接入完整外部 rerank 供应商。
- MCP 与 eval 都是受控能力：默认关闭，适合演示和回归验证，不应包装成完整生产平台。
