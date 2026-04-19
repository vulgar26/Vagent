# Quote-only 门控（评测 `POST /api/v1/eval/chat`）

本页是 **实现语义 SSOT**：与代码 `EvalQuoteOnlyGuard`、配置 `vagent.guardrails.quote-only.*` 一致；修改判定时应同步更新本文件。

## 何时生效

同时满足：

1. 服务端 **`vagent.guardrails.quote-only.enabled=true`**（环境变量 `VAGENT_GUARDRAILS_QUOTE_ONLY_ENABLED`）。
2. 请求体 **`quote_only: true`**（题集 / eval case 侧打开）。
3. 当前路径仍为 **`behavior=answer`**，且检索候选 **非空**（有正文可组成 corpus）。

若候选为空（例如门控短路后无命中），**不运行** quote-only（避免无 corpus 时误杀）。

## Corpus 口径

将本次 **候选 `RetrieveHit` 的 `content`** 非空项拼成一条大字符串（空白规范化），与 `sources[].snippet` 同源材料，便于以后与 citation 对齐。

## 严格度档位（`strictness`）

配置：`vagent.guardrails.quote-only.strictness`，取值 **`relaxed` / `moderate` / `strict`**（不区分大小写）；非法值回退 **`moderate`**。

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

## 与 `capabilities.guardrails.quote_only`

当服务端总开关 **`quote-only.enabled=true`** 时，`capabilities.guardrails.quote_only` 为 **true**，表示 target **支持**该能力（题集仍须 `quote_only: true` 才会实际执行检查）。

## 调参建议

- 题集以中文叙述为主、数字少：可从 **relaxed** 起步。
- 混合 SKU / 金额 / 订单号：用 **moderate**（默认）。
- 英文说明题多、希望减少「凭空英文术语」：**strict**。

修改档位后若 CI 或 eval 大量误杀，应优先 **收紧 corpus**（检索是否召回正确 chunk），再考虑降档。
