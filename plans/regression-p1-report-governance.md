# P1：Report / 看板与 `meta` 治理

**前置**：P0-1～P0-3 已完成或并行推进（基线登记、`meta-trace-keys`、至少一次标准 compare 留证）。  
本文覆盖 **运营侧报表是否消费 `meta`**，以及 **`meta` 契约变更** 的跨团队流程，避免「口头指标」与 eval 落库不一致。

---

## P1-1：Report / 分桶与 `meta` 对齐

### 目标

- 明确 **对外日报、看板、门禁** 的指标来源：只认 **eval 已持久化且 API 可拉取** 的字段（`verdict`、`error_code`、`latency_ms`、`meta` 等），禁止与本地脚本各算一套。
- 若需要 **检索分布**（如 hybrid 模式占比、距离分桶聚合），确认 **vagent-eval 的 `report` / `buckets`（或等价接口）** 是否已读 **`eval_result.target_meta_json`**；若未读，走 **需求单** 扩展 eval，而不是在业务仓另起统计脚本当 SSOT。

### 执行步骤

1. **盘点**：列出当前日报/看板用到的 **每一个指标** 及其数据来源（eval report、buckets、手工导出 results、其他）。  
2. **对照**：对 vagent 关心的检索类指标，在 **`GET .../runs/{id}/report`**（及若有 **`/report/buckets`**）响应中逐项确认 **是否存在或可由现有字段推导**。  
3. **缺口决策**：  
   - **能在 results 全量 `meta` 里算、但 report 没有**：评估是 **离线一次性分析**（允许脚本）还是 **门禁必须**（应推进 eval 聚合进 report）。  
   - **必须进门禁**：向 vagent-eval 提需求（接受字段清单、聚合口径、性能预算）。  
4. **文档化**：在团队「指标词典」或本页 **§5 登记表** 中写明：**指标名 → API 路径 → 是否 P0/P1 门禁**。

### 验收

- 任意指标若出现在 **发版阻塞条件** 中，能在 **eval 官方 API** 或 **已文档化的导出路径** 中复现，无需私有未提交脚本。

---

## P1-2：`meta` 契约变更治理（跨仓库）

### 目标

- Vagent **重命名、删除、改变语义** 的 `meta` 键，不会静默破坏 eval 的 **contract、meta-trace-keys、看板**。

### 规则

| 触发条件 | 动作 |
|----------|------|
| **新增** 可选观测键 | 更新 `plans/eval-meta-trace-keys-vagent.md` §5；若需进 compare 摘要，同步 eval `meta-trace-keys`。 |
| **重命名 / 删除** 已有键 | **破坏性变更**：发 MR 说明；同步 eval 侧 contract / 探针 / 报表；**至少**更新 §5 与运维配置清单。 |
| **travel-ai** | 与 vagent **分 target 配置**；禁止把 vagent 的 `meta-trace-keys` 抄到 travel-ai。 |

### 执行步骤

1. 在 Vagent MR 模板或 **Checklist** 中增加一条：**「是否改动 `EvalChatResponse.meta` 键集合或语义？」** 若 **是**，必须链接到本页与 `eval-meta-trace-keys-vagent.md` 的更新子 MR 或子任务。  
2. **Regression Owner** 在发版前核对：**eval 配置与文档** 已与本次 release 对齐。

### 验收

- 近一次含 `meta` 变更的 release，存在 **可追溯的 eval/运维侧变更记录**（工单或 commit）。

---

## P1-3（可选技术债）：观测口径一致性

- **示例**：`hybrid_lexical_mode=bm25` 与 `hybrid_lexical_chunk_id_count` 等计数的语义是否一致；若产品/研发需要「可对外的统一解释」，单独立项，**不阻塞** P0/P1 主流程。

---

## P1-4（可选运维）：`meta` 体积与落库失败率

- 关注 eval **`eval.persistence.max-target-meta-json-chars`**：超限时 **整段 `meta` 不落库**。  
- 建议 **季度或发版前**：抽检 **`meta` 为空比例**；Vagent 侧避免向 `meta` 塞大块调试文本。

---

## 5. P1-1 登记表（指标 → 来源）

| 指标（人类可读） | 来源 API / 路径 | 是否门禁 | 备注 |
|------------------|-----------------|----------|------|
| 通过率 / FAIL 数 | `GET .../runs/{id}/report` | 是 | Vagent `p0.v4`：`run_e4d7fa1…` → 29 PASS、0 FAIL、3 SKIPPED |
| 单条检索归因（hybrid、距离、membership） | `GET .../runs/{id}/results` 每条 `meta` | 是（对比/排障） | 2026-04 起 `meta` 已持久化非空；compare 投影见 P0-2 |
| Tag 桶（attack / rag.empty / rag.low_conf） | **本仓脚本** `scripts/summarize-p0-eval-buckets.ps1` + 导出 results | 是（项目 P0 证据） | 直至 eval **report/buckets** 与题集 tags 完全对齐前，门禁可继续用脚本留证 |
| hybrid 模式占比（聚合） | eval `report` / `buckets`（若已接 `target_meta_json`） | 否→待 eval | 日报需要时再提需求单 |

---

## 6. 相关文档

- P0-1：`plans/regression-baseline-convention.md`
- P0-2：`plans/eval-meta-trace-keys-vagent.md`
- P0-3：`plans/regression-compare-standard-runbook.md`
- Eval API：`plans/eval-upgrade.md`
- Vagent 契约与升级：`plans/vagent-upgrade.md`
