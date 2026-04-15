# P0+ B 线 S1-D1：`EvalChatController` 出口清单 × 契约对照

**目的**：在加 `retrieval_hits`、加深 citation 断言前，固定「有哪些出口、哪些是 HTTP 非 200、契约层验什么」的事实来源。  
**源码锚点**：`src/main/java/com/vagent/eval/EvalChatController.java`（方法 `chat`）。

---

## 1. 控制流出口（按出现顺序）

| # | 类型 | 条件 | HTTP / 体 | 备注 |
|---|------|------|-----------|------|
| E1 | `throw` | `!tokenVerifier.isEnabled()` | **404** `NOT_FOUND`，**无** `EvalChatResponse` JSON | 隐藏存在性；eval 侧应记为「目标不可用 / 未暴露」类事实，非契约 JSON |
| E2 | `return` | `!tokenVerifier.verifyOrFalse(xEvalToken)` | **200** + `EvalChatResponse` | `behavior=deny`，`error_code=AUTH`，`sources=[]` |
| E3 | `return` | 检索未启用或服务缺失 | **200** + `EvalChatResponse` | `behavior=deny`，`error_code=POLICY_DISABLED`，`retrieve_hit_count=0` 等 meta |
| E4 | `return` | 鉴权通过且检索可用 | **200** + `EvalChatResponse` | 单条 `return`，**此前**由子分支改写 `answer` / `behavior` / `error_code` / meta |

**E4 子分支（合并到同一 `return`）**：

| 子分支 | 条件（摘要） | 典型 `behavior` / `error_code`（成功路径上仍可能被子分支覆盖） |
|--------|----------------|------------------------------------------------------------------|
| E4a | `hitCount == 0` | `clarify`，`RETRIEVE_EMPTY`，低置信 meta |
| E4b | `q.length() < MIN_QUERY_CHARS` | `clarify`，`RETRIEVE_LOW_CONFIDENCE`，低置信 meta |
| E4c | 否则 | `answer`，无 `error_code`（除非 reflection 改写） |
| E4d | `guardrailsProperties.reflection.enabled` 且 `EvalReflectionOneShotGuard` 返回 patch | 覆盖 `answer`/`behavior`/`error_code`，`guardrail_triggered` 等 meta |

**间接异常（未在 `chat` 内捕获）**：`buildRetrievalHitIdHashes` → `hmacSha256` 失败会 `IllegalStateException`，通常映射为 **5xx**，**无**正常 eval 契约体 —— 清单中应单列，避免与业务分支混淆。

**框架与下游（非 `chat` 手写 `return`/`throw`，运行中仍可能出现）**：

- `@Valid @RequestBody EvalChatRequest`：校验失败时一般由 Spring 在**进入** `chat` 方法体之前返回 **400**，响应为框架错误形态，**非** `EvalChatResponse`。
- `knowledgeRetrieveService.searchForRag(...)`：若抛出未捕获的运行时异常，通常 **5xx**，**非**正常 eval 契约 JSON。

---

## 2. `EvalChatContractValidator` 对照（vagent-eval SSOT）

**说明**：本仓库不含 `vagent-eval`；以下以 **评测侧** `com.vagent.eval.run.EvalChatContractValidator#validate(JsonNode root)` 为 SSOT，**合并前请在 `vagent-eval` 内打开该类核对**。

### 2.1 契约层**会**校验的（响应 JSON 顶层）

- `answer`：string  
- `behavior`：string  
- `latency_ms`：number  
- `capabilities`：object（存在即可；深层字段以评测实现为准）  
- `meta`：object，且 **`meta.mode` 为 string（必填）**

### 2.2 契约层**不**由该类校验的（常见误解纠正）

- **`sources[]`**、**`retrieval_hits`**：由 `RunEvaluator` 等业务路径/citation 规则处理，**不在**本契约校验器的 `validate` 里逐字段断言（与「先盘点再改 DTO」的动机一致：契约窄、业务宽）。  
- **`X-Eval-Token` / `X-Eval-Run-Id` / `X-Eval-Dataset-Id` / `X-Eval-Case-Id` / `X-Eval-Target-Id`**：这些是**请求头**；Controller 可选写入 `meta`（如 `x_eval_run_id`），**不是** `EvalChatContractValidator` 对响应体的标准校验项。实习生把「读头」与「契约 JSON 校验」混为一谈时需纠正。

### 2.3 与当前实现的缺口提示（供 S1-D2+）

- 当前 `EvalChatResponse` **无** `retrieval_hits` 字段；P0+ 要求补齐后，**契约校验器是否扩展**以 SSOT 与组长裁定为准。  
- `error_code`：若评测侧对顶层 `error_code` 有单独规则，以 `RunEvaluator` / 数据集期望为准，**勿默认**契约校验器已覆盖。

---

## 3. 契约失败与 `NOT_FOUND` 的记法

- **`CONTRACT_VIOLATION`（或等价命名）**：响应体可被解析为 JSON，但**不满足**契约（例如缺 `meta.mode`、类型不对）。  
- **`NOT_FOUND`（404）**：E1 等路径**没有** `EvalChatResponse`；清单中必须写明 **「非 JSON 契约体」**，避免报表把「未返回对象」误当成契约字段缺失。

---

## 4. 日终交付检查

- [x] 已将本文或等价清单发给组长（可附 PR 链接或飞书/邮件记录）。  
- [x] 已在 `vagent-eval` 中对照 `EvalChatContractValidator` 与本文 §2（**组长核对**：与实现一致，S1-D1 **PASS**）；日终或该类变更时再 diff 一次即可。

**版本**：与 `EvalChatController` 当前快照一致；行号易漂移，以 **类名 + 分支语义** 为准。
