# 回归日报 — 2026-04-11（示例满填，Day8 证据）

> 本文件为 **按 `regression-daily-template.md` 填写的样例**；数字来自一次 **travel-ai + 32 case** 基线描述（`run_id` 请与你们环境一致时可替换）。

---

## 元信息

- **日期**：2026-04-11  
- **TODAY_TOKEN**：`2026-04-11-D-D8`  
- **汇报人**：（实习生 / D 线负责人）  
- **dataset_version**：`v0.1`  
- **题库文件**：`plans/datasets/p0-dataset-v0.jsonl`  
- **eval 基址**：`http://localhost:8099`

---

## Run

- **run_id**：`run_9cc08ae16c4d4269a9e67959d593a47c`（**示例**；以你们当时实际为准）  
- **target_id**：`travel-ai`  
- **dataset_id**：（创建 dataset 时返回的 `ds_...`，此处略）  
- **run 状态**：`FINISHED`  
- **备注**：eval 为内存存储时，进程重启后需用新 `run_id` 重跑并更新本节。

---

## run.report 摘要

- **total_cases**：32  
- **completed_cases**：32  
- **pass_rate**：0.1875  
- **skipped_rate**：0.0  
- **p95_latency_ms**：32  
- **p95_method**：`nearest_rank_ceiling`  
- **top_error_codes**：`UNKNOWN × 21`，`CONTRACT_VIOLATION × 5`（其余见明细 results）

---

## P0 硬门槛（tool_policy）

- **规则**：仅 `stub|disabled` 计入硬门槛；`real` 单列。  
- **本 run 是否含 real case**：**否**（当前 v0.1 无 `real`）  
- **硬门槛 pass_rate**：与全量 **相同**（32 条均为 stub/disabled）；**待 eval 报表分栏**后可拆列展示。

---

## 分桶统计（可选）

| 桶（tags） | case 数（dataset） | 通过率（若可得） | 备注 |
|------------|------------------|------------------|------|
| `attack/*` | 12 | 待从 report 拆 | eval 侧按 tag 报表待增强 |
| `rag/empty` | 3 | 待拆 | |
| `rag/low_conf` | 2 | 待拆 | |

---

## compare（base vs cand）

- **是否有 compare**：**无**（首轮基线，仅单次 run）  
- **base_run_id**：—  
- **cand_run_id**：—  
- **regressions**：—  
- **improvements**：—  

**说明**：无 compare 时，**不称本表为 regression 列表**；失败项见下节 **Day6 维修表**。

---

## 失败分锅 / 维修表

- **生成方式**：  
  `.\export-eval-run-failures.ps1 -RunId "run_9cc08ae16c4d4269a9e67959d593a47c" -EvalBase "http://localhost:8099" -OutCsv ".\run_failures-2026-04-11.csv"`  
- **附件**：`run_failures-2026-04-11.csv`（运行脚本后生成）  
- **按 error_code 聚类摘要**：  
  - `UNKNOWN`（21）→ **owner A（eval）**：收敛 `RunEvaluator`，减少兜底 UNKNOWN。  
  - `CONTRACT_VIOLATION`（5）→ **owner C（travel-ai）**：对齐 `POST /api/v1/eval/chat` P0 契约。

---

## 今日结论

- **是否接近 P0 门槛**：**否**（通过率偏低；归因以 UNKNOWN / CONTRACT 为主）  
- **最大阻塞项**：  
  1. eval：`UNKNOWN` 占比过高，需拆成可行动 error_code。  
  2. travel-ai：契约类失败需对齐 `answer/behavior/latency_ms/capabilities/meta`。  
  3. report：按 `tags` 分桶通过率待 eval 报表支持（可选 P0+）。

---

## 明日动作

- A：排期 UNKNOWN 映射；C：修 CONTRACT 样例响应；D：次日同模板再出一期日报并附 CSV。
