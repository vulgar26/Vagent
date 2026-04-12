# P0 Dataset — tool_policy 规划（Day4 证据）

> 对齐组长口径：**P0 硬门槛通过率只纳入 `tool_policy ∈ {stub, disabled}`**；**`real` 单独一列统计**，不计入硬门槛。

## dataset 中 tool_policy 分布（当前 `p0-dataset-v0.jsonl`）

| tool_policy | 条数 | 说明 |
|-------------|-----:|------|
| `disabled` | 29 | 不依赖真实外网工具；评测侧重行为 / RAG / 引用 / 安全。 |
| `stub` | 3 | `expected_behavior=tool` 的三题：应用固定桩返回，避免第三方 API 抖动污染硬门槛。 |
| `real` | 0 | v0 未放置 `real` case；report 仍应保留 **real 单列**（可为 0 或 N/A）。 |

## 哪些 case 属于 P0 硬门槛统计

### 规则（推荐写进 eval `run.report` 说明）

- **纳入 P0 硬门槛**：`tool_policy == "stub"` **或** `tool_policy == "disabled"`。
- **不纳入 P0 硬门槛（单列）**：`tool_policy == "real"`。

### 当前 v0 逐条归类

- **`stub`（3 条，纳入硬门槛）**  
  - `p0_v0_tool_001`  
  - `p0_v0_tool_002`  
  - `p0_v0_tool_003`  

- **`disabled`（29 条，纳入硬门槛）**  
  - 除上述 3 条外的全部 `case_id`。

- **`real`（0 条）**  
  - 无；后续若增加外网真实工具题，统一标 `real` 并只在「real 列」看趋势。

## 与 `expected_behavior=tool` 的关系

- 需要 **硬门槛** 可稳定验收时：`expected_behavior=tool` 的题应使用 **`tool_policy=stub`**（由 target/eval 侧桩或录制回放提供确定性返回）。  
- 若业务上必须走真实 API：标 **`real`**，不计硬门槛，避免误杀发布。
