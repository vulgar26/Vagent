# P0+ 每日任务表 & 验收表（组长下发用）

> **用途**：复制对应章节发给 **A / B / C / D** 或全员；与 **`plans/p0-plus-execution.md`** 配套。  
> **填写**：每次下发前替换 `TODAY_TOKEN`、日期、`S?-D?`、冻结 dataset 版本、`UNKNOWN` 阈值（见 `p0-plus-execution.md` §4.1）。

---

## 0. 全员每日节奏（组长可原样发群）

### 0.1 早间（开工前 10 分钟）

```text
【P0+ 今日同步】TODAY_TOKEN：____-S?_-D?
冻结 dataset 版本：v0.__（git：________）
本日主战场（组长指定）：A | B | C | D | 组合：________

请各 owner 17:00 前回复本条「日终验收表」对应段落（勾选 + run_id 如有）。
禁止：夹带 P1（CRAG / Multi-Agent / 并行工具 / eval 大重构等），见 p0-plus-execution.md §9。
```

### 0.2 晚间（收工前）

```text
【P0+ 日结】TODAY_TOKEN：____-S?_-D?
请按角色回复 §「日终验收」勾选结果；有 run 的贴 run_id + report 里 CONTRACT / UNKNOWN / Top3 error_code。
阻塞项一条（无则写「无」）：________
```

---

## 1. 冲刺 S1（归因与契约）— 建议 5 个工作日任务表

> **门禁**：`p0-plus-execution.md` **§6.5**（双 target `CONTRACT_VIOLATION==0`、`UNKNOWN`≤阈值、映射表落盘）。

> **补充（`p0-plus-execution.md` v1.4 / §16.7）**：若全量里仍有 **`verdict_reason=missing_sources`** 且 **`actual_behavior=deny`**、**`sources_count=0`**：**先 A** 改 `RunEvaluator`（豁免 + 升 `EVAL_RULE_VERSION` + SSOT），**再 B** 用新 eval **全量复跑** 验证；**禁止** B 造假 `sources` 过关。

### 1.1 A（Eval / vagent-eval）— 按日任务

| 工作日 | 当日任务（必须交付） | 日终自检（见 §4 日终表 A） |
|--------|----------------------|---------------------------|
| **S1-D1** | 通读 `RunEvaluator.evaluate`：标出仍返回 `UNKNOWN` 的分支清单；`TargetClient` 列出 token 改造点（配置键名草案） | 清单已发组长；无代码也可，但必须有文档/消息记录 |
| **S1-D2** | `RunModel.ErrorCode` 增加 `BEHAVIOR_MISMATCH`、`TOOL_EXPECTATION_NOT_MET`（名以 SSOT 为准）；`RunEvaluator` 两分支改挂新码；单测 2 条 | PR 或本地 commit 可指向；单测绿 |
| **S1-D3** | `EvalChatContractValidator` 可选：多字段 violation 列表进 debug；**或** `EvalProperties`+`TargetClient`：`X-Eval-Token`；**或（与 B 并行时优先）** **§16.7**：`RunEvaluator` deny+空 sources 豁免、`EVAL_RULE_VERSION` 递增、单测、改 `eval-upgrade`/`p0-execution-map` | 配置说明 / PR / SSOT diff 任一可指 |
| **S1-D4** | 集成测/手工：带 token 打 Vagent 一次；全量跑 **vagent** target，导出 `run_id` + report；统计 `CONTRACT`/`UNKNOWN` | `run_id` + TopN 粘贴组长 |
| **S1-D5** | 全量跑 **travel-ai**；**UNKNOWN 映射表**定稿并提交到组长指定路径（`docs/` 或 `plans/`）；对照 §6.1 补 3 条 case 修复前后 debug | 映射表路径 + 双 target 最新 report 摘要 |

> **可并行**：D2～D4 若人力够，可与 B/C 联调同日进行；**组长指定主战场**避免四人同时大改。

### 1.2 B（Vagent）

| 工作日 | 当日任务 | 日终自检 |
|--------|----------|----------|
| **S1-D1** | 列出 `EvalChatController` **所有** `return` 分支；对照 `EvalChatContractValidator` 缺啥 | 分支清单发给组长 |
| **S1-D2** | DTO 增加 **`retrieval_hits`**（ snake_case 序列化）；设计元素字段（至少 `id`） | 编译通过；本地 JSON 样例一条 |
| **S1-D3** | Controller：**读 `X-Eval-Membership-Top-N`**（或配置锁死 = eval `top-n`）；候选条数与 **§16.4** 一致；填充 `retrieval_hits` 与 `sources` 同口径 | MockMvc/集成测一条带 Header |
| **S1-D4** | 契约单测补齐：`EvalChatContractValidatorTest` 对齐的分支覆盖；**在 A 已合并 §16.7/专码等 eval 变更后**，本机对新 eval **全量跑**，对照 `eval_rule_version` | 单测绿 + `run_id` + CONTRACT/UNKNOWN 摘要 |
| **S1-D5** | 全量跑验收：**`CONTRACT_VIOLATION==0`**（**含** §16.7 已生效的 eval）；若未 0，列剩余 `case_id` + **`verdict_reason`** | report 贴组长 |

### 1.3 C（travel-ai）

| 工作日 | 当日任务 | 日终自检 |
|--------|----------|----------|
| **S1-D1** | 同 B：枚举所有 eval 出口；确认 DTO 缺 `sources`/`retrieval_hits` | 清单发组长 |
| **S1-D2** | `EvalChatResponse` 增加 **`sources`** + **`retrieval_hits`**（或组长批准整卷 citation SKIP 则走 capabilities+S2） | 不破坏 `EvalChatContractValidator` 顶层必填 |
| **S1-D3** | 与 eval **top_n** 对齐（读头或配置）；`sources[*].id` ⊆ 前 N 条 `retrieval_hits` | 单测一条 |
| **S1-D4** | `EvalChatService` 全出口补齐；`EvalSafetyErrorCodes` 与 eval 映射（§16.5）定案 | 单测/快照 |
| **S1-D5** | 全量跑：**`CONTRACT_VIOLATION==0`** | report 贴组长 |

### 1.4 D（Dataset / 回归）

| 工作日 | 当日任务 | 日终自检 |
|--------|----------|----------|
| **S1-D1** | 确认冻结 **v0.x** 与 **changelog** 当前头；通知全员不得静默改题 | 冻结版本写入日报 |
| **S1-D2** | 维护 **回归日报**（附录 A 字段）；收集 B/C 的「契约剩余条数」 | 日报文件路径 |
| **S1-D3** | 若 A 导出 FAIL 明细：按 `error_code` 粗分桶给 B/C 标签（非必须改题） | 分桶表 |
| **S1-D4** | 记录当日 **vagent / travel-ai** `run_id`（若有） | 日报更新 |
| **S1-D5** | 汇总 S1 是否满足 §6.5 四项（材料齐否）；**不配平代码**但配平证据 | **S1 收口检查表**（见 §3.1）草稿 |

---

## 2. 冲刺 S2（题与能力对齐）— 建议 3～5 日任务表

> **门禁**：`p0-plus-execution.md` **§7.4**。

### 2.1 A

| 日 | 任务 |
|----|------|
| S2-D1 | `EvalCase` + `normalizeCase` 接 **`tool_policy`**；`RunEvaluator` 与 D1 清单对齐 SKIP/FAIL |
| S2-D2 | 硬门槛分母与 `p0-dataset-tool-policy.md` 一致；联跑验证 |
| S2-D3 | 全量跑 + report；配合 D 抽查 5 条 SKIPPED/FAIL |

### 2.2 B / C

| 日 | 任务 |
|----|------|
| S2-D1 | `capabilities` 与真实能力对齐；禁止虚报 |
| S2-D2 | 按 D 裁定改响应或配合 A 的 SKIP 规则 |
| S2-D3 | 全量跑；组长抽查「无谎言」 |

### 2.3 D

| 日 | 任务 |
|----|------|
| S2-D1 | **D1 能力缺口清单**填首版（每 case 一行） |
| S2-D2 | 组长裁定补齐；**changelog** 逐条挂 `case_id` |
| S2-D3 | **§7.4 收口检查** |

---

## 3. 冲刺 S3（对比与分桶）— 建议 3～5 日任务表

> **门禁**：`p0-plus-execution.md` **§8.3**；compare **已实现**（`RunCompareService`），本阶段做 **固定用法 + tag 报表 + 模板**。

### 3.1 A

| 日 | 任务 |
|----|------|
| S3-D1 | 固定 **compare** 调用方式（base/cand run_id 规则）并写 README 一段 |
| S3-D2 | 实现 **§21** 方案 A 或 B（tag 分桶 pass_rate） |
| S3-D3 | 产出示例：同 `run_id` 总 report + attack/rag 子报表 |

### 3.2 D

| 日 | 任务 |
|----|------|
| S3-D1 | 更新 **日报模板**：两 `run_id`、compare 摘要、子通过率、dataset 版本 |
| S3-D2 | 连续两周周报试填（组长试填通过） |
| S3-D3 | **§8.3 收口** + compare 存档路径 |

### 3.3 B / C

| 日 | 任务 |
|----|------|
| S3-D1～D3 | **keep-alive**：每日探针或按需修回归；**禁止**开 P1；配合 D 要第二周 run 时准时出包 |

---

## 4. 日终验收表（每人复制回复）

**TODAY_TOKEN**：________ **角色**：A / B / C / D  

### 4.1 A（Eval）

- [ ] 今日代码已 push / PR 已开（或说明「仅调研无 push」）  
- [ ] `UNKNOWN` 相关：映射表 / `RunEvaluator` / `TargetClient` 至少一项有 **可见进展**（链接或 commit）  
- [ ] 若今日有全量跑：**run_id** = ________，`CONTRACT` = ____，`UNKNOWN` = ____，Top3 error_code = ________  
- [ ] 阻塞项：________  

### 4.2 B（Vagent）

- [ ] 今日动到的文件列表：________  
- [ ] `retrieval_hits` / `X-Eval-Membership-Top-N` / 契约单测：今日完成项 ________  
- [ ] 若今日有全量跑：**run_id** = ________，`CONTRACT_VIOLATION` = ____  
- [ ] 阻塞项：________  

### 4.3 C（travel-ai）

- [ ] 今日动到的文件列表：________  
- [ ] `sources` + `retrieval_hits` / top_n 对齐 / 映射：今日完成项 ________  
- [ ] 若今日有全量跑：**run_id** = ________，`CONTRACT_VIOLATION` = ____  
- [ ] 阻塞项：________  

### 4.4 D（Dataset / 回归）

- [ ] 冻结 dataset 版本仍为：v0.__（若变更已写 changelog）  
- [ ] 今日日报路径：________  
- [ ] 收集到的 B/C `run_id`（若有）：________  
- [ ] 阻塞项：________  

---

## 5. 冲刺末验收表（组长勾选）

### 5.1 S1 收口（对应 `p0-plus-execution.md` §6.5）

| # | 项 | 证据位置 / 备注 |
|---|----|-----------------|
| 1 | Vagent 全量 `CONTRACT_VIOLATION == 0` | run_id：________ |
| 2 | travel-ai 全量 `CONTRACT_VIOLATION == 0` | run_id：________ |
| 3 | `UNKNOWN` ≤ §4.1 阈值 | 两 run 各贴计数 |
| 4 | UNKNOWN 映射表已落盘 | 路径：________ |
|  | **组长签字 / 日期** | ________ |

### 5.2 S2 收口（对应 §7.4）

| # | 项 | 证据 |
|---|----|------|
| 1 | D1 清单 100% 有裁定且已落地 | 附件/链接：________ |
| 2 | 全量跑 SKIPPED/FAIL 比例符合规则（抽查 5 条） | 记录：________ |
| 3 | 无 capabilities 虚报（抽查） | 记录：________ |
|  | **组长签字 / 日期** | ________ |

### 5.3 S3 收口（对应 §8.3）

| # | 项 | 证据 |
|---|----|------|
| 1 | 周报模板组长试填通过 | 模板路径：________ |
| 2 | 一次完整 compare 已存档 | 路径：________ |
| 3 | tag 分桶数字已进入周报（同 run_id） | 附件：________ |
|  | **组长签字 / 日期** | ________ |

### 5.4 P0+ 总出口（对应 §10，宣告前四项 S1～S3 均已满足或豁免）

| # | 项 |
|---|----|
| 1 | S1～S3 收口表已全部签字（或豁免附 changelog） |
| 2 | 双 target 最近一次全量 `CONTRACT_VIOLATION = 0` |
| 3 | `UNKNOWN` ≤ 阈值 |
| 4 | 连续 **2** 份周报含 compare + 分桶 |
| 5 | P1-0（Vagent 门控 SSOT）已排入下一迭代第一项，owner=B |
|  | **组长签字 / P0+ 结束日期** | ________ |

---

## 6. 单人周视图（可选打印）

|  | 周一 | 周二 | 周三 | 周四 | 周五 |
|--|------|------|------|------|------|
| **A** | S1-D1 | S1-D2 | S1-D3 | S1-D4 | S1-D5 |
| **B** | S1-D1 | S1-D2 | S1-D3 | S1-D4 | S1-D5 |
| **C** | S1-D1 | S1-D2 | S1-D3 | S1-D4 | S1-D5 |
| **D** | S1-D1 | S1-D2 | S1-D3 | S1-D4 | S1-D5+收口草稿 |

进入 S2/S3 时，将表头 **S1** 改为 **S2** / **S3**，任务正文换用 **§2** / **§3**。

---

**文档版本**：v1.1 **配套**：`plans/p0-plus-execution.md` v1.4（§16.7、§22）  
