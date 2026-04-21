# Quote-only 门控（评测 `POST /api/v1/eval/chat` 与可选主对话 SSE）

本页是 **实现语义 SSOT**：与代码 `EvalQuoteOnlyGuard`、配置 `vagent.guardrails.quote-only.*` 一致；修改判定时应同步更新本文件。

## 规则包范围（`scope`）

配置：`vagent.guardrails.quote-only.scope`（环境变量 `VAGENT_GUARDRAILS_QUOTE_ONLY_SCOPE`），取值（不区分大小写，`-` / `_` 等价）：

| `scope` | 含义 | 与 `strictness` 的关系 |
|---------|------|-------------------------|
| **`digits_only`** | 仅 **A**：连续数字串（≥3）须在 corpus 中出现。 | `strictness` 的 moderate/strict **不再追加** token/英文词层（仅数字层生效）。 |
| **`digits_plus_tokens`**（默认） | **A + B**（及按档位的 **STRICT 英文词**）：与改 `scope` 前的历史行为一致。 | `relaxed`：仅 A；`moderate`：A + 长 token/混合码；`strict`：再卡英文长词。 |
| **`digits_plus_tokens_plus_evidence`** | **A + B + C**：在通过子串核对后，若答案中存在长度 ≥3 的数字串，则每个数字串须在 **`evidence_map` 的 numeric 条目** 上可核对：**`claim_value` 归一化后与该数字串一致**（与 `EvidenceMapExtractor` 的 numeric span 切分同源）。`sources` 非空；无数字串时不强制非空 `evidence_map`。 | 子串层仍完全受 `strictness` 控制；**C 仅作用于数字串 → evidence 绑定**。 |

失败时 **C 层** 使用 `meta.reflection_reasons` 首条 **`QUOTE_ONLY_EVIDENCE_UNBOUND`**（子串层仍为 **`QUOTE_ONLY_UNGROUNDED`**），根级仍为 `behavior=deny`、`error_code=GUARDRAIL_TRIGGERED`。

评测 **`meta`** 会写入 **`quote_only_scope`**（小写字符串，与配置一致）。

## Eval：`reflection` 与 `quote_only` 的执行顺序

当 **`vagent.guardrails.reflection.enabled=true`** 且请求 **`quote_only: true`** 同时满足各自前提时，`EvalChatController` **先**执行 `EvalReflectionOneShotGuard`（引用闭环、低置信超长），**仅当**根级 **`behavior` 仍为 `answer`** 时再执行 `EvalQuoteOnlyGuard`。若 reflection 已改为 `deny`/`clarify`，**不再**跑 quote-only，避免重复门控与矛盾 `meta`。

## 主对话 SSE（`apply-to-sse-stream`）

除 eval 接口外，主链路 **`RagStreamChatService`** 可选对齐同一套 `EvalQuoteOnlyGuard` 判定（corpus 来自当次 RAG 命中的 `RetrieveHit.content`）。

附加条件（全部满足才启用缓冲门控）：

1. **`vagent.guardrails.quote-only.enabled=true`**
2. **`vagent.guardrails.quote-only.apply-to-sse-stream=true`**（`VAGENT_GUARDRAILS_QUOTE_ONLY_APPLY_TO_SSE_STREAM`）
3. **`branch=RAG`** 且 **`retrieve_hit_count>0`** 且 corpus 非空

行为说明：

- 与默认「边生成边 `chunk`」不同：启用后服务端 **缓冲 LLM 全文**，再发 **首条 `type=meta`**（含检索 trace 与门控结果）与 **一条 `type=chunk`**（全文或拒答替换文案），随后 **`done`**；便于在发出任何用户可见正文前完成 quote-only。
- 该首条 **`meta`** 在 `quote_only` / `quote_only_scope` / `quote_only_strictness` 等之外，另含 **`meta.capabilities.guardrails`**（与评测 **`POST /api/v1/eval/chat`** 根级 **`capabilities.guardrails`** 同源：`quote_only`、`quote_only_scope`、`quote_only_scopes_supported`、`evidence_map`、`reflection`），由 **`EvalCapabilitiesObjects`** 构造；**`capabilities.retrieval` / `tools` / `streaming`** 仍以评测 HTTP 响应为准，SSE 此路径不伪造。
- **不设**按请求头单独开关，仅由配置统一控制，避免客户端随意打开缓冲模式。

## 何时生效（仅评测 HTTP）

同时满足：

1. 服务端 **`vagent.guardrails.quote-only.enabled=true`**（环境变量 `VAGENT_GUARDRAILS_QUOTE_ONLY_ENABLED`）。
2. 请求体 **`quote_only: true`**（题集 / eval case 侧打开）。
3. 当前路径仍为 **`behavior=answer`**，且检索候选 **非空**（有正文可组成 corpus）。

若候选为空（例如门控短路后无命中），**不运行** quote-only（避免无 corpus 时误杀）。

## Corpus 口径

将本次 **候选 `RetrieveHit` 的 `content`** 非空项拼成一条大字符串（空白规范化），与 `sources[].snippet` 同源材料，便于以后与 citation 对齐。

## 严格度档位（`strictness`）

配置：`vagent.guardrails.quote-only.strictness`，取值 **`relaxed` / `moderate` / `strict`**（不区分大小写）；非法值回退 **`moderate`**。须与上表 **`scope`** 联读：`digits_only` 时仅本表中 **RELAXED** 数字规则生效。

| 档位 | 含义（大白话） | 规则摘要 |
|------|----------------|----------|
| **RELAXED** | 只卡「像编出来的长数字」 | 答案里每个 **长度 ≥3 的连续数字串** 必须在 corpus 中出现（子串匹配）。 |
| **MODERATE**（默认） | 适中：数字 + 长编码/混合码 | RELAXED **加上**：按 Unicode 字母数字切出的 token 中，**长度 ≥8** 的整段，或 **长度 ≥4 且含数字** 的整段，必须在 corpus 中出现；纯 ASCII 字母数字段比较时 **忽略大小写**。 |
| **STRICT** | 在 MODERATE 上再卡英文长词 | MODERATE **加上**：答案中每个 **长度 ≥5 的纯英文单词**（`[A-Za-z]{5,}`）且不在小型停用词表内，须在 corpus 中出现（忽略大小写）。 |

### 刻意不做的（避免误杀）

- 不做「整句语义相似度」、不做 LLM 裁判。
- 中文 **不按字逐条强制**（MODERATE 仅靠长字母数字段与数字；STRICT 只追加 **英文** 词）。
- 标点、空格、换行不影响：规则在规范化后的 `answer` 与 `corpus` 上做子串匹配。

## 失败时的响应

- 根级 **`behavior=deny`**，**`error_code=GUARDRAIL_TRIGGERED`**（与现有 reflection 门控一致）。
- **`meta.guardrail_triggered=true`**，`meta.reflection_outcome` / `meta.reflection_reasons` 与根级对齐（与 `EvalBehaviorMetaSync` 一致）。
- `reflection_reasons` 至少含 **`QUOTE_ONLY_UNGROUNDED`**，并带一条简短机器可读原因（如 `digit_run:…` / `long_token:…`）。

## 与 `capabilities.guardrails.*`（quote-only）

当服务端总开关 **`quote-only.enabled=true`** 时：

| JSON 字段 | 含义 |
|-----------|------|
| **`capabilities.guardrails.quote_only`** | **true**：target 支持 quote-only（题集仍须在请求体写 **`quote_only: true`** 才会实际跑门控）。 |
| **`capabilities.guardrails.quote_only_scope`** | 服务端**当前配置**的 **`vagent.guardrails.quote-only.scope`**（小写 snake_case，`-` 与 `_` 统一为 `_`），便于命题 / 流水线对齐部署值。 |
| **`capabilities.guardrails.quote_only_scopes_supported`** | 本实现**识别且可配置**的全部 `scope` 取值（与 `EvalQuoteOnlyGuard.Scope` 枚举顺序一致），供 vagent-eval 等客户端做契约校验。 |

当 **`quote_only` 为 false** 时，后两个字段在 JSON 中**省略**（`null` 不序列化）。

## 调参建议

- 题集以中文叙述为主、数字少：可从 **relaxed** 起步。
- 混合 SKU / 金额 / 订单号：用 **moderate**（默认）。
- 英文说明题多、希望减少「凭空英文术语」：**strict**。

修改档位后若 CI 或 eval 大量误杀，应优先 **收紧 corpus**（检索是否召回正确 chunk），再考虑降档。

## 合并后与 CI 回归建议

- 变更 **`EvalQuoteOnlyGuard`**、评测门控顺序、或 **`EvidenceMapExtractor`** 等与 quote-only 共用逻辑后，应执行 **全量** `./mvnw test`（勿仅跑 `EvalQuoteOnlyGuardTest` 等子集），以免其它 Spring 集成测试回归未被发现。
- 题集或流水线若 **固定依赖**某一 **`scope`** 语义，请在部署环境显式设置 **`VAGENT_GUARDRAILS_QUOTE_ONLY_SCOPE`**（或 `application.yml` 中的 `vagent.guardrails.quote-only.scope`），避免默认值与命题假设不一致。
- **`full-answer-enabled`** + **`fake-stream`** 下与 `POST /api/v1/eval/chat` 对齐的 HTTP 回归：**`EvalChatControllerQuoteOnlyDigitsOnlyFullAnswerMockMvcTest`**（`scope=digits_only`）、**`EvalChatControllerQuoteOnlyPlusEvidenceFullAnswerMockMvcTest`**（`scope=digits_plus_tokens_plus_evidence` 通过 / 截断 snippet 失败）。
