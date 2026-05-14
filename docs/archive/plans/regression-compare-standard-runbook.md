# P0-3：标准 compare 跑通与留证（Runbook）

本文定义 **一次可被审计的「基线 vs 候选」回归动作**：从两条 run 到 compare 产物，以及 **必须保存的证据**。  
前置：**P0-1**（`plans/regression-baseline-convention.md`）已登记冻结 `dataset_id`；**P0-2**（`plans/eval-meta-trace-keys-vagent.md`）已在目标环境配置 `meta-trace-keys`（若尚未配置，compare 仍可跑，但 `*_meta_trace` 可能为空 `{}`）。

---

## 1. 目标

- 在同一 **`dataset_id`**、同一 **`target_id=vagent`**、同一 **eval 环境** 下，完成 **`base` run → `cand` run → GET compare**。
- 产出 **可归档、可转发** 的最小证据包，供发版/日报/工单引用，避免「口头说过比过」。

---

## 2. 前置检查（跑之前）

| 检查项 | 说明 |
|--------|------|
| **eval 可达** | 例如 `GET {eval}/api/v1/eval/health` 或任意已知 200 的只读接口（以你们实际为准）。 |
| **Vagent 可达** | eval 内配置的 vagent `base-url` 可访问；`X-Eval-Token` 与 Vagent `vagent.eval.api` 一致。 |
| **`dataset_id`** | 与 **§4 冻结表** 一致；若本次故意换新题集，先走 P0-1 的基线更新流程，**不要**用旧 base 比新题。 |
| **变量会话** | 使用 PowerShell/curl 时，`base`/`cand` 的 run_id 在同一终端会话内赋值后再 compare，避免空参。 |

---

## 3. 标准流程（顺序）

### 步骤 A：基线 run（`base`）

1. 对冻结的 **`dataset_id`**、`target_id=vagent` **创建并执行**一轮完整 run（与日常门禁相同参数）。  
2. 轮询 **`GET .../runs/{run_id}`** 至 **`FINISHED`**（或团队认可的终态）。  
3. 记录 **`run_id` → 记为 `base`**；拉取 **`GET .../runs/{base}/report`**，保存摘要（通过率、Top error_code 等）。

### 步骤 B：候选 run（`cand`）

1. 在 **已明确的变更** 之后执行（例如：合并某 MR、切换某配置、或「无代码变更」的对照复跑——须在证据里写清）。  
2. 再次对 **同一 `dataset_id`**、`vagent` 创建并执行一轮 run。  
3. 记录 **`run_id` → 记为 `cand`**；同样保存 **report**。

### 步骤 C：Compare

1. 调用 **`GET .../runs/compare?base={base}&cand={cand}`**（URL 以 vagent-eval 为准）。  
2. 确认 HTTP 200 且 body 非 `run not found` / 非 dataset 不匹配类错误。  
3. 在响应中查看 **`regressions` / `improvements`**（名称以实际 API 为准）；任抽 **1～2 条**含 **`base_meta_trace` / `cand_meta_trace`** 的项（若已配置 P0-2），核对与 **results 中同 `case_id` 的 `meta`** 一致。

### 步骤 D：留证（最小证据包）

至少保存以下内容之一（推荐 **全部**）：

| 证据 | 来源 | 用途 |
|------|------|------|
| **Run 元数据** | 两次 `GET .../runs/{id}` 的响应或截图 | 证明 `dataset_id`、`target_id`、终态、时间 |
| **Report 摘要** | 两次 `GET .../runs/{id}/report` | 通过率与错误分桶 |
| **Compare 全文** | `GET .../runs/compare?...` 的 JSON 落盘 | 回归列表与 meta_trace |
| **可选：一条 case 的 results 切片** | `GET .../runs/{cand}/results?limit=...` 中含关注 `case_id` | 佐证 `meta` 全量 |

归档位置：团队约定的 **issue 附件 / 共享盘 / 发版工单**；文件名建议包含 **`dataset_id` 短前缀、`base`、`cand`、日期**。

---

## 4. 证据登记表（每次 compare 填一行）

| 日期 | 环境 | dataset_id | base run_id | cand run_id | 变更说明 | compare 结论 | 证据链接/附件 |
|------|------|------------|-------------|-------------|----------|----------------|---------------|
| 2026-04-18 | dev | `ds_c734df5a78e94d1da41ae31c1c079fcf` | `run_e4d7fa1ce57f47b3a0ef4ae2198a0918` | （与另一 cand 对比时补） | 基线登记 / 无代码变更复跑 | 同 dataset 下 compare 200；无契约回退即接受 | 保存 `GET .../compare` JSON + 可选 `results` 切片 |
| （例） | dev | ds_… | run_… | run_… | MR#xxx / 配置 yyy | 无阻塞 regressions | … |

---

## 5. 验收标准（P0-3 算完成）

- **同一 `dataset_id`**：两次 `GET .../runs/{id}` 中字段一致。  
- **compare 成功**：可复现的 URL 或保存的 JSON；**regressions** 列表团队已读过并有结论（修复 / 接受 / 误判排除）。  
- **登记表**（§4）或等价工单里 **有一行完整记录**。

---

## 6. 常见失败与处理

| 现象 | 处理 |
|------|------|
| `run not found` | 核对两 run 是否属于 **当前 eval 库**；query 里 `base`/`cand` 是否非空。 |
| compare 200 但无 meta_trace | 检查 P0-2 `meta-trace-keys`；或该条 `meta` 被体积策略整段丢弃。 |
| 大量 PASS→FAIL | **先**核对 dataset / `eval_rule_version` 是否变更（P0-1），再归因代码。 |

---

## 7. 相关文档

- P0-1 基线约定：`plans/regression-baseline-convention.md`
- P0-2 meta 摘要键：`plans/eval-meta-trace-keys-vagent.md`
- P1 report/看板与契约治理：`plans/regression-p1-report-governance.md`
- Eval API：`plans/eval-upgrade.md`
- 混合检索 / rerank 开关在同一 `dataset_id` 下的 A/B 与 compare 门禁：`scripts/README-hybrid-rerank-ab.md`（`compare-eval-runs.ps1` 的 `-RequireSameDataset`、`-StrictContractGate`）
