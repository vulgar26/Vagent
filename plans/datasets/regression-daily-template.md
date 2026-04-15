# 回归日报（模板）— Dataset / 回归 / 集成（D 线）

> **用法**：每天复制本模板为新文件 `regression-daily-YYYY-MM-DD.md`，只改「值」不改「字段名」。  
> **SSOT**：`plans/leadership-execution.md` §7.4、`plans/eval-upgrade.md`、`plans/p0-execution-map.md`。

---

## 元信息

- **日期**：YYYY-MM-DD  
- **TODAY_TOKEN**：`<YYYY-MM-DD>-D-D8`（或当日 Dn）  
- **汇报人**：  
- **dataset_version**：（与 `p0-dataset-changelog.md` 一致，如 `v0.1`）  
- **题库文件**：`plans/datasets/p0-dataset-v0.jsonl`（或注明路径）  
- **eval 基址**：`http://localhost:8099`（或实际环境）

---

## Run

- **run_id**：  
- **target_id**：`vagent` | `travel-ai`（或实际配置的 target）  
- **dataset_id**：  
- **run 状态**：`FINISHED` / `RUNNING` / …  
- **备注**：（如：eval 内存存储，重启后 run 丢失需重跑）

---

## run.report 摘要

- **total_cases**：  
- **completed_cases**：  
- **pass_rate**：  
- **skipped_rate**：  
- **p95_latency_ms**：  
- **p95_method**：（如 `nearest_rank_ceiling`）  
- **top_error_codes**：（从 `error_code_counts` 抄 TopN，或写「无 / 待报表」）

---

## P0 硬门槛（tool_policy）

- **规则**：仅 `tool_policy ∈ {stub, disabled}` 计入硬门槛；`real` **单列**，不计硬门槛。  
- **本 run 是否含 real case**：是 / 否  
- **硬门槛 pass_rate**：（若 eval 尚未分栏，写「与全量相同」或「待实现」）

---

## 分桶统计（可选）

> eval 若暂不支持按 tag 出报告，填「待增强」并跳过表格。

| 桶（tags） | case 数（dataset） | 通过率（若可得） | 备注 |
|------------|------------------|------------------|------|
| `attack/*` | | | |
| `rag/empty` | | | |
| `rag/low_conf` | | | |

---

## compare（base vs cand）

- **是否有 compare**：有 / **无**（首轮基线、单日仅一 run 时写 **无**）  
- **base_run_id**：  
- **cand_run_id**：  
- **regressions**：`[{ case_id, error_code, owner, next_action }]`  
- **improvements**：`[case_id, ...]`  

> **无 compare 时**：本节写「无 base run」，**失败处理见下节「失败分锅 / 维修表」**（对应 Day6）。

---

## 失败分锅 / 维修表

- **生成方式**：`plans/datasets/export-eval-run-failures.ps1 -RunId "..." -OutCsv "run_failures.csv"`  
- **附件或链接**：（粘贴 CSV 路径 / 飞书表格）  
- **按 error_code 聚类摘要**：（如 UNKNOWN ×N → owner A；CONTRACT ×M → owner C）

> 建议：将每次 run 导出的 CSV、环境快照（如 `env.json`）等产物统一放到 `plans/datasets/artifacts/`，文件名包含 `run_id`，避免与 SSOT 文档混放。

---

## 今日结论

- **是否接近 P0 门槛**：（是 / 否 / 部分）  
- **最大阻塞项**（1～3 条）：  
  1.  
  2.  
  3.  

---

## 明日动作（可选）

-  
