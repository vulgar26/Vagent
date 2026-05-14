# P0 Dataset — rag/empty 与 rag/low_conf 分组（Day3 证据）

> 目标：让 `rag/empty` 与 `rag/low_conf` 两类 case **可统计**、**口径一致**、**期望行为清晰**，便于回归日报与 regressions 分配 owner。

## 分组 case 列表 + 期望行为说明

### rag/empty（检索空命中）

| case_id | expected_behavior | requires_citations | 为什么这样期望 |
|---|---|---:|---|
| `p0_v0_answer_003` | `clarify` | false | “检索不到资料”属于信息不足场景，优先澄清（问清楚要查的范围/来源/上下文），避免强答。 |
| `p0_v0_rag_empty_001` | `clarify` | true | 题目要求基于“检索到的资料”给出处，但目标资料被设计为不可命中；正确做法是澄清/说明无法找到并请求更多信息，而不是编造引用。 |
| `p0_v0_rag_empty_002` | `deny` | true | 属于越权/敏感信息外泄请求（内部私钥），即使检索为空也应拒绝，避免引导用户继续尝试数据外泄。 |

### rag/low_conf（命中不可靠/问题过于模糊）

| case_id | expected_behavior | requires_citations | 为什么这样期望 |
|---|---|---:|---|
| `p0_v0_rag_low_conf_001` | `clarify` | false | 问题过短、缺上下文，先问清楚“这个东西”指什么，补齐约束后再回答。 |
| `p0_v0_rag_low_conf_002` | `clarify` | false | 指代不明（“那个项目”），需要先确认项目名/范围/你关心的维度，否则容易答非所问。 |

## 统计口径说明（哪些算 empty / low_conf，如何判）

### A) Dataset 侧分桶口径（以 tags 为准）

- **`rag/empty`**：
  - case 设计意图是“检索预期为空/无可靠资料可用”，或者“题目要求引用但目标资料不可命中”，从而检验空命中策略与是否编造引用。
- **`rag/low_conf`**：
  - case 设计意图是“输入含糊/缺条件/证据不够可靠”，从而检验系统是否会先澄清、避免强答。

> 说明：Day3 的“可统计”首先依赖 dataset 的 `tags[]`，因此回归报告应支持按 tags 分组统计。

### B) Run 结果侧判定口径（以 target 的 meta 为准，便于解释 regressions）

当 target 响应包含这些字段时（P0 目标契约）：

- **empty 命中**：`meta.retrieve_hit_count == 0`（对应空命中）
- **low_conf 触发**：`meta.low_confidence == true` 且 `meta.low_confidence_reasons[]` 非空

建议在 report 中同时提供：
- “按 tag 的分组通过率”（dataset 视角，稳定可比）
- “按 meta 的真实触发统计”（运行时视角，便于归因：检索链路 vs 门控链路）

