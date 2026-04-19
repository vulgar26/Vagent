# P0 Dataset Changelog

## 验收登记（与代码/评测对齐）

- **可复现结论与指标**：见仓库根目录 `plans/vagent-upgrade.md` 中「验收快照」与 `plans/regression-baseline-convention.md` 登记段落。

## v0.2 (2026-04-11)

### Changed

- **可判定性**：2 条 `attack/*` 的 `expected_behavior` 从 `answer` 调整为 `clarify`（PR/合并说明中保留 case 列表即可）。
- **标签**：`p0_v0_contract_shape_001/002` 增加 `meta/contract`，便于契约类题分桶。

### Notes

- 与 **v0.1** 的 **同 `case_id` 期望** 有差异；compare 须注明 dataset 版本。

## Dataset version policy（版本号升级策略）

### 版本号形式

- 使用 **`v0.x`**（P0）：与 `p0-dataset-v0.jsonl` 的 **P0 题库线**一致；不在 JSONL 每行重复写版本（避免破坏仅识别 case 行的导入器）。
- **当前题库内容的「逻辑版本」以本文件最新一条 `## v0.x` 为准**；Git **commit / tag** 作为不可变锚点（推荐 PR 合并时在描述里写 `dataset: v0.x`）。

### 何时升级哪一位

| 变更类型 | 建议版本 | 说明 |
|----------|----------|------|
| 修正错别字、标点、无判定影响的 tags 微调 | **patch**（如 `v0.1` → `v0.1.1`，P0 可继续记为 `v0.1` 并在该版本下追加 **Changed** 小节） | 不改变 `case_id` 与 `expected_behavior` 语义时可不升主版本；若团队希望「任何合并都可追溯」，也可每次合并升 `v0.2`。 |
| 新增 case、`case_id` 变更、删除 case、`expected_behavior` / `requires_citations` / `tool_policy` 语义变化 | **次版本 +1**（`v0.1` → `v0.2`） | 会影响分母、分桶或与历史 run **不可比**；**必须**新小节 + 说明影响。 |
| 导入 schema 断裂（字段重命名、必填变更） | **次版本 +1** 并同步更新 `vagent-eval` 导入与文档 | 避免旧 JSONL 静默导入错。 |

### 流程（防漂移）

1. 修改 `p0-dataset-v0.jsonl` 前：在本文件顶部新增 **`## v0.x (YYYY-MM-DD)`** 草稿或在 PR 中写好 **Added/Changed/Removed**。  
2. 合并后：**compare** 必须能回答「变差来自代码还是题库」——因此删除/改期望要在 changelog **点名 `case_id`**。  
3. **同一文件名策略**：P0 允许继续维护 `p0-dataset-v0.jsonl`；**真实不可变快照**以 **Git 历史 + 本 changelog** 为准。若将来需要并排保留多版本文件，再引入 `p0-dataset-v0.2.jsonl` 等（与导入脚本约定即可）。

---

## Changelog entry examples（变更记录样例）

以下为**写法模板**，不是真实已发生的条目（真实变更请按 `v0.x` 小节追加）。

### 示例 A — 新增 case（Added）

```text
### Added
- case_id: `p0_v0_rag_empty_004` — 增补空命中下「必须澄清不编造」场景；tags: `rag/empty`, `expected/clarify`。
影响：total_cases +1；`rag/empty` 桶 +1；与上一版 compare 时会出现「新题无历史 verdict」。
```

### 示例 B — 修改 case（Changed）

```text
### Changed
- case_id: `p0_v0_answer_005` — `expected_behavior`: `deny` → `clarify`（组长裁定：先澄清再拒答，符合产品策略）。
影响：同 `case_id` 上历史 run 与新版 **不可直接对比 PASS/FAIL**；报告需按版本切片或只对比「未改 case 子集」。
```

### 示例 C — 删除 case（Removed）

```text
### Removed
- case_id: `p0_v0_contract_shape_002` — 与 `p0_v0_contract_shape_001` 重复度过高，移除以减少噪声。
影响：total_cases -1；若删除的多为 FAIL，**总 pass_rate 可能上升** — 必须在日报注明「分母/题库变更」避免粉饰误解。
```

## v0.1 (2026-04-10)

### Changed

- **运维脚本**：新增 `export-eval-run-failures.ps1`（从 eval 导出 FAIL 明细）。  
- **文档**：补充「Dataset version policy」与 add/modify/delete **changelog 样例**（见上文）。

### Added

- **`tool_policy` 字段**：每条 case 增加 `tool_policy`：`stub` | `disabled` | `real`（v0 中 `real`=0）。
- **Day4 证据**：`p0-dataset-tool-policy.md`（分布 + P0 硬门槛纳入规则）。

### Rules

- **P0 硬门槛统计**：仅 `tool_policy ∈ {stub, disabled}`；`real` 单列，不计硬门槛。

## v0 (2026-04-09)

### Added

- **Initial P0 dataset v0**: 32 cases in `p0-dataset-v0.jsonl`.
- **Expected behavior coverage**: includes `answer` / `clarify` / `deny` / `tool`.
- **Attack suite (seeded for Day2)**: `attack/*` tags are present to support later expansion and isolated reporting.
- **RAG buckets (seeded for Day3)**: includes `rag/empty` and `rag/low_conf` tagged cases to enable grouped statistics.

### Notes

- **Schema (P0 minimal)**: each line is JSONL with `case_id`, `question`, `expected_behavior`, `requires_citations`, `tool_policy`, `tags`.
- **Tagging intent**:
  - `attack/*` tags identify adversarial cases for separate pass-rate reporting.
  - `rag/empty` targets empty-hit handling; `rag/low_conf` targets low-confidence gating/clarification.
  - `requires/citations` is used as a convenience tag alongside `requires_citations=true` for easier grouping.
