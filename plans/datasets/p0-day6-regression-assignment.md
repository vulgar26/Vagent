# D / Day6 — 回归分配机制（按 error_code 聚类 + owner）

> **补做说明**：本应在 Day7 之前完成；与 `leadership-execution.md` §8.4 **D/Day6** 对齐。  
> **证据形态**：每条失败 case 需 **`case_id` + `error_code` + `owner` + `next_action`**。eval 当前为内存存储时，请在 **run 仍存在于 eval 进程内** 时用脚本导出；否则需 **重新跑同一 dataset** 再导出。

## 1. Owner 约定（团队可改，但需全员一致）

| 代号 | 角色 | 典型负责 |
|------|------|----------|
| **A** | eval 服务 | 判定器、`UNKNOWN` 归因、HTTP 客户端、report/compare |
| **B** | Vagent | `POST /api/v1/eval/chat` 契约、RAG/引用闭环 |
| **C** | travel-ai-planner | `POST /api/v1/eval/chat` 契约、阶段编排 |
| **D** | Dataset/集成（你） | 题库版本、回归节奏、日报、协调 A/B/C |

## 2. 默认分锅规则（首轮基线：target = travel-ai）

> 以下在 **无 compare** 时视为「失败分桶 + 指派」，不是严格意义的 PASS→FAIL regression。

| error_code（聚类） | 默认 owner | next_action（模板） |
|--------------------|------------|----------------------|
| `UNKNOWN` | **A** | 扩展 `RunEvaluator`：把 HTTP 非 2xx、JSON 解析失败、缺字段等映射到 SSOT 明确码；必要时在 `debug` 里保留可审计摘要。 |
| `CONTRACT_VIOLATION` | **C**（若 target 为 travel-ai） | 对齐 P0 契约：顶层 `answer`/`behavior`/`latency_ms`/`capabilities`/`meta`；与 `eval-upgrade.md` 一致。 |
| `AUTH` / `TIMEOUT` / `UPSTREAM_UNAVAILABLE` | **A + C** | A：超时、重试、鉴权头；C：服务可用性、依赖（DB/LLM/Redis）。 |
| 其他 | **按 SSOT 附录 D 具体码** 个案指定 | 查阅 `p0-execution-map.md` 附录 D，指定到 B 或 C。 |

## 3. 生成「case 级」列表（必须）

在 **`plans/datasets/`** 下执行（替换为你的 `run_id`）：

```powershell
cd D:\Projects\Vagent\plans\datasets
.\export-eval-run-failures.ps1 -RunId "run_你的run_id" -EvalBase "http://localhost:8099" -OutCsv ".\run_failures.csv"
```

- 控制台会打印 **FAIL** 行；CSV 含 **`case_id`, `error_code`, `owner`, `next_action`**（owner/next 为脚本按 §2 **建议值**，你可人工覆盖后贴进日报）。

## 4. 与「真实 regressions」的区别

- **Day6 本任务**：按 **error_code 聚类 + 指派**，首轮基线可直接用 **当前 run 的全部 FAIL**。  
- **严格 regressions（PASS→FAIL）**：需要 **compare(base, cand)**；在仅有单次基线时，日报写：**「无 base，以下为失败分锅清单（非 regression 列表）」**。

## 5. 聚类摘要（便于组长一眼看桶）

> 将下面数字替换为你 **实际 report** 中的 TopN（示例来自你曾反馈的一次 travel-ai 基线：UNKNOWN 21 + CONTRACT_VIOLATION 5）。

| error_code | 条数（约） | owner | next_action（摘要） |
|------------|------------|-------|---------------------|
| `UNKNOWN` | 21 | A | 收敛判定映射，减少兜底 UNKNOWN |
| `CONTRACT_VIOLATION` | 5 | C | travel-ai 响应对齐评测契约 |

**完整 case_id 级证据**：以 §3 导出的 CSV / 表格为准，附在 PR 或日报附件。
