## 组长执行手册（两周 P0 排期 + 任务下达 + 验收口径 + 实习生带教）

本文件把 `plans/` 下的规格文档落到“可指挥执行”的层面：谁做什么、每天交付什么、怎么验收、怎么带教实习生。

> **唯一施工单（SSOT）**：`plans/p0-execution-map.md`  
> **唯一判定规则（SSOT）**：`plans/eval-upgrade.md`（P0 契约 + PASS/FAIL + error_code + 安全边界）

---

## 1. 团队编制与职责边界

- **员工 A（Eval 负责人）**
  - 负责 eval 服务 P0：dataset/run/report/compare、安全边界、引用闭环校验（hashed membership）。
- **员工 B（Vagent 负责人）**
  - 负责 Vagent target P0：`POST /api/v1/eval/chat`、sources 服务端生成、门控、引用闭环、EVAL_DEBUG 边界、一次性 Reflection（如启用）。
- **员工 C（travel-ai 负责人）**
  - 负责 travel-ai target P0：`POST /api/v1/eval/chat`、固定线性阶段、PlanParser（附录 E schema）、repair once、串行工具与降级矩阵、可观测 meta。
- **员工 D（Dataset/回归/集成负责人）**
  - 负责 P0 dataset（≥30 case）与每日回归节奏；出“回归日报”；回归 triage（按 error_code 聚类）与 owner 分配。
- **实习生（你）**
  - 目标：两周内能独立完成一次“导入 dataset → 跑 run → 看 report/compare → 输出回归结论 + 归因建议”。

---

## 2. 两周排期表（每人一份）

### 2.1 员工 A（Eval 负责人）排期（10 个工作日）

| 天 | 目标 | 当天交付物（可验收） |
|---|---|---|
| D1 | 项目骨架与配置跑通 | 能启动；读取目标配置（Vagent、travel-ai baseUrl + token-hash）；最小健康检查 |
| D2 | Dataset 导入闭环 | JSONL/CSV 导入 + dataset 列表/查询；导入 30+ case 不报错 |
| D3 | Run 串行执行 | 能创建 run 并串行跑完全部 case；结果落库/输出含 error_code |
| D4 | Target 调用契约对齐 | 调用 target `POST /api/v1/eval/chat` 并解析响应（snake_case 字段） |
| D5 | P0 判定器 v1 | expected_behavior + requires_citations + SKIPPED_UNSUPPORTED 判定可用 |
| D6 | 引用闭环校验 | hashed membership 校验落地；可稳定判定 `SOURCE_NOT_IN_HITS` |
| D7 | 报告 report v1 | `run.report`（pass_rate/skipped_rate/p95 latency_ms/error_code TopN） |
| D8 | compare v1 | base vs cand：pass_rate_delta + regressions/improvements 列表 |
| D9 | 安全边界与审计 | enabled/CIDR/token-hash/审计 reason；EVAL_DEBUG 违规判定 |
| D10 | 稳定性与演示 | 一键跑一轮并导出报告；输出“已知限制与下一步” |

### 2.2 员工 B（Vagent 负责人）排期（10 个工作日）

| 天 | 目标 | 当天交付物（可验收） |
|---|---|---|
| D1 | Eval 接口骨架 | `POST /api/v1/eval/chat` 能返回最小字段（snake_case） |
| D2 | sources 服务端生成 | `sources[]` 从 hits 构造；`snippet` ≤300；LLM 不产 sources |
| D3 | 空命中 + low_confidence | `retrieve_hit_count/low_confidence/reasons/error_code` 可被 eval 统计 |
| D4 | 引用闭环（debug 路径） | EVAL_DEBUG 下可排障；非 debug 不泄露敏感字段 |
| D5 | 引用闭环（hashed） | 非 debug 返回 `retrieval_hit_id_hashes[]` + N/total + canonical scheme |
| D6 | EVAL_DEBUG 边界固化 | 非 debug 严禁返回明文 hit_ids；违规路径可复现 |
| D7 | Reflection（一次性） | `guardrail_triggered` + 可归因 `error_code`；不循环 |
| D8 | 与 eval 集成跑通 | 用 A 的 eval 跑通 30+ case；修掉契约/字段问题 |
| D9 | attack 用例加固 | `attack/*` 通过率提升；归因稳定（非 UNKNOWN） |
| D10 | P0 过线/收敛 | 输出“剩余 fail/regressions → 修复点 → 预计时间” |

### 2.3 员工 C（travel-ai 负责人）排期（10 个工作日）

| 天 | 目标 | 当天交付物（可验收） |
|---|---|---|
| D1 | Eval 接口骨架 | `POST /api/v1/eval/chat` 最小字段返回（snake_case） |
| D2 | 串行阶段骨架 | 固定 `PLAN→RETRIEVE→TOOL→WRITE→GUARD` 的执行器框架 |
| D3 | meta 可观测 | `stage_order/step_count/replan_count=0` 稳定输出 |
| D4 | PlanParser 接入 schema | 按 `p0-execution-map.md` 附录 E schema 解析；tolerant parse |
| D5 | repair once | 解析失败最多修复一次；`plan_parse_attempts/outcome` 输出 |
| D6 | 工具串行 + 超时 + 降级 | 工具超时/失败不崩；按矩阵降级并归因 |
| D7 | 空命中/低置信门控 | 行为可预测（clarify/deny）；meta 原因可统计 |
| D8 | 与 eval 集成跑通 | 能被 A 的 eval 稳定跑；修契约/字段/边界 |
| D9 | 注入/解析鲁棒性 | prompt/工具回传注入与 plan 失败路径稳定可控 |
| D10 | P0 过线/收敛 | 输出“剩余 regressions → 修复策略/风险” |

### 2.4 员工 D（Dataset/回归/集成负责人）排期（10 个工作日）

| 天 | 目标 | 当天交付物（可验收） |
|---|---|---|
| D1 | P0 dataset v0 | 30+ case 初版（含 tags/expected_behavior/requires_citations） |
| D2 | attack/* 套件 | ≥8 条 attack 覆盖三类注入；可单独统计 |
| D3 | rag/empty & low_conf 分组 | 分组与期望行为清晰；统计口径明确 |
| D4 | tool_policy 规划 | P0 门槛默认 stub/disabled；real 单列不计硬门槛 |
| D5 | 跑第一轮基线 | 产出基线 report/compare（允许失败多，但必须可归因） |
| D6 | 回归分配机制 | regressions 按 error_code 聚类→分配给 A/B/C |
| D7 | dataset 版本化 | dataset 版本号/变更记录/不可变性约束（避免漂移） |
| D8 | 每日回归固化 | 固定“回归日报模板”并连续产出 |
| D9 | 定位质量提升 | 对高频失败补充 case/期望，减少“不可判定” |
| D10 | P0 验收包 | 一页结论：是否过线 + 未过线阻塞项 + owner |

---

## 3. 任务下达方式（不要“甩 plans/”）

### 3.1 你给团队的下达消息（可直接复制粘贴）

> 各位，本轮目标是**两周完成 P0 可回归闭环并达标**。  
> **唯一施工单**：`plans/p0-execution-map.md`（A1/A2/A3 + 附录 C/D/E）。  
> **唯一判定规则**：`plans/eval-upgrade.md`（P0 契约 + PASS/FAIL + error_code + 安全边界）。  
> 分工：A=eval、B=Vagent target、C=travel-ai target、D=dataset/回归集成（排期表见本消息附件/本文第 2 节）。  
> **每日节奏**：每天 17:30 前必须产出一轮 run 的 `run.report` + `compare`；D 汇总“回归日报”（top errors + top regressions + owner），我拍板当日优先级。  
> 所有争议以 `p0-execution-map.md` 为准；接口字段必须与附录 C 一致（`X-Eval-*` header、snake_case、`latency_ms` 等）。

### 3.2 每个人“只需要读哪几份”

- **A（Eval）必读**：`eval-upgrade.md`（P0 契约/规则/安全边界） + `p0-execution-map.md`（A1/C/D）
- **B（Vagent）必读**：`vagent-upgrade.md`（P0） + `p0-execution-map.md`（A2/B1/C）
- **C（travel-ai）必读**：`travel-ai-upgrade.md`（P0） + `p0-execution-map.md`（A3/附录 E/C）
- **D（Dataset/集成）必读**：`eval-upgrade.md`（dataset + attack + 统计口径） + `p0-execution-map.md`（验收门槛引用）

---

## 4. 如何判断是否完成任务（验收清单 = 必交证据）

> 原则：**不看“口头说完成”，只看证据**。每个模块都要“可被 eval 统计/对比/复现”。

### 4.1 每日必交（D 汇总，组长审核）

- **当日 run 证据**
  - `run_id`
  - `run.report` 摘要：`pass_rate`、`skipped_rate`、`p95 latency_ms`、`error_code` TopN
  - `compare` 摘要：regressions/improvements TopN（至少列出 PASS→FAIL case_id）
- **回归分配**
  - 每个 regression 必须有 owner（A/B/C 之一）与下一步动作（修契约/修策略/修解析/修安全边界等）

### 4.2 A（Eval）验收通过的硬证据

- 能对两目标（Vagent、travel-ai）执行 run，并输出：
  - `run.report`（含 `error_code` 分布与 P95 `latency_ms`）
  - `compare`（base vs cand：regressions/improvements）
- P0 判定器生效：
  - expected_behavior / requires_citations / SKIPPED_UNSUPPORTED
  - 引用闭环 hashed membership 校验能稳定产出 `SOURCE_NOT_IN_HITS`
- 安全边界有证据：
  - enabled/CIDR/token-hash/审计 reason
  - 非 EVAL_DEBUG 返回敏感字段会被判 `SECURITY_BOUNDARY_VIOLATION`

### 4.3 B（Vagent）验收通过的硬证据

- `POST /api/v1/eval/chat` 返回字段满足附录 C（snake_case、`latency_ms`、`capabilities`、`meta.mode`）
- `sources[]` 服务端生成且 `snippet` 截断 ≤300
- 引用闭环：
  - 非 debug：返回 `meta.retrieval_hit_id_hashes[]` + `retrieval_candidate_limit_n/total` + `canonical_hit_id_scheme`
  - 非 debug：不返回明文 hit_ids
- 门控可统计：
  - `retrieve_hit_count/low_confidence/low_confidence_reasons[]/error_code`

### 4.4 C（travel-ai）验收通过的硬证据

- `POST /api/v1/eval/chat` 返回字段满足附录 C（snake_case、`latency_ms`）
- 线性阶段可验收：
  - `meta.stage_order[]`、`meta.step_count`、`meta.replan_count=0`
- PlanParser 与 repair once：
  - 按 `p0-execution-map.md` 附录 E schema 解析
  - 输出 `meta.plan_parse_attempts/meta.plan_parse_outcome`
- 工具串行 + 降级矩阵覆盖：失败不崩、有 error_code

### 4.5 D（Dataset/回归）验收通过的硬证据

- dataset ≥30 case 且含：
  - `attack/*` ≥8（覆盖三类注入）
  - `rag/empty`、`rag/low_conf` 分组可统计
  - P0 门槛统计默认只纳入 `tool_policy=stub|disabled`
- dataset 有版本化与变更记录（避免漂移导致 compare 失真）

---

## 5. 实习生带教（把“全流程”教会）

### 5.1 带教目标（两周结束你能独立做）

- 独立完成：导入 dataset → 跑 run → 看 report/compare → 输出回归结论（含 owner 分配建议）
- 能口头解释 1 个 regression：为什么 FAIL、属于哪个系统、下一步怎么修、需要哪些 meta 证据

### 5.2 每位员工的带教配额（强制）

- 每位员工每周至少 **2 小时 pairing** 实习生，且 pairing 必须产出一个可验收物：
  - 新增 1–2 条 case
  - 修复 1 个 regression
  - 或补齐 1 项 meta/契约/安全边界证据

---

## 6. 组长指挥节奏（每天怎么开）

- **每天固定一轮跑数**：以 `compare.regressions` 作为当天唯一最高优先级来源。
- **三条铁律**
  - 所有回归必须有 owner
  - 所有修复必须用一次 compare 证明（至少核心子集）
  - 所有争议回到 SSOT：`p0-execution-map.md` 与 `eval-upgrade.md`

---

## 7. 可直接转发的执行 prompts（A/B/C/D + Leader）

> 用法：你把对应段落原样转发给员工；把 “Leader prompt” 保存起来，换会话时发给我或任何协助者，保证口径不漂移。

### 7.1 员工 A（Eval 负责人）prompt（可直接转发）

你是本项目的 **eval P0 负责人**。目标是在 2 周内交付可跑、可对比、可审计的 eval 闭环，并作为所有子系统验收的唯一裁判。

#### 你要做什么（SSOT）

- **唯一施工单**：`plans/p0-execution-map.md`（只看 A1/C/D）
- **唯一判定规则**：`plans/eval-upgrade.md`（P0 契约、PASS/FAIL、error_code、安全边界、引用闭环 hashed membership）

按 `p0-execution-map.md` 的 **A1（eval 两周 MVP）** 完成：

- dataset 导入（JSONL/CSV）与查询（≥30 case）
- run 创建/执行/取消（P0 允许串行执行）
- 调用 target：`POST /api/v1/eval/chat`（Vagent、travel-ai）
- P0 判定器：
  - expected_behavior
  - requires_citations
  - `SKIPPED_UNSUPPORTED`
  - 引用闭环（hashed membership）→ `SOURCE_NOT_IN_HITS`
- 输出：
  - `run.report`
  - `compare`（base vs cand）
- 安全边界：enabled/CIDR/token-hash/审计 reason；`EVAL_DEBUG` 违规判定

#### 你必须遵守的口径（硬要求）

- 对外契约与字段命名以 `p0-execution-map.md` 附录 C 为准（snake_case、`latency_ms`、Header 使用 `X-Eval-*`）
- error_code 枚举以 `p0-execution-map.md` 附录 D 为准
- 引用闭环必须按 `eval-upgrade.md` 的 hashed membership 规则实现（canonical id scheme + 前 N 口径）

#### 你如何自验收（必须提供证据）

- 能对 **两个 target** 跑同一 dataset 的 run，并产出：
  - `run.report`：pass_rate、skipped_rate、p95 latency_ms、error_code TopN
  - `compare`：pass_rate_delta、regressions（PASS→FAIL case_id 列表）、improvements
- 对任意 case：
  - target 返回契约缺字段 → `CONTRACT_VIOLATION`
  - 引用闭环失败 → `SOURCE_NOT_IN_HITS`
  - 非 EVAL_DEBUG 返回敏感 debug 字段 → `SECURITY_BOUNDARY_VIOLATION`

#### 你每天要返回给组长什么（固定格式）

每天 17:30 前发一条消息，按以下格式：

- `run_id`：
- `dataset_version`：
- `targets`：vagent / travel-ai
- `run.report` 摘要：
  - pass_rate=
  - skipped_rate=
  - p95_latency_ms=
  - top_error_codes=[...]
- `compare` 摘要（如有 base/cand）：
  - pass_rate_delta=
  - regressions=[case_id...]
  - improvements=[case_id...]
- 阻塞项（如有）：（按 owner=A/B/C/D 指派）

---

### 7.2 员工 B（Vagent 负责人）prompt（可直接转发）

你是本项目 **Vagent target P0 负责人**。目标是在 2 周内让 Vagent 满足评测契约、可被 eval 稳定回归，并达成 P0 的“引用闭环 + 门控 + 安全边界”要求。

#### 你要做什么（SSOT）

- **唯一施工单**：`plans/p0-execution-map.md`（只看 A2/B1/C/D）
- **参考规格**：`plans/vagent-upgrade.md`（P0 章节）
- **判定规则**：`plans/eval-upgrade.md`（只按其契约与 error_code 归因）

按 `p0-execution-map.md` 的 **A2/B1** 完成：

- 实现 `POST /api/v1/eval/chat`（非流式、snake_case）
- Header 读取 `X-Eval-*`（代码内部可归一化变量名，但对外契约不变）
- `sources[]` 服务端生成（LLM 禁止生成/改写），`snippet` 规则截断 ≤300
- 门控：
  - `meta.retrieve_hit_count`
  - `meta.low_confidence=true|false`
  - `meta.low_confidence_reasons[]`
  - `error_code`（P0 必须可归因）
- 引用闭环：
  - 非 debug：返回 `meta.retrieval_hit_id_hashes[]`（前 N≤50）+ limit_n/total + `canonical_hit_id_scheme="kb_chunk_id"`
  - `EVAL_DEBUG`：仅在 debug 模式允许敏感字段；非 debug 严禁明文 hit_ids

#### 你必须遵守的口径（硬要求）

- 对外字段命名必须与 `p0-execution-map.md` 附录 C 完全一致（snake_case，尤其 `latency_ms`）
- error_code 必须用附录 D 枚举，不要自造新码
- 引用闭环口径：sources 构造与 hashed membership 必须基于同一“候选集前 N”口径

#### 你如何自验收（必须提供证据）

- 用 eval 对 Vagent 跑一轮 dataset：
  - `CONTRACT_VIOLATION = 0`
  - 引用闭环不误报（否则给出 debug 复现与 canonical/前 N 口径排查）
- 对 3 类 case 现场可复现（发响应样例）：
  - requires_citations=true → sources 非空且闭环可验
  - rag/empty → `low_confidence=true` + 合法 behavior + 合法 error_code
  - 非 EVAL_DEBUG → 不返回敏感 debug 字段

#### 你每天要返回给组长什么（固定格式）

- 今日完成项（对照 A2/B1 第几条）：
- 新增/修改的接口字段（如有）：
- 一轮 run 结果摘要（从 D 的日报中引用即可）：
  - regressions 里属于 Vagent 的 case_id 列表：
  - 每个 case 的拟修复点（文件/模块级）：
- 明日计划：

---

### 7.3 员工 C（travel-ai 负责人）prompt（可直接转发）

你是本项目 **travel-ai target P0 负责人**。目标是在 2 周内把 travel-ai 从“单链路 RAG”升级为“可控 Agent（线性阶段）”，并可被 eval 稳定回归。

#### 你要做什么（SSOT）

- **唯一施工单**：`plans/p0-execution-map.md`（只看 A3/附录 C/附录 E）
- **参考规格**：`plans/travel-ai-upgrade.md`（P0 章节）
- **判定规则**：`plans/eval-upgrade.md`

按 `p0-execution-map.md` 的 **A3** 完成：

- 实现 `POST /api/v1/eval/chat`（非流式、snake_case）
- 固定线性阶段：`PLAN→RETRIEVE→TOOL→WRITE→GUARD`（P0 禁止 DAG/回环/并行工具）
- 返回可验收 meta：
  - `meta.stage_order[]`
  - `meta.step_count`
  - `meta.replan_count=0`
  - `meta.plan_parse_attempts`
  - `meta.plan_parse_outcome`
- PlanParser：
  - 以 `p0-execution-map.md` **附录 E** 为 schema SSOT（tolerant parse + repair once + 回显限制）
- 工具调用：
  - 串行 + tool timeout + total timeout + 降级矩阵覆盖（不得异常退出）

#### 你必须遵守的口径（硬要求）

- `p0-execution-map.md` 附录 E 是 Plan schema 单一事实来源，不要另起 schema
- 对外字段命名与附录 C 完全一致（snake_case，`latency_ms`）
- P0 禁止 replan loop：`meta.replan_count` 必须为 0

#### 你如何自验收（必须提供证据）

- 给出 1 个正常 plan 与 1 个“坏 plan”（触发 repair once）的复现：
  - 响应里 `plan_parse_attempts/outcome` 可观察
  - 修复提示不泄露用户文本/工具输出/KB 片段（符合回显限制）
- eval 跑一轮 dataset：
  - `stage_order/step_count/replan_count` 在 report 中可统计、且无“控制流异常导致 UNKNOWN”高占比

#### 你每天要返回给组长什么（固定格式）

- 今日完成项（对照 A3 第几条）：
- 线性阶段观测证据（贴 1 个响应 meta 示例即可）：
- regressions 中归属 travel-ai 的 case_id 列表 + 修复点：
- 明日计划：

---

### 7.4 员工 D（Dataset/回归/集成负责人）prompt（可直接转发）

你是本项目 **dataset/回归/集成负责人**。目标是在 2 周内构建可验收的数据集与回归节奏，让团队每天按 regressions 列表推进。

#### 你要做什么（SSOT）

- **唯一施工单**：`plans/p0-execution-map.md`（关注 A1/A2/A3 的验收口径引用）
- **唯一判定规则**：`plans/eval-upgrade.md`（dataset schema、attack/*、统计口径、tool_policy 规则）

你需要交付：

- P0 dataset（≥30 case），并保证：
  - `attack/*` ≥8（覆盖 prompt injection / source poisoning / tool output injection）
  - `rag/empty`、`rag/low_conf` 分组可统计
  - P0 门槛统计默认只纳入 `tool_policy=stub|disabled`（`real` 单列不计硬门槛）
- dataset 版本化与变更记录（避免漂移污染 compare）
- 每日回归节奏：
  - 组织 run（或推动 A 跑）
  - 产出回归日报
  - regressions 按 error_code 聚类并分配 owner

#### 你如何自验收（必须提供证据）

- dataset 导入成功，run 可重复执行，结果不因 dataset 漂移而不可解释
- `attack/*` 在 report 中可单独统计通过率
- 每天的日报能把 regressions 分配到 A/B/C 并给出复现信息（case_id + 期望）

#### 你每天要返回给组长什么（固定格式：回归日报）

每天 17:30 前发：

- `dataset_version`：
- `run_id`：
- `run.report` 摘要：
  - pass_rate=
  - skipped_rate=
  - p95_latency_ms=
  - top_error_codes=[...]
- `compare` 摘要（base vs cand）：
  - regressions=[{case_id, error_code, owner(A/B/C), next_action}]
  - improvements=[case_id...]
- 今日结论（是否接近 P0 门槛，最大阻塞项是什么）：

---

### 7.5 Leader prompt（换会话时发给我/协助者，保证精准）

你是本项目的执行助理/技术 PM + 架构审校。请在不跑偏的前提下，协助我推进“两周 P0 可回归闭环”。

#### 目标（不可更改）

- 两周内交付 P0：eval + 两个 target（Vagent、travel-ai）可跑同一 dataset（≥30 case），并输出可对比报告；安全边界与引用闭环可验收。

#### 唯一事实来源（SSOT）

- 施工单：`plans/p0-execution-map.md`
- 判定规则：`plans/eval-upgrade.md`
- 组长执行手册：`plans/leadership-execution.md`
- Vagent 规格：`plans/vagent-upgrade.md`
- travel-ai 规格：`plans/travel-ai-upgrade.md`

#### 必须遵守的统一契约/口径

- 对外 JSON 一律 snake_case；核心字段：`latency_ms`、`ttft_ms`（如存在）
- HTTP Headers 对外契约一律 `X-Eval-*`
- `error_code` 枚举以 `p0-execution-map.md` 附录 D 为准
- 引用闭环：hashed membership（前 N≤50）+ canonical id scheme 固定（`kb_chunk_id`）
- `EVAL_DEBUG`：非 debug 禁止敏感字段；违规判 `SECURITY_BOUNDARY_VIOLATION`
- travel-ai：P0 禁止 DAG/回环/并行工具；`replan_count` 必须为 0

#### 我会提供给你的输入（你要怎么用）

我可能只会给你：

- `run.report` 摘要
- `compare`（regressions/improvements 列表）
- 1–2 个失败 case 的 target 响应样例（含 meta/capabilities/error_code/sources/tool）

你需要输出：

- 是否达标（对照 P0 门槛与契约）
- regressions 的归因与 owner 建议（A/B/C/D）
- 下一步最小修复动作（优先级最高的 3–5 条）
- 任何“口径漂移/字段命名不一致/安全边界风险”的拦截提醒

---

## 8. 每日指令库（Day1～Day10，组长复制粘贴用，门控推进）

> 用法：你每天只发“对应角色 + 对应 Day”的一段指令。  
> **门控规则（必须写在每条指令里）**：除非我明确回复 `PASS DayN`，否则你不得开始 DayN+1 的任何工作。

### 8.0 通用门控指令模板（你可选用）

把下面模板粘到每日指令开头（建议保留 TODAY_TOKEN）：

- TODAY_TOKEN：`<YYYY-MM-DD>-<ROLE>-D<N>`
- 今日目标：……
- 允许参考（SSOT）：`plans/p0-execution-map.md`、`plans/eval-upgrade.md`（以及角色对应的方案文档）
- 禁止事项：
  - 不得改契约字段名（snake_case、`latency_ms`、Header=`X-Eval-*`）
  - 不得进入 DayN+1
- 必须交付证据（不齐不算完成）：……
- 返回格式：按你角色 prompt 的固定格式
- 门控：我回复 `PASS DayN` 才能进入下一天；否则补齐证据

---

### 8.1 员工 A（Eval）每日指令库

#### A / Day1

- TODAY_TOKEN：`<DATE>-A-D1`
- 今日目标：eval 项目骨架与配置跑通（能启动 + 读取 targets 配置 + 最小健康检查）。
- 允许参考（SSOT）：`plans/p0-execution-map.md`（A1）、`plans/eval-upgrade.md`（安全边界与契约）。
- 必须交付证据：
  - 启动成功的最小日志片段（不含敏感信息）
  - 当前 target 配置示例（脱敏后：targetId + baseUrl 形态 + 是否启用）
  - 你认为 Day2 需要的 dataset 导入接口/格式草案（要与 `eval-upgrade.md` 一致）
- 返回格式：按 7.1 的“每日返回格式”。
- 门控：我回复 `PASS Day1` 才能进入 Day2。

#### A / Day2

- TODAY_TOKEN：`<DATE>-A-D2`
- 今日目标：Dataset 导入闭环（JSONL/CSV）+ dataset 列表/查询，能导入 ≥30 case。
- 必须交付证据：
  - dataset 导入示例（字段包含 question/expected_behavior/requires_citations/tags）
  - 导入后 dataset 查询结果截图/JSON 片段（含 case 数量）
  - 任意 2 条 case 的解析后结构化结果（展示字段规范化）
- 门控：`PASS Day2` 才进入 Day3。

#### A / Day3

- TODAY_TOKEN：`<DATE>-A-D3`
- 今日目标：Run 串行执行（创建 run → 串行跑完所有 case → 记录每条结果的 PASS/FAIL/SKIPPED + error_code）。
- 必须交付证据：
  - `run_id`
  - 至少 5 条结果样例（含 case_id、判定、error_code、latency_ms）
  - 取消 run 的行为说明（取消后状态如何体现）
- 门控：`PASS Day3` 才进入 Day4。

#### A / Day4

- TODAY_TOKEN：`<DATE>-A-D4`
- 今日目标：Target 调用契约对齐：能调用并解析两个 target 的 `POST /api/v1/eval/chat`，并对缺字段做 `CONTRACT_VIOLATION`。
- 必须交付证据：
  - 对 Vagent 与 travel-ai 各 1 个成功响应的解析样例（含 answer/behavior/latency_ms/capabilities/meta）
  - 对 1 个“缺字段/类型错”的响应，判定为 `CONTRACT_VIOLATION` 的证据
- 门控：`PASS Day4` 才进入 Day5。

#### A / Day5

- TODAY_TOKEN：`<DATE>-A-D5`
- 今日目标：P0 判定器 v1（expected_behavior + requires_citations + SKIPPED_UNSUPPORTED）。
- 必须交付证据：
  - 3 条 case 的判定解释（各覆盖：expected_behavior、requires_citations、SKIPPED_UNSUPPORTED）
  - 规则版本标识（你在 eval 内部如何版本化规则）
- 门控：`PASS Day5` 才进入 Day6。

#### A / Day6

- TODAY_TOKEN：`<DATE>-A-D6`
- 今日目标：引用闭环 hashed membership 校验落地（稳定判 `SOURCE_NOT_IN_HITS`）。
- 必须交付证据：
  - 1 个 PASS 的 requires_citations case（展示 membership 验证通过）
  - 1 个 FAIL 的构造 case（展示 membership 验证失败 → `SOURCE_NOT_IN_HITS`）
  - canonical id scheme 与候选集前 N 口径的说明（与 SSOT 对齐）
- 门控：`PASS Day6` 才进入 Day7。

#### A / Day7

- TODAY_TOKEN：`<DATE>-A-D7`
- 今日目标：`run.report` v1（pass_rate/skipped_rate/p95 latency_ms/error_code TopN）。
- 必须交付证据：
  - 一份 `run.report` JSON/markdown 摘要（包含上述字段）
  - 你如何计算 p95 latency_ms 的说明（边界条件）
- 门控：`PASS Day7` 才进入 Day8。

#### A / Day8

- TODAY_TOKEN：`<DATE>-A-D8`
- 今日目标：`compare` v1（base vs cand）：pass_rate_delta + regressions/improvements。
- 必须交付证据：
  - 一份 compare 输出样例
  - regressions 列表至少包含 case_id，并可链接/查询到该 case 的详细结果
- 门控：`PASS Day8` 才进入 Day9。

#### A / Day9

- TODAY_TOKEN：`<DATE>-A-D9`
- 今日目标：安全边界与审计：enabled/CIDR/token-hash/审计 reason；EVAL_DEBUG 违规判定。
- 必须交付证据：
  - enabled=false 时的行为（返回 404 或等价）与审计 reason=DISABLED
  - token 错误 → 401/403 与审计 reason
  - 非 EVAL_DEBUG 出现敏感字段 → `SECURITY_BOUNDARY_VIOLATION`
- 门控：`PASS Day9` 才进入 Day10。

#### A / Day10

- TODAY_TOKEN：`<DATE>-A-D10`
- 今日目标：稳定性与演示：一键跑一轮并导出 report/compare；列出已知限制与下一步。
- 必须交付证据：
  - 1 次完整 run 的 report + compare（脱敏）
  - 已知限制列表（P0 未覆盖/风险）
- 门控：`PASS Day10` 结束 A 线 P0。

---

### 8.2 员工 B（Vagent）每日指令库

#### B / Day1

- TODAY_TOKEN：`<DATE>-B-D1`
- 今日目标：实现 `POST /api/v1/eval/chat` 骨架（非流式 + snake_case + Header=`X-Eval-*`）。
- 允许参考：`plans/p0-execution-map.md`（A2/B1/C）、`plans/vagent-upgrade.md`（P0）。
- 必须交付证据：
  - 1 个最小请求/响应样例（含 answer/behavior/latency_ms/capabilities/meta）
  - 说明你从哪里读取并校验 `X-Eval-Token`（enabled/禁用行为遵循 SSOT）
- 门控：`PASS Day1` 才进入 Day2。

#### B / Day2

- TODAY_TOKEN：`<DATE>-B-D2`
- 今日目标：`sources[]` 服务端生成 + `snippet` 截断 ≤300（LLM 不产 sources）。
- 必须交付证据：
  - requires_citations=true 的响应样例（sources 至少 1 条，含 id/title/snippet）
  - snippet 截断策略说明（规则截断，不是 LLM 摘要）
- 门控：`PASS Day2` 才进入 Day3。

#### B / Day3

- TODAY_TOKEN：`<DATE>-B-D3`
- 今日目标：空命中/低置信门控输出可统计：`retrieve_hit_count/low_confidence/low_confidence_reasons/error_code`。
- 必须交付证据：
  - rag/empty 场景响应样例（`retrieve_hit_count=0`、`low_confidence=true`、合法 behavior + error_code）
  - rag/low_conf 场景响应样例（reasons[] 非空）
- 门控：`PASS Day3` 才进入 Day4。

#### B / Day4

- TODAY_TOKEN：`<DATE>-B-D4`
- 今日目标：引用闭环排障路径准备好（EVAL_DEBUG 可排障；非 debug 不泄露）。
- 必须交付证据：
  - EVAL_DEBUG 模式响应样例（仅在 debug 下允许敏感字段）
  - 非 debug 模式响应样例（证明不含敏感字段）
- 门控：`PASS Day4` 才进入 Day5。

#### B / Day5

- TODAY_TOKEN：`<DATE>-B-D5`
- 今日目标：hashed membership（前 N≤50）+ canonical scheme 固定（`kb_chunk_id`）。
- 必须交付证据：
  - 非 debug 响应样例（含 `retrieval_hit_id_hashes[]`、limit_n/total、canonical_hit_id_scheme）
  - N 与 sources 构造同口径说明
- 门控：`PASS Day5` 才进入 Day6。

#### B / Day6

- TODAY_TOKEN：`<DATE>-B-D6`
- 今日目标：EVAL_DEBUG 边界固化：非 debug 严禁明文 hit_ids；违规应能被判定。
- 必须交付证据：
  - 非 debug 响应样例（确认无明文 hit_ids）
  - 你如何保证“debug 只有在满足条件才开启”（开关/鉴权/allowlist）
- 门控：`PASS Day6` 才进入 Day7。

#### B / Day7

- TODAY_TOKEN：`<DATE>-B-D7`
- 今日目标：一次性 Reflection（如启用）可观测：`guardrail_triggered` + 可归因 error_code；不循环。
- 必须交付证据：
  - 1 个触发 guardrail 的响应样例（meta 字段齐全）
  - 反思失败/解析失败的降级路径（如适用）说明
- 门控：`PASS Day7` 才进入 Day8。

#### B / Day8

- TODAY_TOKEN：`<DATE>-B-D8`
- 今日目标：与 eval 集成跑通（≥30 case），修掉契约/字段问题，`CONTRACT_VIOLATION=0`。
- 必须交付证据：
  - 由 D 或 A 提供的 run.report 摘要引用 + 你负责的 regressions 修复列表
- 门控：`PASS Day8` 才进入 Day9。

#### B / Day9

- TODAY_TOKEN：`<DATE>-B-D9`
- 今日目标：attack/* 用例加固（通过率提升，失败归因不落 UNKNOWN）。
- 必须交付证据：
  - attack 分组通过率（从 report 中引用）
  - 仍失败的 attack case_id + 下一步修复点
- 门控：`PASS Day9` 才进入 Day10。

#### B / Day10

- TODAY_TOKEN：`<DATE>-B-D10`
- 今日目标：P0 过线收敛：输出剩余风险与清单化修复计划。
- 必须交付证据：
  - 当前 pass_rate 与关键门槛项是否达标（引用 report）
  - “剩余 fail/regressions → 修复点 → 预计时间”清单
- 门控：`PASS Day10` 结束 B 线 P0。

---

### 8.3 员工 C（travel-ai）每日指令库

#### C / Day1

- TODAY_TOKEN：`<DATE>-C-D1`
- 今日目标：实现 `POST /api/v1/eval/chat` 骨架（非流式 + snake_case）。
- 允许参考：`plans/p0-execution-map.md`（A3/C/E）、`plans/travel-ai-upgrade.md`（P0）。
- 必须交付证据：
  - 1 个最小请求/响应样例（含 answer/behavior/latency_ms/capabilities/meta）
- 门控：`PASS Day1` 才进入 Day2。

#### C / Day2

- TODAY_TOKEN：`<DATE>-C-D2`
- 今日目标：串行阶段骨架：固定 `PLAN→RETRIEVE→TOOL→WRITE→GUARD`（P0 禁止 DAG/回环/并行工具）。
- 必须交付证据：
  - 代码/流程说明：如何保证固定顺序（概念层即可）
  - 1 个响应 meta 样例（含 stage_order 初版）
- 门控：`PASS Day2` 才进入 Day3。

#### C / Day3

- TODAY_TOKEN：`<DATE>-C-D3`
- 今日目标：meta 可观测稳定：`stage_order[]/step_count/replan_count=0`。
- 必须交付证据：
  - 2 个不同输入的响应 meta（证明 step_count 与 stage_order 合理）
  - replan_count 固定为 0 的证据
- 门控：`PASS Day3` 才进入 Day4。

#### C / Day4

- TODAY_TOKEN：`<DATE>-C-D4`
- 今日目标：PlanParser 接入附录 E schema（tolerant parse）。
- 必须交付证据：
  - 1 个符合 schema 的 plan 解析成功证据（plan_version=v1 等）
  - schema 以附录 E 为 SSOT 的引用说明（不要另起 schema）
- 门控：`PASS Day4` 才进入 Day5。

#### C / Day5

- TODAY_TOKEN：`<DATE>-C-D5`
- 今日目标：repair once：解析失败最多修复一次；输出 `plan_parse_attempts/outcome`。
- 必须交付证据：
  - 1 个“坏 plan”触发 repair once 的复现（attempts=2，outcome=repaired 或 failed）
  - 回显限制说明：修复提示不泄露用户/工具/KB 文本
- 门控：`PASS Day5` 才进入 Day6。

#### C / Day6

- TODAY_TOKEN：`<DATE>-C-D6`
- 今日目标：工具串行 + 超时 + 降级矩阵覆盖（失败不崩，有 error_code）。
- 必须交付证据：
  - 1 个工具成功 case 响应样例（tool.used=true 时的 meta/字段）
  - 1 个工具超时/失败 case 响应样例（仍正常结束，带 error_code）
- 门控：`PASS Day6` 才进入 Day7。

#### C / Day7

- TODAY_TOKEN：`<DATE>-C-D7`
- 今日目标：空命中/低置信门控（P0 不启用 score 阈值），并可统计 reasons。
- 必须交付证据：
  - rag/empty 或低置信响应样例（low_confidence=true + reasons[] 非空）
- 门控：`PASS Day7` 才进入 Day8。

#### C / Day8

- TODAY_TOKEN：`<DATE>-C-D8`
- 今日目标：与 eval 集成跑通（≥30 case），修掉契约/字段/边界问题。
- 必须交付证据：
  - run.report 摘要引用 + 你负责的 regressions 修复列表
- 门控：`PASS Day8` 才进入 Day9。

#### C / Day9

- TODAY_TOKEN：`<DATE>-C-D9`
- 今日目标：注入/解析鲁棒性：prompt/工具回传注入、plan 失败路径稳定可控（归因不漂移）。
- 必须交付证据：
  - 2 个对抗/失败路径 case_id（从 dataset 中引用）+ 当前行为是否符合 expected_behavior
- 门控：`PASS Day9` 才进入 Day10。

#### C / Day10

- TODAY_TOKEN：`<DATE>-C-D10`
- 今日目标：P0 过线收敛：输出剩余 regressions、修复策略与风险。
- 必须交付证据：
  - pass_rate/关键门槛项是否达标（引用 report）
  - “剩余 regressions → 修复点 → 风险”清单
- 门控：`PASS Day10` 结束 C 线 P0。

---

### 8.4 员工 D（Dataset/回归/集成）每日指令库

#### D / Day1

- TODAY_TOKEN：`<DATE>-D-D1`
- 今日目标：P0 dataset v0（≥30 case）初版，字段齐全、可导入。
- 允许参考：`plans/eval-upgrade.md`（dataset schema）、`plans/p0-execution-map.md`（门槛口径）。
- 必须交付证据：
  - dataset 版本号（例如 v0）
  - case 数量统计 + tags 分布摘要
  - 至少 3 条 case 示例（覆盖不同 expected_behavior）
- 门控：`PASS Day1` 才进入 Day2。

#### D / Day2

- TODAY_TOKEN：`<DATE>-D-D2`
- 今日目标：attack/* 套件：≥8 条，对抗覆盖三类注入。
- 必须交付证据：
  - attack case 列表（case_id + tag + 期望行为）
  - 覆盖说明（prompt injection / source poisoning / tool output injection）
- 门控：`PASS Day2` 才进入 Day3。

#### D / Day3

- TODAY_TOKEN：`<DATE>-D-D3`
- 今日目标：rag/empty 与 rag/low_conf 分组完善（可统计、期望清晰）。
- 必须交付证据：
  - 分组 case 列表 + 期望行为说明
  - 统计口径说明（哪些算 empty/low_conf，如何判）
- 门控：`PASS Day3` 才进入 Day4。

#### D / Day4

- TODAY_TOKEN：`<DATE>-D-D4`
- 今日目标：tool_policy 规划：P0 门槛统计只纳入 stub/disabled；real 单列。
- 必须交付证据：
  - dataset 中 tool_policy 分布
  - 哪些 case 属于 P0 门槛统计（列表或规则）
- 门控：`PASS Day4` 才进入 Day5。

#### D / Day5

- TODAY_TOKEN：`<DATE>-D-D5`
- 今日目标：跑第一轮基线（允许失败很多，但必须可归因）。
- 必须交付证据（回归日报 v0）：
  - run_id
  - run.report 摘要（pass_rate、p95 latency_ms、top error_codes）
  - 若有 compare：regressions 列表
- 门控：`PASS Day5` 才进入 Day6。

#### D / Day6

- TODAY_TOKEN：`<DATE>-D-D6`
- 今日目标：回归分配机制：regressions 按 error_code 聚类并指派 owner。
- 必须交付证据：
  - regressions 列表（每条含 case_id、error_code、owner、next_action）
- 门控：`PASS Day6` 才进入 Day7。

#### D / Day7

- TODAY_TOKEN：`<DATE>-D-D7`
- 今日目标：dataset 版本化与变更记录（避免漂移）。
- 必须交付证据：
  - dataset 版本号升级策略
  - 变更记录样例（新增/修改/删除 case 的说明）
- 门控：`PASS Day7` 才进入 Day8。

#### D / Day8

- TODAY_TOKEN：`<DATE>-D-D8`
- 今日目标：回归日报模板固化并连续产出。
- 必须交付证据：
  - 回归日报模板（固定字段）
  - 当天一份完整日报（按模板）
- 门控：`PASS Day8` 才进入 Day9。

#### D / Day9

- TODAY_TOKEN：`<DATE>-D-D9`
- 今日目标：定位质量提升：减少“不可判定/口径不清”的 case。
- 必须交付证据：
  - 你修订的 case 列表 + 修订原因（比如 expected_behavior 不清晰）
  - 修订前后对 report/compare 的影响预期
- 门控：`PASS Day9` 才进入 Day10。

#### D / Day10

- TODAY_TOKEN：`<DATE>-D-D10`
- 今日目标：P0 验收包：一页结论（是否过线 + 阻塞项 + owner）。
- 必须交付证据：
  - “是否过线”结论（对照 P0 门槛）
  - top 阻塞项列表（每项含 owner 与预计修复时间）
- 门控：`PASS Day10` 结束 D 线 P0。

---

## 9. 每日二次验收（LeaderAI）prompt

把下面整段复制给 LeaderAI，再在末尾粘贴当日材料（日报 + 样例 JSON）。

```text
你是本项目的二线验收（LeaderAI）。目标是对当天 DayN 的交付做二次把关，防止口径漂移与隐藏返工风险。
SSOT：plans/p0-execution-map.md、plans/eval-upgrade.md、plans/leadership-execution.md。
硬口径：对外 JSON snake_case（含 latency_ms），Header=X-Eval-*，error_code 枚举以 p0-execution-map.md 附录 D 为准；引用闭环 hashed membership（前 N≤50 + canonical scheme 固定）；非 EVAL_DEBUG 禁止敏感字段（违规=SECURITY_BOUNDARY_VIOLATION）；travel-ai P0 replan_count=0。
我会提供：dataset_version、run_id、run.report 摘要、compare 摘要、2 个 target 响应样例（1 失败 1 通过）。
请输出：
1) DayN 结论：PASS/FAIL
2) FAIL 的具体原因（按证据指向 SSOT 条款）
3) regressions 分配建议（A/B/C/D）
4) 下一步最小修复清单（Top 3-5）
5) 任何安全边界/契约一致性的红旗提醒。
以下是今日材料：
（粘贴日报与样例 JSON）
```

---

## 10. P0 后收口（勿忘）：SSE 与 eval 门控单一事实来源

> **问题**：`RagStreamChatService`（真人 SSE）与 `EvalChatController`（评测 JSON）在 P0 可能各写一套「空命中 / 低置信 / error_code」，易**规则漂移**。

- **已写入升级计划**：`plans/vagent-upgrade.md` 的 **P1-0 门控与评测对齐（单一事实来源，防 SSE / eval 漂移）**。
- **执行时机**：Vagent **P0 过线后**优先排期本项（早于或并行于 CRAG 等质量增强，按组长取舍）。
- **组长动作**：P0 `PASS Day10` 后，在第一次迭代计划里**显式勾选 P1-0**，并指定 owner（建议 B / Vagent 负责人）。
