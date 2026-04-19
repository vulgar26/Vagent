## P0 执行视图（落地改动地图 / MVP / 契约字段表 / 枚举与前缀对齐）

本文件用于把三份规格（`vagent-upgrade.md` / `travel-ai-upgrade.md` / `eval-upgrade.md`）从“条款化方案”补齐到“工程执行视图”，避免落地阶段因 Owner/里程碑/改动点不清而返工。

> 约定：所有对外 JSON（API、dataset、`meta`、`config_snapshot_json`）一律 **snake_case**；DB 列名同样 snake_case；代码内部可 camelCase，但序列化输出必须 snake_case。

---

## 附录 A：P0 两周 MVP（<=10 条 / 项目）

### A1. eval（两周可交付的最小闭环）

- **A1-1**：Dataset 导入（JSONL/CSV）+ dataset 列表/查询（最小 30 case）
- **A1-2**：Run 创建/进度/取消（串行跑 case 即可）
- **A1-3**：调用 target `POST /api/v1/eval/chat`（两目标：Vagent、travel-ai）
- **A1-4**：P0 PASS/FAIL/`SKIPPED_UNSUPPORTED` 规则落地（expected_behavior + requires_citations + 引用闭环）
- **A1-5**：P0 报告 `run.report`（passRate/skippedRate/p95 latency/error_code TopN）
- **A1-6**：P0 compare（base vs cand）：passRateDelta + regressions/improvements 列表
- **A1-7**：安全边界落地：`enabled` 开关 + CIDR allowlist + token-hash 校验 + 审计 reason
- **A1-8**：`EVAL_DEBUG` 模式开关与违规判定（`SECURITY_BOUNDARY_VIOLATION`）
- **A1-9**：最小可观测：run_total/run_failed/case_latency/error_rate 指标

### A2. Vagent（P0：引用闭环 + 门控 + 可回归）

- **A2-1**：实现 `POST /api/v1/eval/chat`（非流式；返回 `answer/behavior/latency_ms/capabilities/meta/...`）
- **A2-2**：`sources[]` 服务端生成（LLM 禁止生成/改写），`snippet` 规则截断（≤300 字符）
- **A2-3**：低置信门控（相对特征）：`meta.low_confidence=true` + `meta.low_confidence_reasons[]`
- **A2-4**：空命中一致性：`meta.retrieve_hit_count=0` 时按配置走 `deny|clarify|allow_llm`
- **A2-5**：引用闭环校验（requires_citations=true）：`SOURCE_NOT_IN_HITS` 可被 eval 判定
- **A2-6**：hashed membership（P0）：`meta.retrieval_hit_id_hashes[]`（前 N，N<=50）+ canonical id scheme 固定
- **A2-7**：`EVAL_DEBUG` 禁止边界：默认不返回 `meta.retrieval_hit_ids[]` 明文
- **A2-8**：一次性 Reflection（不循环）：触发则 `meta.guardrail_triggered=true` + 可归因 error_code

### A3. travel-ai（P0：串行阶段闭环 + 可控性可验收）

- **A3-1**：实现 `POST /api/v1/eval/chat`（非流式；snake_case 契约）
- **A3-2**：固定线性阶段顺序：`plan → retrieve → tool → write → guard`
- **A3-3**：P0 控制流观测字段：`meta.stage_order[]`、`meta.step_count`、`meta.replan_count=0`
- **A3-4**：Plan JSON schema + tolerant parse + repair once（含回显限制）
- **A3-5**：Plan 解析观测：`meta.plan_parse_attempts/outcome`
- **A3-6**：串行工具调用 + 总超时 + 降级矩阵覆盖（不得异常退出）
- **A3-7**：低置信/空命中门控：以命中数/相对特征为主（P0 不启用 score 阈值）

---

## 附录 B：落地改动地图（条款 → 代码改动点 → 产物 → Owner/工期）

> Owner/工期建议你们按团队实际分配填写；这里先给“改动点与产物形态”，保证新同学能一眼看到“本周要改哪些文件/接口”。

### B1. Vagent（本仓：`D:\Projects\Vagent`）

| P0 条款/目标 | 建议改动点（类/模块） | 产物（字段/行为/测试） | Owner | 工期（d） |
|---|---|---|---|---|
| `POST /api/v1/eval/chat` | 新增 controller（建议文件：`src/main/java/com/vagent/eval/EvalChatController.java`） | 统一 JSON 响应（snake_case）；读取 `X-Eval-*` headers（代码内部可归一化为 `x_eval_*` 变量名） |  |  |
| `sources[]` 服务端生成 | `src/main/java/com/vagent/chat/RagStreamChatService.java`（构造 prompt 与 hits）+ `src/main/java/com/vagent/kb/KnowledgeRetrieveService.java`（检索） | `sources[].id/title/snippet` 规则截断（≤300）；LLM 不得生成 sources |  |  |
| low_confidence 门控 | `RagStreamChatService.stream(...)`：`hits = knowledgeRetrieveService.searchForRag(...)` 后 | `meta.low_confidence=true` + `meta.low_confidence_reasons[]`；空命中 `meta.retrieve_hit_count=0` |  |  |
| 引用闭环校验 | Eval 专用响应生成层（返回前） | requires_citations=true：`SOURCE_NOT_IN_HITS` / deny|clarify（按策略） |  |  |
| hashed membership（前 N） | 检索 hits 后处理（与 sources 同口径） | `meta.retrieval_hit_id_hashes[]`（N<=50）+ `meta.retrieval_candidate_limit_n/total` |  |  |
| canonical id 固定 | `RetrieveHit` 的 id 选择（建议统一 `kb_chunk.id`） | `meta.canonical_hit_id_scheme="kb_chunk_id"`（P0 固定一条） |  |  |
| EVAL_DEBUG 边界 | `EvalChatController` | 非 debug 禁止 `meta.retrieval_hit_ids[]`；违规为安全边界问题 |  |  |

#### B1-补充：现有入口定位（便于新同学上手）

- SSE 主入口：`src/main/java/com/vagent/chat/StreamChatService.java`（`ragProperties.isEnabled()` 时委托给 RAG）
- RAG 编排入口：`src/main/java/com/vagent/chat/RagStreamChatService.java`（rewrite → intent → tool(optional) → retrieve → prompt → stream）
- 检索层：`src/main/java/com/vagent/kb/KnowledgeRetrieveService.java`（`searchForRag` 内 `topK = ragProps.getTopK()`，可作为 P0 topK 上限落点）

### B2. travel-ai（外部仓：`D:\Projects\travel-ai-planner`）

| P0 条款/目标 | 建议改动点（类/模块） | 产物（字段/行为/测试） | Owner | 工期（d） |
|---|---|---|---|---|
| 固定线性阶段 | `TravelAgent` 主流程 | 代码上固定调用顺序 |  |  |
| stage_order/step_count | **评测口** `EvalChatService` + `EvalLinearAgentPipeline` | `meta.stage_order[]`、`meta.step_count`（**主链路 SSE 另列验收**） |  |  |
| replan_count=0 | agent orchestration | `meta.replan_count=0` |  |  |
| Plan schema + repair once | `PlanParser` | `meta.plan_parse_attempts/outcome` |  |  |
| 修复提示回显限制 | parser repair prompt builder | 允许字段白名单 |  |  |
| **hashed membership（E7）** | `EvalChatController`（读 `X-Eval-*`）、`RetrievalMembershipHasher`、`EvalChatService#attachRetrievalMembershipMeta`、`EvalChatMeta` | `meta.retrieval_hit_id_hashes[]` + `retrieval_candidate_limit_n/total` + `canonical_hit_id_scheme`；单测见 `EvalChatControllerTest` / `RetrievalMembershipHasherTest` |  | **已落地（2026-04-18）** |

### B3. eval（外部仓：`D:\Projects\vagent-eval`）

| P0 条款/目标 | 建议改动点（模块） | 产物（字段/行为/测试） | Owner | 工期（d） |
|---|---|---|---|---|
| dataset/import | dataset service + storage | JSONL/CSV import |  |  |
| run/execute | runner + queue | 串行 case 执行 |  |  |
| report/compare | report service | regressions/improvements |  |  |
| 引用闭环验证 | evaluator | `SOURCE_NOT_IN_HITS` 判定 |  |  |
| token/CIDR/审计 | api filter/middleware | reason=DISABLED/CIDR_DENY/... |  |  |

---

## 附录 C：统一对外契约字段表（最终版，以 eval 为准）

### C1. target：`POST /api/v1/eval/chat`（请求）

- Headers（P0）：
  - `X-Eval-Token`
  - `X-Eval-Run-Id`
  - `X-Eval-Dataset-Id`
  - `X-Eval-Case-Id`
  - `X-Eval-Target-Id`
- Body（P0）：
  - `query`
  - `mode`（可选）
  - `conversation_id`（可选）

> 说明（防止“契约字段”与“代码变量名”混淆）：对外 HTTP Header **以 `X-Eval-*` 为最终契约**；代码内部读取后可做归一化，例如映射为 `x_eval_token/x_eval_run_id/...` 以便与 snake_case 变量名/配置一致，但归一化名不应出现在对外契约字段表中。

### C2. target：`POST /api/v1/eval/chat`（响应顶层）

- 必填（P0）：
  - `answer`
  - `behavior`
  - `latency_ms`
  - `capabilities`
  - `meta`（至少含 `mode`）
- 按 capabilities 可选（但 case 强约束可提升为必填）：
  - `sources[]`
  - `tool`
  - `evidence_map[]`（P1）
  - `reflection`（可选）

### C3. `meta`（P0 关键字段，snake_case）

- 门控/检索：
  - `retrieve_hit_count`
  - `low_confidence`
  - `low_confidence_reasons[]`
- Guardrail：
  - `guardrail_triggered`
  - `disabled_reason`（用于 `POLICY_DISABLED`）
- 引用闭环（P0）：
  - `retrieval_hit_id_hashes[]`（前 N）
  - `retrieval_candidate_limit_n`
  - `retrieval_candidate_total`
  - `canonical_hit_id_scheme`
- travel-ai 可控性（P0）：
  - `stage_order[]`
  - `step_count`
  - `replan_count`
  - `plan_parse_attempts`
  - `plan_parse_outcome`

---

## 附录 D：统一 error_code 与配置前缀对齐

### D1. error_code（统一口径，P0 必须）

> 要求：同一失败点在 Vagent/travel-ai 必须打同码，否则横向对比不可用。

- `AUTH`
- `RATE_LIMITED`
- `TIMEOUT`
- `UPSTREAM_UNAVAILABLE`
- `PARSE_ERROR`
- `RETRIEVE_EMPTY`
- `RETRIEVE_LOW_CONFIDENCE`
- `GUARDRAIL_TRIGGERED`
- `SOURCE_NOT_IN_HITS`
- `CONTRACT_VIOLATION`
- `SECURITY_BOUNDARY_VIOLATION`
- `POLICY_DISABLED`
- `UNKNOWN`

### D2. 配置前缀（发起方 vs 被测方）

- eval（发起方）：`eval.api.*`
- Vagent（被测方）：`vagent.eval.api.*`
- travel-ai（被测方）：`travelai.eval.api.*`（或该仓库约定的 `app.eval.api.*`，但必须提供一张映射表并保证语义一致）

---

## 附录 E：travel-ai Plan JSON Schema（最终版，可引用）

> 目标：把 travel-ai 的 Plan 产物做成“单一事实来源（SSOT）”，让 **Parser / Prompt / eval 规则**都引用同一份最终 schema，避免各写各的造成返工。
>
> 约束（P0）：P0 只要求“**固定线性阶段** + **最多一次修复** + **可观测**”，不引入 DAG/并行/动态重规划；因此 schema 也保持最小可用形状，额外字段允许但不得破坏必填字段的解析。

### E1. Schema（Draft 2020-12）

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://vagent.local/schema/travel-ai/plan.v1.schema.json",
  "title": "travel-ai Plan (P0 v1)",
  "type": "object",
  "additionalProperties": true,
  "required": ["plan_version", "steps", "constraints"],
  "properties": {
    "plan_version": { "type": "string", "const": "v1" },
    "goal": { "type": "string", "minLength": 1 },
    "steps": {
      "type": "array",
      "minItems": 1,
      "maxItems": 8,
      "items": {
        "type": "object",
        "additionalProperties": true,
        "required": ["step_id", "stage", "instruction"],
        "properties": {
          "step_id": { "type": "string", "minLength": 1 },
          "stage": {
            "type": "string",
            "enum": ["PLAN", "RETRIEVE", "TOOL", "WRITE", "GUARD"]
          },
          "instruction": { "type": "string", "minLength": 1 },
          "tool": {
            "type": ["object", "null"],
            "additionalProperties": true,
            "required": ["name", "args"],
            "properties": {
              "name": { "type": "string", "minLength": 1 },
              "args": { "type": "object", "additionalProperties": true }
            }
          },
          "expected_output": { "type": ["string", "null"] }
        }
      }
    },
    "constraints": {
      "type": "object",
      "additionalProperties": true,
      "required": ["max_steps", "total_timeout_ms", "tool_timeout_ms"],
      "properties": {
        "max_steps": { "type": "integer", "minimum": 1, "maximum": 20 },
        "total_timeout_ms": { "type": "integer", "minimum": 1 },
        "tool_timeout_ms": { "type": "integer", "minimum": 1 }
      }
    },
    "notes": { "type": ["string", "null"] }
  }
}
```

### E2. 最小示例（P0）

```json
{
  "plan_version": "v1",
  "goal": "Plan a 3-day trip to Shanghai for a first-time visitor.",
  "steps": [
    { "step_id": "s1", "stage": "PLAN", "instruction": "Summarize constraints and key preferences." },
    { "step_id": "s2", "stage": "RETRIEVE", "instruction": "Retrieve local travel tips and opening hours." },
    { "step_id": "s3", "stage": "WRITE", "instruction": "Write a day-by-day itinerary with time blocks." },
    { "step_id": "s4", "stage": "GUARD", "instruction": "Ensure safety, budget, and citations policy are met." }
  ],
  "constraints": { "max_steps": 5, "total_timeout_ms": 45000, "tool_timeout_ms": 8000 }
}
```

### E3. P0 解释与硬约束（与可验收字段对齐）

- **固定线性阶段**：`steps[*].stage` 只允许 `PLAN|RETRIEVE|TOOL|WRITE|GUARD`，由执行器按顺序串行执行；P0 不允许 DAG/回环。
- **限步可验收**：执行后的 `meta.step_count` 应等于“实际执行过的阶段数”；且 `meta.stage_order[]` 必须为上述枚举的子序列。
- **修复一次**：PlanParser 若解析失败，只允许最多 1 次 repair；观测字段以 `meta.plan_parse_attempts/meta.plan_parse_outcome` 输出（见附录 C3）。

### F. P0 后收口（指针，防门控漂移）

P0 过线后，将 **SSE 主链路与 `POST /api/v1/eval/chat` 的空命中 / 低置信 / error_code 门控** 收敛到**单一事实来源**（共享模块），避免两套分支长期分叉。详细条目见 `plans/vagent-upgrade.md` **P1-0**；执行节奏见 `plans/leadership-execution.md` **§10**。

