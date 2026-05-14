# Vagent 文档导航

本目录只保留面试官快速判断项目质量需要看的文档。阶段总结、升级计划、P0/P1 开发记录和个人面试准备材料已归档到 `docs/archive/`。

## 推荐阅读

| 文档 | 内容 |
| --- | --- |
| [Vagent-项目介绍.md](Vagent-项目介绍.md) | 项目总览、包结构、核心链路、数据模型、接口与配置 |
| [DECISIONS.md](DECISIONS.md) | 关键取舍、能力边界、哪些默认关闭 |

## Eval / Evidence

| 文档 | 内容 |
| --- | --- |
| [scripts/README-eval-kb.md](../scripts/README-eval-kb.md) | eval 知识库准备、固定题集联调、工具题策略 |
| [scripts/README-hybrid-rerank-ab.md](../scripts/README-hybrid-rerank-ab.md) | hybrid / rerank A/B 对比与 compare 脚本 |
| [docs/archive/plans/quote-only-guardrails.md](archive/plans/quote-only-guardrails.md) | quote-only、evidence map、引用约束的细节口径 |

## 归档

| 目录 | 内容 |
| --- | --- |
| [archive/implementation/](archive/implementation/) | M0-M6、U1-U7 阶段实现说明 |
| [archive/plans/](archive/plans/) | 升级计划、P0/P1、回归评测、CI 对接、历史策划材料 |
| [archive/interview/](archive/interview/) | 个人面试准备稿 |

## 当前口径提醒

- Flyway 已引入，PostgreSQL 环境以 `src/main/resources/db/migration/` 为主要 DDL 来源。
- `empty-hits-behavior` 可配置：默认 `allow-llm`，`no-llm` 时空命中不调用 LLM。
- `rerank` 当前是配置与归因扩展点，默认关闭；未接入完整外部 rerank 供应商。
- MCP 与 eval 都是受控能力：默认关闭，适合演示和回归验证，不应包装成完整生产平台。
