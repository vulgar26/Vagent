# 混合检索与重排：同一题集下的开关对比（A/B）

目标：在**冻结的同一 `dataset_id`**、同一评测环境、同一 `target_id=vagent` 下，对比「关 hybrid / 开 hybrid /（可选）开 rerank」等配置跑出的两次（或多次）`run_id`，并用脚本做 verdict 差分；**门禁**为：不出现**契约类**回归（见下文）。

实现与开关说明见仓库根目录 `plans/vagent-upgrade.md` 中「混合检索 + 可选重排」一节；应用配置键为 `vagent.rag.hybrid.*`、`vagent.rag.rerank.*`（默认全关）。当前工程内 **rerank 供应商未接入** 时，`rerank_outcome` 多为 `skipped`，对比重点在 **hybrid 开关** 即可。

---

## 1. 前置

| 项 | 说明 |
|----|------|
| 题集 | 使用已在评测服务中导入的冻结 `dataset_id`（登记见 `plans/regression-baseline-convention.md`）。 |
| 知识库 | 若要对齐「空库 / gold」口径，见 `scripts/README-eval-kb.md`。 |
| 评测服务 | 能创建 run、拉取 `report`/`results`（与 `plans/eval-upgrade.md` 一致）。 |
| Vagent | eval 配置的 `base-url` 可达；`X-Eval-Token` 与 `vagent.eval.api` 一致。 |

---

## 2. 推荐流程（同一 `dataset_id`）

### A. 基线 run（hybrid 关、rerank 关）

1. Vagent 使用保守配置启动（或 `application-local.yml` 不显式打开 hybrid/rerank，与 `application.yml` 默认一致）。  
2. 在评测服务上对冻结 **`dataset_id`**、`vagent` 执行一轮完整 run。  
3. 记录 **`run_id` → `base_run`**；保存 `GET .../runs/{id}/report` 摘要。

### B. 候选 run（hybrid 开）

1. 在 `application-local.yml` 中打开 hybrid（例如 `lexical-mode: tsvector` 或 `ilike`，按环境选择），**其余尽量不变**；重启 Vagent。  
2. **同一 `dataset_id`** 再跑一轮 run。  
3. 记录 **`run_id` → `cand_run`**；保存 report。

### C.（可选）第三组：hybrid 开 + `rerank.enabled=true`

用于验证「打开 rerank 开关」时主路径仍降级正常（当前实现可能仍为 `rerank_outcome=skipped`）。与 B 的对比方式相同。

### D. 差分与门禁

在仓库根目录执行（将 URL 与 run id 换成实际值）：

```powershell
.\scripts\compare-eval-runs.ps1 `
  -EvalBase "http://localhost:9090" `
  -BaseRunId "run_..." `
  -CandRunId "run_..." `
  -RequireSameDataset `
  -StrictContractGate `
  -OutDir "plans/datasets/artifacts"
```

说明：

- **`-RequireSameDataset`**：在线拉取 `GET .../api/v1/eval/runs/{id}`，解析 `dataset_id`；不一致或缺失（在要求严格时）直接失败。  
- **`-StrictContractGate`**：若存在「基线 PASS → 候选非 PASS」且候选 **`error_code`** 属于契约集合（`CONTRACT_VIOLATION`、`PARSE_ERROR`、`SOURCE_NOT_IN_HITS`、`SECURITY_BOUNDARY_VIOLATION`），脚本 **`exit 1`**，便于 CI 引用。  
- 产物：`eval_compare_<base>_vs_<cand>.json` / `.md`（输出目录勿提交仓库根时可改用已忽略的 `artifacts`）。

离线对比（两段已导出的 `results` JSON）：

```powershell
.\scripts\compare-eval-results-files.ps1 `
  -BaseResultsJson ".\base_results.json" `
  -CandResultsJson ".\cand_results.json" `
  -StrictContractGate
```

契约子集逻辑与在线脚本一致（见 `scripts/eval-compare-contract.ps1`）。

---

## 3. 如何读结果

- **总回归**：`regressions`（PASS → 任意非 PASS）需人工 triage（质量波动、题集噪声等）。  
- **门禁重点**：`contract_regressions` 应为空；非空表示引用闭环、解析或安全边界类问题，**优先回退或修配置/代码**。  
- **检索归因**：对比同一 `case_id` 两侧 `meta` 中的 `hybrid_*`、`rerank_*` 与距离分桶（若 eval 落库了完整 `meta`），解释「通过率不变但候选集变化」类现象。

---

## 4. 相关脚本与文档

| 路径 | 用途 |
|------|------|
| `scripts/compare-eval-runs.ps1` | 在线拉取两 run 的 results + report，生成 compare JSON/MD，可选 dataset 校验与契约门禁。 |
| `scripts/compare-eval-results-files.ps1` | 离线两段 results 文件对比。 |
| `scripts/eval-compare-contract.ps1` | 被上述脚本点源；契约 `error_code` 集合的单点维护。 |
| `plans/regression-compare-standard-runbook.md` | 通用 compare 留证流程。 |
