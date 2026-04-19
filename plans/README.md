## 说明

本目录用于存放**后续规划/方案**，与现有 `docs/`（已实现功能的说明文档）**隔离**，避免混淆“现状”和“计划”。

### 仓库卫生（eval 导出物）

- **勿在仓库根目录提交** `eval_run_*.json`、`eval_compare*.json` / `eval_compare*.md`：已在根目录 **`.gitignore`** 排除；需要留证时写入 **`plans/datasets/artifacts/`**（该目录已忽略）或 CI / issue 附件，并登记 `dataset_id` / `run_id` 见 `plans/regression-baseline-convention.md`。
- **代码与 `plans/` 内策划**应随功能迭代 **正常 `git add` / commit**，避免「本机全绿、克隆无门」。
- **Eval 要真实 `answer` 而非占位**：见 `vagent.eval.api.full-answer-enabled`（默认关）；staging 联调可开，并配好 `vagent.llm.*`。

## 文档索引

## 最近更新（2026-04-18）

- **vagent-eval**（`D:\Projects\vagent-eval`）：已对 **`vagent`**、**`travel-ai`** 各跑通同一 **32 case**（`plans/datasets/p0-dataset-v0.jsonl`），产出 `run.report.v1` / `results` / `compare.v1`；状态与待补项见 **`plans/eval-upgrade.md`**「**vagent-eval 与双 target 联调状态**」及 **`plans/regression-baseline-convention.md`** §4 / §4.1。
- **travel-ai**（`travel-ai-planner`）评测口已实现 **eval-upgrade.md E7**（`meta.retrieval_hit_id_hashes[]` + `X-Eval-*` headers），全量 **32/32 PASS** 留证见 `travel-ai-upgrade.md`「可复现登记」、`regression-baseline-convention.md` §4.1 与 `travel-ai-planner/docs/DAY10_P0_CLOSURE.md`。

## 主文档（建议从这里读）

- **travel-ai 升级方案（Agent）**：见 `plans/travel-ai-upgrade.md`
- **Vagent 升级方案（RAG 编排/治理）**：见 `plans/vagent-upgrade.md`
- **eval 升级方案（统一评测回归）**：见 `plans/eval-upgrade.md`
- **P0 执行视图（落地改动地图 / MVP / 契约对齐）**：见 `plans/p0-execution-map.md`

## P0+ 执行手册与落地清单

- **P0+ 强制执行手册（门禁/冲刺/必交 artifacts）**：见 `plans/p0-plus/p0-plus-execution.md`
- **P0+ 日报模板与验收表**：见 `plans/p0-plus/p0-plus-daily-and-acceptance-tables.md`
- **P0+ B 线分支盘点（EvalChatController 出口枚举）**：见 `plans/p0-plus/p0-plus-b-s1d1-eval-chat-branch-inventory.md`
- **P0+ B 线响应样例**：见 `plans/p0-plus/p0-plus-b-s1d2-eval-chat-response-sample.json`

## 参考（可选）

- **外部参考：Spring AI RAG 模板摘录**（非本仓实现说明）：见 `plans/reference/reference-external-spring-ai-rag-blueprint.md`

