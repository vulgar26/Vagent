# D 线 P0 验收一页纸（Day10）

- **TODAY_TOKEN**：`2026-04-11-D-D10`  
- **角色**：Dataset / 回归 / 集成（D）  
- **对照 SSOT**：`plans/eval-upgrade.md`（P0 退出标准等）、`plans/p0-execution-map.md`、`plans/leadership-execution.md` §7.4 / §8.4 D 线  

---

## 1. 是否过线（结论）

| 维度 | 结论 | 说明 |
|------|------|------|
| **D 线交付（题库 + 流程资产）** | **已过线（就 D 线范围而言）** | ≥30 case；`attack/*` 套件与三类注入覆盖说明；`rag/empty` & `rag/low_conf` 分组与口径；`tool_policy` 规则与分布；版本策略与 changelog（含 v0.2）；Day6 分锅脚本与说明；Day8 日报模板 + 示例；Day9 可判定性修订。 |
| **工程整体 P0（eval + 双 target 全绿）** | **未全绿（当前基线）** | travel-ai 上曾出现大量 FAIL、`UNKNOWN` 占比高；全量 P0 还需 A/B/C 与 eval 判定/契约收敛。 |

**一句话**：**D 线 P0 任务可结案；项目级 P0 仍以组长对 A1/A2/A3 的验收为准。**

---

## 2. 对照 P0 门槛（摘要）

| 门槛项（与 D 线相关） | 状态 |
|------------------------|------|
| Dataset ≥30 case | 满足（32） |
| `attack/*` ≥8 且覆盖 prompt / source / tool 注入 | 满足（见 `p0-dataset-attack-suite.md`） |
| `rag/empty`、`rag/low_conf` 可统计 + 口径文档 | 满足（见 `p0-dataset-rag-buckets.md`） |
| `tool_policy`：硬门槛 stub/disabled，real 单列 | 规则与数据已落地（见 `p0-dataset-tool-policy.md`）；eval 侧按 `tool_policy` 分栏报表可后置 |
| 版本化与变更记录 | 满足（changelog + v0.2） |
| 回归节奏：run、report、分锅、日报模板 | 机制已具备；持续产出依赖每日执行 |

---

## 3. Top 阻塞项（每项含 owner + 预计时间）

> 时间为 **粗估**，用于排期，非承诺截止。

| # | 阻塞项 | Owner | 预计修复时间（量级） |
|---|--------|-------|----------------------|
| 1 | `UNKNOWN` 占比高：eval 需把失败映射到 SSOT `error_code`，减少兜底 | **A（eval）** | 约 **3～5 人日**（视分支复杂度） |
| 2 | `CONTRACT_VIOLATION`：travel-ai（及/或 Vagent）评测接口与 P0 契约对齐 | **C / B（target）** | 各约 **2～4 人日**（可并行） |
| 3 | 按 `tags`（attack/rag 桶）出 report 子通过率 | **A（eval）** | 约 **1～2 人日**（可选增强） |
| 4 | `tool_policy` 写入 eval `EvalCase` 与硬门槛分母逻辑 | **A（eval）** | 约 **1～2 人日** |
| 5 | 无统一 `kb_fixture` 时，`requires_citations=true` 题在「无 KB」target 上的语义（SKIP 或分集） | **A + D** | 约 **1 人日**（规则拍板 + 文档） |

---

## 4. 建议的 P0 后动作（指针）

- **【强制执行单】** `plans/p0-plus-execution.md`：P0-A 收口后进入 **P0+**（契约清零、UNKNOWN 收敛、题集与能力对齐、compare/分桶运营）的 **门禁、冲刺划分、必交证据、偷工减料拒收条款**；项目组 **不得以「未安排」为由跳过**。  
- `plans/leadership-execution.md` **§10**：SSE 与 eval 门控单一事实来源（P1-0），建议组长在 **P0+ 出口** 后勾选并指定 **B（Vagent）** owner。  
- 双 target 全量绿：**同一 dataset v0.2** 上分别跑 Vagent / travel-ai，再出一期 **compare** 与日报（细节见 `p0-plus-execution.md` §8）。

### 4.1 评测口限定范围（补充说明）

- 本轮为提升 P0+ 回归稳定性，在 **`POST /api/v1/eval/chat`** 增加检索前安全/拒答门控（`EvalChatSafetyGate`，开关：`vagent.eval.api.safety-rules-enabled`），并在短路时写入 `meta.eval_safety_rule_id` 便于分桶统计。
- **范围管控**：该门控目前 **仅限评测口**；主线对话链路的复用/收敛，按 P0+ 出口后的「共享门控（单一事实来源）」任务执行，避免两套逻辑长期分叉。

---

## 5. D 线声明

- **D / Day10**：本文件为 **P0 验收包（D 线）**；**`PASS Day10`** 表示 D 线指令库 **§8.4 D/Day1～Day10** 交付闭环，**不等于** 全项目 P0 已在所有 target 上数值过线。
