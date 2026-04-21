# 规划与回归文档（`plans/`）

本目录放**路线图、评测契约、基线登记与题库维护**材料，与 `docs/`（按功能模块写的实现说明）分开，避免把「计划」和「现状说明」混在一起。

## 主方案（阅读顺序）

| 文档 | 内容 |
|------|------|
| [`vagent-upgrade.md`](vagent-upgrade.md) | 本仓库 RAG 编排、门控、评测接口、引用闭环与后续路线（**主施工说明**） |
| [`eval-upgrade.md`](eval-upgrade.md) | 统一评测服务（dataset / run / report / compare）契约与联调说明 |
| [`travel-ai-upgrade.md`](travel-ai-upgrade.md) | 另一套 target（travel-ai）与评测对齐的专项目录 |

上述三个文件名以 `-upgrade` 结尾的文档为**长期规格锚点**；其它 `plans/` 下的文件应与之对齐或作为其附录。

## 回归与留证

| 文档 | 内容 |
|------|------|
| [`regression-baseline-convention.md`](regression-baseline-convention.md) | 基线登记字段、`dataset_id` / `run_id` 写法、与评测报告对齐的约定 |
| [`regression-compare-standard-runbook.md`](regression-compare-standard-runbook.md) | 标准 compare 流程与留证步骤 |
| [`eval-meta-trace-keys-vagent.md`](eval-meta-trace-keys-vagent.md) | compare 摘要里与 trace / meta 相关的键约定 |
| [报告与 `meta` 字段治理](regression-p1-report-governance.md) | 命名、落库、看板口径（文件名含历史阶段后缀，以文件内定义为准） |

## 题库与脚本

| 路径 | 内容 |
|------|------|
| [标准题集 JSONL](datasets/p0-dataset-v0.jsonl) | 与本仓库联调用题集；逻辑名与验收登记见 `vagent-upgrade.md` |
| [题集变更记录](datasets/p0-dataset-changelog.md) | 版本策略与每条变更说明 |
| [攻击面说明](datasets/p0-dataset-attack-suite.md)、[工具策略说明](datasets/p0-dataset-tool-policy.md)、[检索分桶说明](datasets/p0-dataset-rag-buckets.md) | 题集设计说明与分桶口径 |
| [`datasets/export-eval-run-failures.ps1`](datasets/export-eval-run-failures.ps1) | 从评测服务导出失败明细 |
| 仓库根目录 [分桶统计脚本](../scripts/summarize-p0-eval-buckets.ps1) | 对导出的 results 与题集 JSONL 做分桶统计（用法见 `vagent-upgrade.md`） |
| [混合检索与 rerank 的 A/B 对比](../scripts/README-hybrid-rerank-ab.md) | 同一 `dataset_id`、离线/在线 compare、`StrictContractGate` 契约门禁；可选 GitHub **`hybrid-ab-compare`** workflow（§5） |
| [评测知识库说明](../scripts/README-eval-kb.md) | 空库 / gold、混合检索对比入口 |
| [Quote-only 门控语义与档位](quote-only-guardrails.md) | `EvalQuoteOnlyGuard` + `vagent.guardrails.quote-only.*`（`strictness` + `scope`）+ 请求体 `quote_only`；末节 **「大白话速读」** 与 `requires_citations`/证据表复用同读 |
| [GitHub Actions 连真实 eval](ci-eval-github-actions.md) | 公网 / 自托管 runner / 隧道；`eval-remote.yml` + `scripts/ci-eval-remote.sh`（轮询参数、失败退出码、报告 artifact、`GITHUB_STEP_SUMMARY`） |
| [CI 评测烟测（双阶段 Maven）](eval-ci-smoke.md) | `eval-smoke` / `skip-eval-in-ci` profile 与 `ci.yml` 顺序 |

## 参考

| 文档 | 内容 |
|------|------|
| [`reference/reference-external-spring-ai-rag-blueprint.md`](reference/reference-external-spring-ai-rag-blueprint.md) | 外部 Spring AI RAG 模板摘录，非本仓实现说明 |

## 仓库卫生（评测导出物）

- **勿在仓库根目录提交** `eval_run_*.json`、`eval_compare*.json`、`eval_compare*.md`：已在根目录 `.gitignore` 排除。需要留证时放到 CI 附件、issue 或本地；登记 `dataset_id` / `run_id` 见 [`regression-baseline-convention.md`](regression-baseline-convention.md)。
- 大批量失败明细可写入 **`plans/datasets/artifacts/`**（若该目录在 `.gitignore` 中已忽略，则仅作本机缓存）。
- 功能与契约变更应随代码**正常提交**到 Git，避免「本机全绿、克隆无门」。

## 配置提示（与方案文档交叉引用）

- 评测接口可选生成完整 `answer`（相对占位 `"OK"`）：`vagent.eval.api.full-answer-enabled`（环境变量 `VAGENT_EVAL_API_FULL_ANSWER`），默认关闭以控成本与确定性；细节见 `vagent-upgrade.md`。
- 混合检索与 rerank 开关在 `application.yml` 的 `vagent.rag.hybrid` / `vagent.rag.rerank`；默认保守关闭，详见 `vagent-upgrade.md`。
