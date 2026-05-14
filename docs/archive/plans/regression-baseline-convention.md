# 回归基线约定（P0-1）

本文是 **Vagent 与 vagent-eval 联调** 的单一事实来源（SSOT）之一：**什么叫一次可比的回归**、**基线如何登记与更新**、**日报必填字段**。  
评测服务独立部署时，以 eval 的 `GET /api/v1/eval/runs/{id}` 返回为准；本地导出的 JSON 仅作附件。

---

## 1. 目标

- 全团队对「可比回归」有同一定义：**同一套题（同一 `dataset_id`）**、同一 **target**（如 `vagent`）上的两次 `FINISHED` run 才能出正式 compare 结论。
- 题集或规则变更与代码回归 **分开归因**：改题后必须换新基线，禁止用旧 base 解释新题上的翻转。
- 日报/发版说明中 **固定可追溯**：dataset、`base`/`cand` run_id（或注明未 compare）。

---

## 2. 强制规则（Compare 前提）

| 规则 | 说明 |
|------|------|
| **同一 dataset** | `base` 与 `cand` 的 `dataset_id` **必须完全一致**。 |
| **同一 target** | 例如均为 `vagent`；跨 target 对比不纳入「Vagent 单系统回归」口径。 |
| **终态** | 两条 run 均为 **`FINISHED`**（或团队书面认可的等价终态）。 |
| **题集/规则变更** | 导入新题集、修改期望、`eval_rule_version` 升级等 → **作废旧基线**，重新登记 **新基线 run** 并更新下表。 |

---

## 3. 角色（可一人兼任）

| 角色 | 职责 |
|------|------|
| **Dataset Owner** | 题集版本：`dataset_id`、导入来源、变更记录（`case_id` 级）。 |
| **Regression Owner** | 维护 **当前基线 run_id**；发版/周期前确认 compare；更新本文 §5。 |
| **Vagent 联系人** | target 的 base-url、`X-Eval-Token`、与基线环境一致。 |

---

## 4. 当前冻结登记（由 Regression Owner 维护）

> **说明**：下列值为联调/示例；正式门禁请以登记当日 **`GET .../runs/{id}`** 校验为准并替换。

| 字段 | 当前值 | 备注 |
|------|--------|------|
| **Dataset 逻辑版本名** | `p0-dataset-v0` | 源文件：`plans/datasets/p0-dataset-v0.jsonl` |
| **`dataset_id`** | `ds_c734df5a78e94d1da41ae31c1c079fcf` | 与 eval 一致；**重新 `POST datasets` + import 会生成新 id**，换题须走 §5。**注意**：`vagent-eval` 已将 dataset/case **落 PostgreSQL**（Flyway `V4`）；**重启进程不会丢题**；`compare` 仍须 **同一 `dataset_id`** 的两条 run。 |
| **`eval_rule_version`** | `p0.v4` | 以 `run.report` / 单条 `debug.eval_rule_version` 为准 |
| **Eval 基址（环境）** | `http://127.0.0.1:8099` | 多环境时注明 dev/staging |
| **当前基线 run_id** | `run_e4d7fa1ce57f47b3a0ef4ae2198a0918` | 全量 32 case；`meta` 已落库可对比 |
| **基线登记日期** | `2026-04-18` | |
| **基线 PASS/FAIL 摘要** | 29 PASS / 0 FAIL / 3 SKIPPED_UNSUPPORTED（tool） | 与 `plans/vagent-upgrade.md`「验收快照」一致 |
| **已知可接受 FAIL（case_id 列表）** | 无（**SKIPPED_UNSUPPORTED 不算 FAIL**） | `p0_v0_tool_001`～`003` 在 `tools_supported=false` 下为预期跳过 |

### 4.1 travel-ai target（扩展登记，`D:\Projects\travel-ai-planner`）

> 与上表 **独立**：`dataset_id` 随 eval 库导入变化；compare 仍须 **同一 dataset_id**（见 §2）。下列为 **2026-04-18** 一次可复现全绿示例。

| 字段 | 当前值 | 备注 |
|------|--------|------|
| **`target_id`** | `travel-ai` | `POST /api/v1/eval/chat` |
| **`dataset_id`** | `ds_0d30f48d494443a096e281c7addba519` | 与当次 eval 导入一致；换库请更新 |
| **`eval_rule_version`** | `p0.v4` | 与 `debug.eval_rule_version` 一致 |
| **示例全量 `run_id`** | `run_6106023bf5354e3089cf1d8b7c4421b4` | **32 PASS / 0 FAIL / 0 SKIPPED**；`error_code` TopN 为空 |
| **E7** | 已实现 | 请求头：`X-Eval-Token`、`X-Eval-Target-Id`、`X-Eval-Dataset-Id`、`X-Eval-Case-Id`；响应：`meta.retrieval_hit_id_hashes[]` 等（见 `plans/eval-upgrade.md` E7） |
| **仓内留证** | `docs/DAY10_P0_CLOSURE.md` | travel-ai-planner 仓库 |

---

## 5. 基线更新流程

1. **Dataset Owner** 确认题集或规则变更已完成，并更新 **§4** 中 `dataset_id` / 逻辑版本名 / 变更说明（可链到 `plans/datasets/` 或 changelog）。
2. **Regression Owner** 在目标环境上对 **新 `dataset_id`**、`target_id=vagent` **完整跑一轮**，得到新 `run_id`。
3. 将 **§4「当前基线 run_id」** 更新为新值，填写登记日期与 PASS/FAIL 摘要；已知 FAIL 列表同步更新。
4. 在团队频道 **@ 相关人** 通告：旧 base **作废**，新 compare 一律对新基线。

---

## 6. 日报 / 发版 Checklist（必填）

每次正式回归或发版相关汇报，至少包含：

- **Dataset**：逻辑版本名 + `dataset_id`
- **Compare**：`base` run_id + `cand` run_id（若未跑 compare，写原因，如「单日单 run」）
- **结论**：通过 / 有条件通过 / 阻塞；若有 regressions，**已排除 dataset/规则变更因素**（是/否）
- **口径（避免与 P0 打架）**：若粘贴 `run.report` 的 `pass_rate`，须同时说明 **`rate_denominator`**；当存在 **`SKIPPED_UNSUPPORTED`** 时，`pass_rate = pass_count / total_cases` **可能小于 1**，与 **`plans/vagent-upgrade.md`「验收快照」里按 `tags` 分桶的通过率** 不是同一指标。P0 分桶与 `tool_policy` 硬门槛分母以 **`vagent-upgrade.md` SSOT 小节** 为准。

---

## 7. 常见坑

- **同一题库多次导入** 会产生不同 `dataset_id`**：以 §4 登记为准；新导入视为新版本。
- **不同 eval 实例或库**：禁止混用不同数据库中的 run_id 做 compare。
- **导出文件**：文件名中的 run_id 仅供参考，**以 eval API 返回的 `dataset_id` / `run_id` 为准**。

---

## 8. 相关文档

- Eval API 清单：`plans/eval-upgrade.md`（runs / results / compare）。
- Vagent 评测接口与 `meta`：`plans/vagent-upgrade.md`（EvalChat、`meta` 契约）。
- **P0-2：compare 摘要键 `meta-trace-keys` 固化与验收**：`plans/eval-meta-trace-keys-vagent.md`。
- **P0-3：标准 compare 跑通与留证**：`plans/regression-compare-standard-runbook.md`。
- **P1：report/看板与 `meta` 治理**：`plans/regression-p1-report-governance.md`。
