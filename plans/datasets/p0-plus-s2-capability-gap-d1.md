# P0+ S2 — D1 能力缺口清单（Vagent）

> 目的：把 `SKIPPED_UNSUPPORTED` 变成 **by-design** 的可追溯裁定，避免每次回归重复争论。  
> 证据来源：`run.report` / `eval_result.debug`（`tools_supported=false` 等）。

## 结论（本轮）

Vagent 当前评测口 `capabilities.tools.supported=false`，因此 dataset 中 `expected_behavior=tool` 的 3 条 case 被 eval 判定为 `SKIPPED_UNSUPPORTED`，属于 **预期跳过**（不计 FAIL，但在报告中单列）。

## 明细表（D1）

| case_id | tags | 预期行为 | 当前 target | 缺口类型 | 裁定建议 |
|---|---|---|---|---|---|
| `p0_v0_tool_001` | `tool/weather` | `tool` | Vagent | 无工具（tools unsupported） | **SKIP（by-design）**：维持 `SKIPPED_UNSUPPORTED`；如需纳入 pass，需在 Vagent 打开工具能力并对齐 `tool_policy=stub` 的契约输出 |
| `p0_v0_tool_002` | `tool/train` | `tool` | Vagent | 无工具（tools unsupported） | **SKIP（by-design）**：同上 |
| `p0_v0_tool_003` | `tool/search` | `tool` | Vagent | 无工具（tools unsupported） | **SKIP（by-design）**：同上 |

## 后续（若组长要求“把 tool 也纳入 pass”）

- 将 Vagent 在 eval 口声明 `capabilities.tools.supported=true` 并实现 stub 工具路径（或让 eval 对 `tool_policy=stub` 的 case 允许“模拟工具”）。
- 同步补：`behavior=tool`、`tool.required/used/succeeded/outcome` 等契约字段（按 `plans/eval-upgrade.md` SSOT）。

