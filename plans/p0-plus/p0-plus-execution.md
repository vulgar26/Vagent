# P0+ 强制执行手册（契约 · 归因 · 题集对齐 · 对比运营）

> **文档性质**：项目组 **必须遵守** 的执行单与验收清单；与「建议」「可选」不同，文中标明 **【强制】** 的条目 **未满足即视为该冲刺未交付**，组长可 **拒收** 并 **打回重做**。  
> **版本**：v1.5（**§24** Vagent：`sources_count` 全 0、灌库 vs 空命中门控分桶；v1.4 及更早见版本注记）  
> **对照 SSOT**：`plans/eval-upgrade.md`、`plans/p0-execution-map.md`、`plans/leadership-execution.md`、`plans/datasets/p0-d10-p0-acceptance-onepager.md`  
> **适用范围**：P0-A（回归基建）已收口之后，进入 **P0+**；**未通过 P0+ 出口门禁前，禁止启动** `vagent-upgrade.md` / `travel-ai-upgrade.md` / `eval-upgrade.md` 中已标为 **P1** 的能力（见 §9）。

---

## 1. 为什么需要本文档

1. **现象**：各组易将「接口能调通」「能跑满 32 题」等同于 **完成**，在 **契约字段、失败归因、题集与能力匹配** 上偷工减料，导致 `UNKNOWN` 高企、`CONTRACT_VIOLATION` 长期存在、报表不可运营。  
2. **目标**：P0+ 把 **可回归** 升级为 **可治理**：失败可归类、契约可单测、对比可分桶、责任可分配。  
3. **执行原则**：**少并行、多证据、门禁硬**；每一冲刺结束必须提交 **可复查 artifacts**，不得仅用口头汇报替代。

---

## 2. 术语与阶段划分（必须统一口径）

| 名称 | 含义 |
|------|------|
| **P0-A** | 已完成的 P0 **工程闭环**：dataset 导入、eval run/report、双 target 可跑满、日报/分锅机制、收口文档。 |
| **P0-B** | **数值与契约质量线**：在 **冻结 dataset 版本** 上，双 target 达到 **S1～S3 各冲刺出口门禁**（§6.5、§7.4、§8.3）及 **§10 总出口** 中与契约、`UNKNOWN` 相关的条件（**勿**将仅 §8 等同于整段 P0-B；§8 仅为 S3）。 |
| **P0+** | **从 P0-A 走到 P0-B 的强制阶段**；本文档描述其 **唯一合法执行路径**。 |
| **P1** | `plans/*-upgrade.md` 中 **P1 章节** 的能力增强；**P0+ 未出口前默认禁止**（§9）。 |

---

## 3. 角色与责任矩阵（【强制】）

| 代号 | 角色 | P0+ 内 **不可推卸** 的责任 |
|------|------|---------------------------|
| **组长** | 裁决、门禁签字、范围裁剪（**UNKNOWN 等数值阈值**仅组长可改，见 **§4.1**；冲刺出口见 **§6.5 / §7.4 / §8.3**） | 接收/拒收冲刺交付；冻结 dataset 版本变更；冲突时拍板 SKIP/分集 |
| **A** | Eval 负责人 | UNKNOWN 映射、violation/mismatch 可观测、compare、分桶报表或等价脚本、`tool_policy` 与判定一致 |
| **B** | Vagent 负责人 | `POST /api/v1/eval/chat` **全分支契约**、单测、全量跑 **CONTRACT=0** |
| **C** | travel-ai 负责人 | 同 B，针对 travel-ai target |
| **D** | Dataset/回归/集成 | changelog、题集与能力对齐清单、日报模板绑定 compare/分桶、版本冻结执行 |

**偷工减料判定（【强制】）**：下列任一情形，组长可 **直接拒收** 该冲刺交付，无需讨论：

- 未提供 **§6.1** 规定的 `run_id` + `run.report` 摘录（或等价落库导出）。  
- 声称「已修契约」但 **无** 新增/更新的 **契约单测** 或 **JSON Schema/校验器** 证据。  
- 将大量 case 改为 PASS **仅靠改题** 而无 **changelog 条目 + case_id 列表 + 原因**。  
- **未** 在冻结 dataset 版本上重跑全量就宣称达标。  
- 把 **P1 功能**（CRAG、Multi-Agent、并行工具等）混入 PR 标题/范围却声称在做 P0+。

---

## 4. 冻结物与变更纪律（【强制】）

### 4.1 Dataset 版本

- **【强制】** P0+ 全程默认冻结：**`plans/datasets/p0-dataset-v0.jsonl` 的逻辑版本以 `p0-dataset-changelog.md` 当前最新 `v0.x` 为准**（截至 P0+ 启动日由组长写入下表）。  
- **【强制】** P0+ 启动时填写：

| 项 | 值（启动时由组长填写） |
|----|------------------------|
| **P0+ 冻结 dataset 逻辑版本** | 例：`v0.2` |
| **冻结 Git 引用** | 例：commit / tag：________ |
| **eval_rule_version（若适用）** | 例：与当时 **`RunEvaluator.EVAL_RULE_VERSION`** 一致（当前仓库多为 `p0.v2`；规则变更 PR 后递增） |
| **S1 UNKNOWN 阈值（组长签字）** | 例：`≤ 8` 或 `≤ 12`（须与 §6.5 一致） |

- **【强制】** P0+ 期间若必须改题：  
  1. 走 `p0-dataset-changelog.md` **新增小节**，点名 **case_id** 与影响；  
  2. **升 patch/次版本** 按现有版本策略；  
  3. **全量重跑** Vagent + travel-ai 两条 run，并 **新 compare**（相对 **改题前** 的 `run_id` 需在日报说明）。  

### 4.2 Target 与 Eval 配置

- **【强制】** 每次全量跑须在日报中记录：**target baseUrl、profile、是否启用 token-hash、eval 版本/commit**。  
- **【强制】** 本地与 CI **不得** 用「关闭校验」糊弄契约；若环境限制，必须在日报 **显式标注**「与生产不一致项」，且 **CONTRACT 清零验收必须在「与契约一致」的配置上完成**。

---

## 5. P0+ 三冲刺结构（总览）

| 冲刺 | 名称 | 核心交付 | 最短建议工期 |
|------|------|----------|----------------|
| **S1** | 归因与契约 | UNKNOWN↓、CONTRACT→0（分 target） | 5 个工作日 |
| **S2** | 题与能力对齐 | SKIP/分集/changelog、capabilities 诚实 | 3～5 个工作日 |
| **S3** | 对比与分桶运营 | **compare v1 已在 eval 仓实现**（`RunCompareService`）；本冲刺主打 **固定调用/存档** + **按 `tags` 分桶报表** + 日报模板 | 3～5 个工作日 |

**【强制】** 冲刺 **不得跳步**：S2 须在 S1 **双 target CONTRACT=0** 后启动（否则 UNKNOWN/CONTRACT 与题意纠缠无法分责）。若组长书面批准 **风险承担方**，可记录例外一次。

---

## 6. 冲刺 S1：归因与契约（详细）

### 6.1 【强制】每轮验收必交 artifacts（缺一不可）

1. **Vagent 全量 run**  
   - `run_id`：________  
   - `report_version`：`run.report.v1`（或当前正式版本）  
   - `total_cases` / `completed_cases`（须相等）  
   - `pass_count` / `fail_count` / `skipped_count`  
   - `error_code` TopN **全文**  
   - `p95_latency_ms`  
2. **travel-ai 全量 run**（同上结构）  
3. **A 须另附**：  
   - **UNKNOWN 映射表**（Markdown 表格）：`触发条件（eval 内部）` → `新 error_code（须 ∈ SSOT 或经组长批准新增）` → `是否已实现`  
   - 至少 **3 条** Day10 基线中为 UNKNOWN 的 `case_id`，在 **修复后** 的 `eval_result.debug` 中应能看到 **非 UNKNOWN** 的顶层码或 **明确子原因字段**（截图或 JSON 片段，脱敏）

### 6.2 【强制】A（Eval）工作项

| # | 工作项 | 完成定义 |
|---|--------|----------|
| A1 | 结构化校验失败 | HTTP 非 2xx、body 非 JSON、缺顶层必填 → **不得** 长时间落在 UNKNOWN；须映射到 `CONTRACT_VIOLATION` 或专码（与 `eval-upgrade` 一致） |
| A2 | behavior 与 expected 不一致 | 须映射到 **`BEHAVIOR_MISMATCH`**（或 SSOT 已列等价码），且 debug 含 `expected_behavior` / `actual_behavior`（或等价） |
| A3 | 解析异常 | 须带 **稳定** `error_code`，禁止静默吞掉变 UNKNOWN |
| A4 | `CONTRACT_VIOLATION` 明细 | **【强制】** `debug` 或日志可查 **缺失字段路径 / 类型错误**，便于 B/C 一次修完 |

### 6.3 【强制】B（Vagent）工作项

| # | 工作项 | 完成定义 |
|---|--------|----------|
| B1 | 全路径响应形状 | 所有分支返回 **相同顶层结构**（`answer`/`behavior`/`latency_ms`/`capabilities`/`meta` 等，以 `eval-upgrade` + `p0-execution-map` 为准） |
| B2 | 契约单测 | **【强制】** 新增或更新测试：**至少覆盖** 正常 answer、空检索、低置信、工具跳过/不支持、EVAL_DEBUG 开关差异（若 P0 有）、auth 失败路径 |
| B3 | 验收数字 | 在 **冻结 dataset** 上全量跑：**`CONTRACT_VIOLATION` 计数 = 0** |

### 6.4 【强制】C（travel-ai）工作项

- 与 §6.3 **同结构**，对象改为 travel-ai 的 `EvalChatController`（或等价入口）。

### 6.5 S1 出口门禁（【强制】，组长签字）

- [ ] Vagent：`CONTRACT_VIOLATION == 0`（该 target 单次全量 run 报告为证）  
- [ ] travel-ai：`CONTRACT_VIOLATION == 0`  
  - **进展留证（2026-04-18，供 C 线勾选时附链接）**：`travel-ai-planner` 已实现 eval-upgrade **E7**（`meta.retrieval_hit_id_hashes[]` + `X-Eval-*` headers）；示例全量 **`run_6106023bf5354e3089cf1d8b7c4421b4`**：**32/32 PASS**，`run.report` 无 `error_code` TopN。归档：`travel-ai-planner/docs/DAY10_P0_CLOSURE.md`；规格交叉索引：`plans/eval-upgrade.md`「travel-ai 联调验收状态」。**最终以组长在冻结 dataset 上的签字 run 为准。**  
- [ ] `UNKNOWN` 计数 ≤ **组长设定阈值**（默认建议：**≤ 8**；若基线极高可先设 **≤ 12**，**必须在启动日写入 §4.1 表**）  
- [ ] A 的映射表已合并到仓库 `docs/` 或 `plans/` **指定路径**（由组长在启动会指定文件名）  

**未全部勾选 → S1 未通过，禁止进入 S2。**

---

## 7. 冲刺 S2：题与能力对齐（详细）

### 7.1 【强制】D + 组长 工作项

| # | 工作项 | 完成定义 |
|---|--------|----------|
| D1 | 能力缺口清单 | 表格列：`case_id` | `当前 target` | `缺口类型（无 KB / 无工具 / 无 RAG / 仅 stub）` | **建议动作（改期望 / SKIP / 拆 suite）** |
| D2 | 组长裁定 | 每一行有 **唯一裁定**，不得留「待定」超过 2 个工作日 |
| D3 | changelog | 每一裁定影响到的 case 在 `p0-dataset-changelog.md` **可追溯** |

### 7.2 【强制】A 工作项

- `tool_policy`、`capabilities`、判定器对 **SKIPPED_UNSUPPORTED** 的处理与 **D1 清单** 一致。  
- **【强制】** 硬门槛分母逻辑与 `p0-dataset-tool-policy.md` **一致**（若 S1 未做，须在 S2 完成）。

### 7.3 【强制】B/C 工作项

- `capabilities` **不得虚报**；与真实行为不一致视为 **S2 不通过**。  
- 对裁定 **SKIP** 的 case：响应须稳定可判定为 SKIP（非随机 FAIL）。

### 7.4 S2 出口门禁（【强制】）

- [ ] D1 清单 **100%** 有裁定且已落地（数据或 eval 规则）  
- [ ] 全量跑：`SKIPPED` 与 `FAIL` 比例符合书面规则（组长抽查 5 条）  
- [ ] 无「宣称支持但系统性 FAIL」的 capabilities 谎言（抽查）  

---

## 8. 冲刺 S3：对比与分桶运营（详细）

### 8.1 【强制】A 工作项

| # | 工作项 | 完成定义 |
|---|--------|----------|
| A5 | compare | 同一 `dataset` 版本、同一 `eval` 版本，输出 **base vs cand** 的 `pass_rate_delta` + **regressions 列表（含 case_id）**。**说明**：`vagent-eval` 已实现 **`RunCompareService` / compare API**（`compare.v1`）；S3 的完成定义侧重 **固定调用方式、周报字段与存档路径**，**不是**从零实现 compare。 |
| A6 | 分桶 | **至少** `tags` 含 `attack/` 与 `rag/empty` 或 `rag/low_conf` 的子通过率（可脚本 + CI artifact，不要求 UI） |

### 8.2 【强制】D 工作项

- 更新 **回归日报模板**：固定字段包含 **两个 run_id**、**compare 摘要**、**attack 子通过率**、**rag 桶子通过率**、**dataset 版本**。

### 8.3 S3 出口门禁（【强制】）

- [ ] 周报可 **只复制模板** 即满足组长最低阅读需求（组长试填一次通过）  
- [ ] 一次 **完整 compare** 记录存档（路径：由组长指定）  

---

## 9. P0+ 期间【禁止】事项（防范围蔓延）

**【强制】** 下列工作在 P0+ **出口前** 默认 **禁止** 合并主线（紧急安全补丁除外，须组长书面批准）：

| 禁止项 | 出处（示例） |
|--------|----------------|
| Vagent CRAG、上下文压缩、记忆增强主功能 | `vagent-upgrade.md` P1-1～P1-3 |
| Vagent ToolRegistry 大规模治理（非 CONTRACT 所必需） | `vagent-upgrade.md` P1-4 |
| travel-ai Multi-Agent、DAG、并行工具、Rerank 主线 | `travel-ai-upgrade.md` P1 |
| Eval Run 持久化/队列大重构、Redis 限流全量 | `eval-upgrade.md` P1（**除非**为消除 UNKNOWN 所必需的 **最小** 落库字段，须组长批） |
| 任何「为了 pass_rate 好看」而 **关闭** 安全校验、**伪造** sources、**泄露** EVAL_DEBUG 敏感字段 | 安全红线 |

---

## 10. P0+ 总出口门禁（宣告进入 P1 的前置条件）

**【强制】** 下列 **全部** 满足后，组长可宣告 **P0+ 结束**，并按 `vagent-upgrade.md` **P1-0** 启动 P1：

1. S1、S2、S3 **出口门禁** 已全部勾选（或组长批准的 **书面豁免** 附在 changelog）。  
2. 双 target 在 **冻结 dataset 版本** 上最近一次全量：**`CONTRACT_VIOLATION = 0`**。  
3. **`UNKNOWN` ≤ 组长设定阈值**（见 §6.5）。  
4. **compare + 分桶** 已跑进 **至少 2 份** 连续周报（证明可运营）。  
5. **P1-0（Vagent 门控 SSOT）** 已排入 **下一迭代第一项**（owner=B，见 `leadership-execution.md` §10）。

---

## 11. 节奏与会议（【强制】最低限度）

| 节奏 | 内容 | 产出 |
|------|------|------|
| **每日**（15 min） | A 报 TopN error_code 变化；B/C 报 CONTRACT 剩余条数 | 站会记录 5 行 |
| **每冲刺末**（60 min） | 对照 §6～8 门禁逐条过 | 组长 **签字/打回** |
| **每周** | D 发布绑定 compare 的周报 | Markdown 文件路径固定 |

---

## 12. 附录 A：日报最小字段（复制用）

```text
日期：
dataset 版本（逻辑 + git）：
eval 版本/commit：
Vagent run_id：  travel-ai run_id：
Vagent：pass_rate=  CONTRACT=  UNKNOWN=  Top3 error_code=
travel-ai：pass_rate=  CONTRACT=  UNKNOWN=  Top3 error_code=
compare（若有）：pass_rate_delta=  regressions Top3 case_id=
本日主战场（A|B|C|D）：  明日主战场：
阻塞项（owner）：
```

---

## 13. 附录 B：启动会 Checklist（组长主持）

- [ ] 宣读 §3 偷工减料判定  
- [ ] 填写 §4.1 冻结表  
- [ ] 确认 S1 UNKNOWN 阈值数字  
- [ ] 指定 **映射表文档路径** 与 **周报归档路径**  
- [ ] 各 owner 承诺 **冲刺内不启动 §9 禁止项**

---

## 14. 文档维护

- 本文档变更须 **PR + 组长 approve**。  
- 与 `plans/datasets/p0-d10-p0-acceptance-onepager.md` **§4** 交叉引用：P0 后强制阶段以 **本文** 为准。  
- **每日任务与验收可复制块**：`plans/p0-plus-daily-and-acceptance-tables.md`（按 A/B/C/D 与 S1～S3 下发）。  
- **`requires_citations` + `deny` + 空 `sources`**：**§16.7**；验证顺序 **先 A 后 B 全量**（§22）。  
- **Vagent 全卷 `sources_count=0`、大量 `clarify` vs 期望 `answer/deny`**：**§24**（灌库主线 vs 门控决策线）。

---

## 15. 三仓代码地图（P0+ 改哪里）

以下路径以本机常见布局为准：**Vagent** 与 **plans** 在 `D:\Projects\Vagent`；**vagent-eval**、**travel-ai-planner** 与 Vagent **平级**（若不同，组长在启动会写死绝对路径）。

| 仓 | 根目录 | P0+ 必碰模块 |
|----|--------|----------------|
| **Eval（A）** | `D:\Projects\vagent-eval` | `com.vagent.eval.run`：`RunRunner`、`TargetClient`、`RunEvaluator`、`EvalChatContractValidator`、`RunReportService`、`RunCompareService`；`com.vagent.eval.dataset`：`DatasetApi`、`DatasetStore`、`Model.EvalCase` |
| **Vagent（B）** | `D:\Projects\Vagent` | `com.vagent.eval`：`EvalChatController`、`EvalChatResponse`（DTO）、`EvalApiProperties`；必要时 `SecurityConfig` |
| **travel-ai（C）** | `D:\Projects\travel-ai-planner` | `com.travel.ai.eval`：`EvalChatController`、`EvalChatService`；`dto.EvalChatResponse`、`EvalChatMeta` |

---

## 16. 根因与源码对应（必须先读再改）

下文若出现 **行号**，仅作当前仓库快照的速查锚点；以 **类名 + 分支语义** 为准，避免合并后行号漂移误导执行。

### 16.1 为何报表里大量 `UNKNOWN`（eval 仓）

**文件**：`vagent-eval/src/main/java/com/vagent/eval/run/RunEvaluator.java`

- **行为不一致**：`expected_behavior` 与响应 `behavior` 不等时，代码设置 `verdict_reason=behavior_mismatch`，但 **`ErrorCode` 使用 `UNKNOWN`**（`evaluate` 内 behavior 比对失败分支）。  
- **工具未满足**：`expected_behavior=tool` 且 `tool` 块不满足 `required&&used&&succeeded` 时，同样 **`UNKNOWN`**（`evaluate` 内 tool 判定失败分支）。

**结论（P0+ 强制改法）**：为上述两条路径 **新增专用枚举**（如 `BEHAVIOR_MISMATCH`、`TOOL_EXPECTATION_NOT_MET`），并写入 `RunModel.ErrorCode`；**禁止**继续用 `UNKNOWN` 表示「已判明是 mismatch/tool」。

**文件**：`vagent-eval/.../RunRunner.java` → `runOneCase`

- **控制流事实（源码复核）**：`try` 内 **所有**成功/失败分支均 **提前 `return`**，不会落到方法末尾的统一 `return`。  
- **`catch` 路径**：`HttpTimeoutException` 置 `TIMEOUT`，其余 `Exception` 置 `UPSTREAM_UNAVAILABLE`，随后落到末尾 `return`；此时 **`errorCode` 已非 null**，故表达式 `errorCode == null ? … UNKNOWN` **在当前结构下对 catch 路径不可达**（仅剩编译器层面的防御性兜底语义，**不应**作为 UNKNOWN 排障主因）。  
- **排障优先级**：报表中的 **`UNKNOWN` 应优先查 `RunEvaluator.evaluate`**（behavior/tool 两分支仍打 UNKNOWN），而非假设 `RunRunner` 末尾兜底频繁触发。  
- **仍建议（P0+ 可选增强）**：若需更细粒度（DNS、SSL、连接被拒等），在 `catch` 内 **显式分支**映射专码；**禁止**在业务已判因的分支滥用 `UNKNOWN`。

### 16.2 为何 `requires_citations=true` 易出现 `CONTRACT_VIOLATION`（Vagent 仓）

**文件**：`vagent-eval/.../RunEvaluator.java` → `verifyCitationMembership`

- Day6 规则：在 `requires_citations` 路径上要求响应根上存在 **`retrieval_hits` 数组**（且元素为 object、含 textual `id`）。缺则 **`missing_retrieval_hits` → CONTRACT_VIOLATION**（见 `verifyCitationMembership` 内对 `hitsNode` 的校验）。

**文件**：`Vagent/src/main/java/com/vagent/eval/dto/EvalChatResponse.java`

- 当前 DTO **无 `retrieval_hits` 字段**，序列化 JSON **不会**出现 `retrieval_hits`，eval 侧必然判缺。

**结论（P0+ 强制改法）**：在 **Vagent** 的 `EvalChatResponse` 增加 `retrieval_hits`（与 `sources` 同源候选 `RetrieveHit` 序列化，字段至少含 `id`；可增 `score` 等与 SSOT 一致），并在 **`EvalChatController`** 所有返回「带检索」的分支 **写入该数组**。  
（与 `plans/vagent-upgrade.md` **P1-0** 门控 SSOT 可并行规划，但 **P0+ 门禁**要求先满足 **eval 判定可读结构**。）

**【强制】与 §16.4 一并阅读**：仅增加 `retrieval_hits` 仍可能因 **`membership.top_n` 与候选条数不一致** 产生 `SOURCE_NOT_IN_HITS`；B 必须保证 **前 N 条 `retrieval_hits` 与 eval 配置的前 N 条候选语义一致**，且 **`sources[*].id` 均落在该前 N 条之内**（或等价地缩小引用范围）。

### 16.3 Day6：三条路径勿混谈（`retrieval_hits` / salt / token）

下列机制 **同时存在**，因果链 **不能**简化为「只修 token 即修复 membership」。

| 路径 | 数据在哪 | eval 如何用 | 常见失败 |
|------|-----------|-------------|----------|
| **A · Day6 citation membership（主路径）** | 响应根 **`retrieval_hits`[]**，元素含明文 **`id`** | `RunEvaluator.verifyCitationMembership` 用 `CitationMembership.membershipHashHex(salt, targetId, canonicalId)`；`salt` 来自 **`eval.membership.salt`**（与请求头 **`X-Eval-Membership-Salt`** 下发值一致） | 缺数组或非空不足 → `missing_retrieval_hits` → **`CONTRACT_VIOLATION`**；id 不在前 **N** 条允许集 → **`SOURCE_NOT_IN_HITS`**（见 §16.4） |
| **B · meta 侧 HMAC 列表（辅助/观测）** | `meta.retrieval_hit_id_hashes`[] | **不替代**路径 A；供非 debug 场景观测 hashed 候选 | Vagent `buildRetrievalHitIdHashes`：**若 `X-Eval-Token` 为空则返回空列表** |

**文件**：`vagent-eval/.../TargetClient.java`

- `X-Eval-Token` **当前写死为空字符串**：`String token = "";`（占位）。

**文件**：`Vagent/.../EvalChatController.java` → `buildRetrievalHitIdHashes`

- **若 token 为空，直接返回空列表**，不产生 `meta.retrieval_hit_id_hashes`。

**结论（P0+ 强制改法）**：

1. **路径 A（优先）**：被测服务必须输出符合 eval 规则的 **`retrieval_hits`**；否则 **仅配 token 无法**消除 `missing_retrieval_hits` 类 **`CONTRACT_VIOLATION`**。  
2. **路径 B（与 A 独立）**：**Eval** 从 `EvalProperties`（或 per-target）读取 **与 Vagent `vagent.eval.api.token-hash` 校验一致的明文 token**（或 eval 专用 token），在 `TargetClient.postEvalChat` 填入 **`X-Eval-Token`**。  
3. **验收**：在 RAG 启用且有命中时，`meta.retrieval_hit_id_hashes` **可**非空（路径 B）；**且** citation case 上路径 A 的 membership **按 §16.4 与 eval `top_n` 对齐**后通过。

> **安全**：token **禁止**写入仓库；用环境变量或本地 `application-local.yml`（已 gitignore）注入。

### 16.4 【强制】`membership.top_n` 与「候选前 N」对齐（eval → B/C）

**eval 侧**：`eval.membership.top-n`（示例默认 **8**，见 `vagent-eval` 的 `application.yml`）决定 **`verifyCitationMembership` 只取 `retrieval_hits` 前 `min(top_n, size)` 条** 生成允许哈希集。

**Vagent 现状（缺口）**：`EvalChatController` 使用固定 **`RETRIEVAL_CANDIDATE_LIMIT_N = 50`** 取检索候选，且 **未读取** `TargetClient` 已发送的 **`X-Eval-Membership-Top-N`** / **`X-Eval-Membership-Salt`**（盐由 eval 侧哈希使用，被测可在响应中返回明文 `id` 即可）。

**风险**：若 `sources` 或 `retrieval_hits` 覆盖的候选 **宽于** eval 的前 **N** 条，则可能出现 **`SOURCE_NOT_IN_HITS` 假失败**（引用 id 落在第 N+1 条及以后）。

**P0+ 强制改法（二选一，组长启动会写死）**：

- **方案 1（推荐）**：Vagent（及 travel-ai）在评测请求中 **读取 `X-Eval-Membership-Top-N`**，将「写入 `retrieval_hits` / 参与 citation 的候选条数」**限制为与该 N 一致**（且与检索排序口径一致）；保证 **`sources[*].id` 均落在该前 N 条 `retrieval_hits` 的 id 集合内**。  
- **方案 2**：不读头，但在 **部署说明** 中 **锁死**：被测 `RETRIEVAL_CANDIDATE_LIMIT_N`（或等价配置）**必须等于** eval 的 `eval.membership.top-n`，且全链路使用该 N。

**验收**：在 `requires_citations=true` 且非 SKIP 的 case 上，**不出现**因 N 不一致导致的 `SOURCE_NOT_IN_HITS`（debug 中 `membership_hits_considered` 与配置一致）。

### 16.5 travel-ai 的 `error_code` 与 eval 枚举不一致

**文件**：`travel-ai-planner/.../EvalSafetyErrorCodes.java`  
定义 `PROMPT_INJECTION_BLOCKED`、`TOOL_OUTPUT_INJECTION_QUERY_BLOCKED`。

**文件**：`vagent-eval/.../RunModel.java` → `ErrorCode`  
**当前枚举可能不含**上述常量名。

**结论（P0+ 强制改法）**（二选一，组长启动会定）：

- **方案 A**：在 eval `ErrorCode` **追加**同名枚举，并在 `RunEvaluator` 中：若 JSON 顶层 `error_code` 与期望行为一致则 PASS 分支扩展（若规格要求）；或仅用于 **报表聚合**（从 target 透传映射）。  
- **方案 B**：travel-ai 改为只使用 **eval 已列枚举**；dataset 期望同步改。  

**禁止**：target 返回 eval **无法解析/无法归类** 的字符串导致落 UNKNOWN（须在 `RunEvaluator` 或单独 `TargetErrorCodeMapper` **显式映射**）。

**【强制】落地**：组长在启动会指定 **唯一入口**（`RunEvaluator` 内联或 `TargetErrorCodeMapper` 类）：凡 JSON 顶层 **`error_code` 字符串** 非空且 **非** `RunModel.ErrorCode` 已列名，必须经过 **显式映射表**（落到已有枚举或组长批增枚举）；**禁止**静默落入 `UNKNOWN`。

### 16.6 `EvalChatContractValidator` 已通过但仍 CONTRACT

契约校验 **仅**检查：`answer`、`behavior`、`latency_ms`、`capabilities`、`meta.mode`（见 `EvalChatContractValidator.java`）。  
**`tool` 子结构、`retrieval_hits` 不在此校验器**，而在 `RunEvaluator` 业务段 → 报表上仍显示 `CONTRACT_VIOLATION`，debug 里应有 `verdict_reason`。

**P0+ 强制**：`EvalResult.debug` 已含 `contract_reason`（契约失败时）；业务段失败须 **始终** 写 `verdict_reason`（现有代码多数已写）。若不足，**强制**在 `RunEvaluator` 所有 FAIL 分支补全 `verdict_reason`。

### 16.7 `requires_citations=true` × `behavior=deny` × `sources=[]`（已观测：`missing_sources` 假 CONTRACT）

**现象（vagent-eval / `RunEvaluator`，`eval_rule_version` 如 `p0.v2`）**：题集 `requires_citations=true`，target 已 **`actual_behavior=deny`** 且 **`sources` 为空数组**，debug 仍为 **`verdict_reason=missing_sources`** → **`CONTRACT_VIOLATION`**。  
**与 B 线 S1-D3 无关**：此类失败 **不**依赖是否已接 **`retrieval_hits[]` / `X-Eval-Membership-Top-N`**；根因是 **判定规则把「要引用」理解成「凡 `requires_citations=true` 就必须 `sources.length≥1`」**，与题意 **「无命中时否决且不得编造引用」** 冲突。

**组长裁定（执行优先级）**：

1. **首选 A（eval）**：在 **`RunEvaluator`** 调整判定顺序或增加 **豁免条件**（须在 **`plans/eval-upgrade.md`** / **`p0-execution-map.md`** 写清条款），语义建议：  
   - `requires_citations=true` 约束 **「若输出可核验的 KB 主张则必须可引用」**；  
   - 当 **`expected_behavior` 与 `behavior` 均为 `deny`** 且 **检索 0 命中**（或 SSOT 定义的等价信号，如 `retrieve_hit_count==0` / `RETRIEVE_EMPTY`）时，**允许** `sources=[]`，**不得**再记 **`missing_sources`**。  
   - **禁止**静默改写已发布规则含义：合并后 **递增** `RunEvaluator.EVAL_RULE_VERSION`（如 **`p0.v2` → `p0.v3`**），旧 run 与旧 `eval_rule_version` **不可**与新规混比 pass_rate。

2. **次选 D（dataset）**：若某 case **本意仅为「空命中拒答」、不要求引用闭环字段**，可将该 case 的 **`requires_citations` 改为 `false`**，并走 **`p0-dataset-changelog.md`**。

3. **不推荐 B（Vagent）**：为过关 **造假 `sources[]` / sentinel 片段**（除非 SSOT **明文**允许且 eval 有对应判定）。

**验证顺序（组长口令）**：**先合并 A 的规则变更（+ 版本号 + SSOT + 单测），再让 B 用新 eval 对 Vagent 跑全量** 验收 `CONTRACT` 是否下降；**不要**要求 B 在 A 未改规则前用假 `sources` 消 CONTRACT。

---

## 17. 冲刺 S1 — 按文件改动的实现清单（A）

| 顺序 | 文件 | 做什么 |
|------|------|--------|
| 1 | `RunModel.java` | `enum ErrorCode` 增加：`BEHAVIOR_MISMATCH`、`TOOL_EXPECTATION_NOT_MET`（或你方 SSOT 最终命名）；若采用 §16.5 方案 A，增加 `PROMPT_INJECTION_BLOCKED`、`TOOL_OUTPUT_INJECTION_QUERY_BLOCKED`。同步更新 `plans/p0-execution-map.md` 附录 D（**【强制】**）。 |
| 2 | `RunEvaluator.java` | `evaluate` 内 **behavior 文本不一致**、**tool 块不满足** 两分支：将 `UNKNOWN` 改为 **项 1** 新增专码。**版本号**见 **§16.7** 与 **项 3**；**禁止**文档与代码版本漂移。 |
| 3 | `RunEvaluator.java` | **§16.7**：`requires_citations=true` 且 **双 deny + 0 命中（或 SSOT 等价条件）** 时 **豁免** `missing_sources` / 对应 `CONTRACT_VIOLATION`；合并后 **递增** `EVAL_RULE_VERSION`（如 `p0.v3`）；**同步** `eval-upgrade.md` / `p0-execution-map.md`。 |
| 4 | `RunEvaluatorTest.java` | **项 2** 两分支各 ≥1 单测；**项 3** 至少 1 单测（deny + 空 sources + requires_citations 场景）。 |
| 5 | `RunRunner.java` | **可选**：在现有 `TIMEOUT` / `UPSTREAM_UNAVAILABLE` 之上，为 DNS、SSL、连接被拒等 **细分专码**（须 SSOT 或组长批）；**禁止**在业务已判因的分支滥用 `UNKNOWN`。 |
| 6 | `EvalChatContractValidator.java` | **可选增强**：`ContractOutcome` 增加 `List<String> violationPaths`；`validate` 收集所有缺失项（不仅第一个），写入 debug `contract_violations`（供 B/C 一次修完）。 |
| 7 | `EvalProperties.java` + `application.yml` | 增加 `eval.targets[].evalToken` 或全局 `eval.targetToken`（**不进 git**）；`TargetClient` 使用该值设置 `X-Eval-Token`。 |
| 8 | `TargetClient.java` | 删除 `token = ""` 硬编码；从配置读取；**集成测试**断言头已发出（可用 mock server）。 |

**完成定义**：全量跑 report 中 **`UNKNOWN` 计数**相对 P0 Day10 基线下降；且 **behavior_mismatch 不再出现在 UNKNOWN 桶**（应落在新码）。

---

## 18. 冲刺 S1 — 按文件改动的实现清单（B · Vagent）

| 顺序 | 文件 | 做什么 |
|------|------|--------|
| 1 | `EvalChatResponse.java` | 新增字段：`List<RetrievalHitDto> retrievalHits`（或 `JsonNode` 列表），Jackson 序列化为 **`retrieval_hits`**；元素 **至少** `id`（string）。 |
| 2 | `EvalChatController.java` | 在 **所有「检索已执行且可能返回 sources」** 的成功路径，填充 `retrieval_hits`（与参与 `sources` 的候选一致）；**【强制】** 满足 §16.4：`top_n` 与 eval 一致（读 **`X-Eval-Membership-Top-N`** 或与 `eval.membership.top-n` 锁死配置），且 **`sources[*].id` ⊆ 前 N 条 `retrieval_hits` 的 id**。**AUTH / POLICY_DISABLED / 纯 deny** 等路径：若仍返回 `capabilities`+`meta`，须满足 `EvalChatContractValidator`；无检索时 `retrieval_hits` 可为 `[]` 或省略（与 eval `RunEvaluator` 对「需要 citations」时的要求一致——**若 requires_citations 且非 SKIP 路径，eval 要求非空 `retrieval_hits`**，故 B 必须保证在「应引用」场景下数组非空且含 id）。 |
| 3 | `EvalChatController` 单测 / `MockMvc` | **【强制】** 每类分支一条：200 + 最小 JSON；与 `vagent-eval` 的 `EvalChatContractValidatorTest` 对齐（可复制用例列表）；**至少一条**断言：`X-Eval-Membership-Top-N` 与响应 `retrieval_hits` 条数/引用范围符合 §16.4。 |
| 4 | 配置说明 | 写明两件事：**(1)** eval 跑全量须配置 **与 Vagent 一致的 `X-Eval-Token`**，否则 **`meta.retrieval_hit_id_hashes` 为空**（§16.3 路径 B）；**(2)** **`eval.membership.top-n` 与被测候选前 N** 须一致（§16.4），否则易出现 **`SOURCE_NOT_IN_HITS`**。 |

---

## 19. 冲刺 S1 — 按文件改动的实现清单（C · travel-ai）

| 顺序 | 文件 | 做什么 |
|------|------|--------|
| 1 | `EvalChatResponse.java` | 确认 **`@JsonInclude(NON_NULL)`** 不会导致 **删掉 eval 认为的「必填」**：当前 eval 校验 **answer 必为 string**（可为 `""`）、`latency_ms` number、`capabilities` object、`meta.mode` string。**禁止**在失败路径返回 `null` 的 `answer` 或缺失 `capabilities`。**现状缺口**：DTO **无 `sources`、无 `retrieval_hits`**。在 eval **未**实施 **§16.7** 前，`requires_citations=true` 会要求 **`sources` 非空**；**§16.7 落地后**，**deny + 0 命中** 等路径以 SSOT 为准，**勿**为用例过关造假 `sources`。 |
| 2 | `EvalChatService.java`（或流水线出口） | 枚举 **所有**出口：`answer/clarify/deny/tool`、工具超时、`PARSE_ERROR`、RAG stub、`Day9` 安全码；每出口 **补齐** 顶层字段；**§16.5** 字符串 `error_code` 走 **唯一映射入口**。 |
| 3 | `sources` + `retrieval_hits` | 与 B 对齐：`sources`（`id` 至少）与根级 **`retrieval_hits`**（元素至少 `id`），并满足 **§16.4** 的 **top_n / 引用子集** 约束。若 travel-ai P0 对 citation case 全部 SKIP，**【强制】** `capabilities.retrieval.supported=false` 且 eval 规则为 SKIP——则 D 须保证题集一致（S2）。 |
| 4 | `EvalChatControllerTest.java` | 为 **每个** `error_code` 与 `behavior` 组合增加快照或 JSON 断言测试。 |

---

## 20. 冲刺 S2 — `tool_policy` 与 EvalCase（A + D）

**现状**：`vagent-eval/.../dataset/Model.java` 的 `EvalCase` **无 `tool_policy` 字段**；JSONL 若有该列，可能被 `DatasetApi` 忽略。

**【强制】实现步骤**：

1. `Model.EvalCase` 增加 `String toolPolicy`（或 enum：`stub|disabled|real`）。  
2. `DatasetApi.normalizeCase`：从 JSON 解析 `tool_policy` 写入 record。  
3. `RunEvaluator`：  
   - 若 `toolPolicy=real` 且 `capabilities.tools.supported=false` → **SKIPPED_UNSUPPORTED**（或组长规定 FAIL，但必须 **写进 debug 原因** 与文档）。  
   - 与 dataset `p0-dataset-tool-policy.md` 硬门槛分母规则一致。  
4. **D**：任何改题与 `tool_policy` 变更 **只许** 走 `p0-dataset-changelog.md`。

---

## 21. 冲刺 S3 — 分桶报表（A）

**现状**：**compare v1 已具备**（见 §8.1 A5）；**缺口在按题集 `tags` 的子报表**——`RunReportService.computeReport` 聚合 **全量** `EvalResult`，**无 tags 维度**；`EvalCase` 含 `tags`（`Model.java`）。

**【强制】实现（二选一）**：

- **方案 A（推荐）**：新增 `RunReportService.buildReportFilteredByTagPrefix(runId, prefix, topN)`：从 `RunStore` 取 results，用 `DatasetStore` 按 `case_id` 查 `EvalCase.tags`，过滤后调用现有统计逻辑（可抽 `computeReport` 为 package-private 重载）。  
- **方案 B**：仓库内增加 `plans/datasets/scripts/report-by-tag.ps1`（或 `.py`），**导出** `GET .../results` JSON 后离线算；**必须在 CI 或周报中固定命令与输出路径**。

**完成定义**：周报能贴 **attack 子集 pass_rate** 与 **rag 桶** 数字，且与总 report **同一次 run_id**。

---

## 22. 执行顺序依赖（防返工）

```text
16.2 Vagent retrieval_hits + §16.4 top_n 对齐 ──┐
16.3 Eval TargetClient token（meta 哈希）        ├──► B 侧形状/Top-N 验收
17 A：UNKNOWN 专码 + §16.7 deny+空 sources 豁免   ├──► 再跑全量（同 dataset、新 eval_rule_version）
19 C sources + retrieval_hits + 全分支           ───► 再跑 travel-ai 全量
20 tool_policy                                   ───► 在题集与 eval 对齐后
21 分桶报表（tags；compare 已存在）               ───► 最后接日报模板
```

**§16.7 与 B 的关系**：若全量仍见 **`missing_sources`** 且 **`actual_behavior=deny`**、**`sources_count=0`**，**先 A（§17 项 3）再 B 复跑验证**；**禁止** B 用假 `sources` 在 A 改规则前「刷绿」。  

**禁止**：在 **未** 修根级 **`retrieval_hits`**、**§16.4 top_n 对齐** 与（如需路径 B）**`X-Eval-Token`** 前，要求 B/C「把 pass_rate 拉高」（否则属无效加班）。

---

## 23. 外部评审对照（批判性复核；**§23.3** 为 v1.3 增补）

本节记录「他人评审 ↔ 三仓源码」核对结果，便于以后审计；**执行仍以 §15～§24 为准**。

### 23.1 评审意见中 **与源码一致、本文保留** 的论断

| 论断 | 依据摘要 |
|------|-----------|
| `RunEvaluator` 在 `behavior_mismatch` / `tool_not_satisfied` 仍用 `UNKNOWN` | `evaluate` 内两分支 `ErrorCode.UNKNOWN` |
| Vagent `EvalChatResponse` 无 `retrieval_hits` | DTO 仅 `sources` 等，无该字段 |
| `EvalChatContractValidator` 不校验 `retrieval_hits` / `tool` | 类注释与 `validate` 实现一致 |
| `EvalCase` / `normalizeCase` 未解析 `tool_policy` | `Model.EvalCase` 无字段；导入逻辑未接 JSONL 列 |
| `RunReportService` 无按 `tags` 分桶 | `computeReport` 全量聚合；分桶须新增 §21 |
| `TargetClient` 中 token 写死 `""` | `postEvalChat` 内 `String token = "";` |
| eval **membership** 用 `eval.membership.salt` + `retrieval_hits[].id`，**不是**用 `X-Eval-Token` 做 `CitationMembership` | `CitationMembership.membershipHashHex(salt, targetId, …)`；与 Vagent `meta.retrieval_hit_id_hashes` 的 HMAC 材料 **分离**（§16.3 表） |
| `eval.membership.top-n` 默认 **8**、`Vagent` `RETRIEVAL_CANDIDATE_LIMIT_N` **50**、Vagent **未读** `X-Eval-Membership-Top-N` | `vagent-eval/application.yml`；`EvalChatController` 常量；`grep` 无消费该头 |
| travel-ai `EvalChatResponse` **无 `sources`、无 `retrieval_hits`** | DTO 仅 answer/behavior/latency/capabilities/meta/errorCode/tool |
| `RunCompareService` **已实现** compare v1 | 与 §8.1 A5、§21「compare 已具备」表述一致 |

### 23.2 评审指出的 **原文档错误**，已在 v1.1+ **修正或重写**

| 问题 | 处理 |
|------|------|
| 将 `RunRunner` 末尾 `UNKNOWN` 描述为主要漏斗 | 已改为 **catch 下不可达**、主因 **`RunEvaluator`**（§16.1） |
| `EVAL_RULE_VERSION` 与「应提到 v3」易与仓库现状冲突 | §17 项 2 标明 **当前 p0.v2**、**仅规则变更 PR 后递增** |
| 强依赖行号 | §16 起增加 **类名+方法+分支语义**；删除易漂移的 L168 表述 |
| §16.3 把 token 与 Day6 membership **混成一条因果** | 已拆 **路径 A/B 表**（§16.3） |
| 未强调 **top_n 与 50 条候选** 不对齐风险 | 已单立 **§16.4** + §18/§22 |
| Vagent **未消费** `X-Eval-Membership-*` | §16.4、§18 强制读头或配置锁死 |
| travel-ai **整条引用链**缺口 | §19 项 1/3 **sources + retrieval_hits** |
| S3 易误解 compare 从零做 | §5 冲刺表、§8.1 A5、§21 首段 **已写明 compare 已有** |
| 顶层 `error_code` 字符串 **无唯一映射入口** | §16.5 **【强制】落地** 段 |

### 23.3 评审未强调、但执行时仍建议 **自查** 的点（本文补充）

- **`RunRunner` 末尾 `UNKNOWN`**：虽当前不可达，若未来有人在 `try` 内增分支且 **忘记 return**，兜底会重新生效；Code review 时 **禁止**在 `try` 内增加「非 return」出口。  
- **报表 TopN 的语义**：`run.report` 的 `error_code` 统计来自每条 **`EvalResult.errorCode`（`RunModel.ErrorCode` 枚举）**，**不是**直接聚合上游 JSON 里的字符串字段；若 target 顶层 `error_code` 字符串未在 **`RunEvaluator` / 映射层** 反映到该枚举，报表上仍会表现为 `UNKNOWN` 等已有桶。§16.5 的映射是 **正解**；若将来要暴露「未映射字符串」，须 **组长批准** 并 **改 report schema 版本**（当前为 `run.report.v1`）。

---

## 24. Vagent（`target_id=vagent`）：灌 KB 与「空命中」门控（`BEHAVIOR_MISMATCH` 分桶）

**组长定稿（摘录）**

- **A 类**：先 **灌库验证**「`answer` +（若题面要求）**citations**」能否 **`sources_count>0`**；不与 B/C 混谈。  
- **B 类**：**0 命中却要 `deny`** **单开决策线**——改 **门控/产品** 或 **改题/期望**，**勿**与灌库绑成「**一灌就绿**」。  
- **工具类**：按 **`SKIPPED_UNSUPPORTED` / eval 能力** 处理；**不在 Vagent 硬扛** tool 通过。

**背景**：全量跑若 **`sources_count` 长期全 0**，则 `EvalChatController` 在 **0 命中** 时走 **`RETRIEVE_EMPTY` → `behavior=clarify`**（与实现一致）。此时 **大量** `expected_behavior ∈ {answer, deny}` 会与 **`actual_behavior=clarify`** 冲突，报表上多为 **`BEHAVIOR_MISMATCH`**——**根因首先是「无 KB 命中」+ 固定门控**，不是 eval 映射回退。

### 24.1 与 eval 灌库对齐（【强制】）

- **`X-Eval-Target-Id=vagent`** 时，Vagent 内 **`stableEvalUserId("vagent")`** 派生的用户/租户，与 **向 KB 灌入文档的归属** **必须一致**；否则易出现 **全卷 0 命中**。  
- 日报须记录：**灌库版本 / 文档条数 / 是否针对 eval 用户**。

### 24.2 失败分桶与执行顺序（组长口令）

| 分桶 | 题意（典型） | 主线动作 | 与「只灌库」关系 |
|------|----------------|----------|------------------|
| **A · 期望 `answer`** | `answer_*`、`requires_citations_*`、`rag_basic_*`、`contract_shape_*` 等 | **灌 KB**（对齐 eval 用户）+ 必要时调检索阈值/索引；重跑后看 **`sources_count>0`** 与 FAIL 是否下降 | **灌库是主线**；无命中则当前实现 **无法**稳定给 `answer`。 |
| **B · 期望 `deny`，但 0 命中时实现仍为 `clarify`** | `answer_005`、`rag_empty_002`、`attack_*`、`behavior_deny_*`、citation_mismatch 等 | **产品/SSOT 决策**：空命中时 **clarify vs deny**（或按 case/tag）；再改 **`EvalChatController`** 分支；必要时 **D** 改期望或 **A** SKIP（S2） | **灌库 alone 不能**解决「0 命中却要 deny」——须 **门控策略** 与题集对齐，**勿**指望「一灌就全绿」。 |
| **C · `tool`** | `p0_v0_tool_*` | **`SKIPPED_UNSUPPORTED`**（eval 声明不支持工具）或 **eval 侧**开工具能力；**禁止** Vagent 硬造 tool 通过 | 与 KB **无关**。 |

### 24.3 建议迭代顺序

1. **先做 A 类验证**：对齐租户 **灌 KB** → 重跑 → 验收 **`answer_*` / `requires_citations_*` / `rag_basic_*`** 等 **`sources_count`** 与 `BEHAVIOR_MISMATCH` 是否下降。  
2. **再开 B 类**：组长确认 P0 下 **空命中** 的期望（clarify / deny / 分 case），再改代码或题集。  
3. **C 类**：维持 SKIPPED 或走 eval 配置。

---

**组长签字 / 生效日期**：________________  

**P0+ 启动日期**：________________  
