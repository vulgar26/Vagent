# P0-2：`meta-trace-keys` 固化方案（vagent target）

本文说明 **vagent-eval** 里 `eval.targets[target-id=vagent].meta-trace-keys` 应如何选、谁维护、如何验收。  
**语义**：compare 的 `base_meta_trace` / `cand_meta_trace` **仅投影**这些键；**全量观测**仍以 `GET .../runs/{id}/results` 每条里的 **`meta`** 为准。

---

## 1. 目标

- compare 摘要 **稳定、可读、可 diff**：只含团队关心的检索归因键，避免把整个 `meta` 搬进 compare 导致噪声与体积问题。
- **变更可审计**：Vagent 若增删改 `meta` 键名或语义，同步更新本页与 eval 配置，避免「compare 里突然少了维度」无人知晓。

---

## 2. 原则

| 原则 | 说明 |
|------|------|
| **SSOT** | 观测键名以 **Vagent `EvalChatResponse.meta`**（snake_case）为准；eval 不解析业务含义，只存储与按列表投影。 |
| **最小够用** | `meta-trace-keys` 只列 **跨 run 最常对比** 的键；其余只在 results 全量 `meta` 里查。 |
| **与基线约定联动** | 正式 compare 仍须满足 `plans/regression-baseline-convention.md`（同一 `dataset_id` 等）。 |
| **明文 id** | 不要把 `retrieval_hit_ids` 放进 trace；eval 默认也会处理明文 hit id。哈希对比用既有契约字段即可。 |

---

## 3. 角色

| 角色 | 职责 |
|------|------|
| **Regression Owner** | 拍板「compare 摘要里必须出现哪些键」；发版前确认 eval 配置与本文一致。 |
| **Vagent 开发** | `meta` 键变更时更新 **§5**、提 MR 通知运维改 `application.yml`。 |
| **eval 运维** | 在 **vagent-eval** 部署配置中维护 `meta-trace-keys`；多环境（dev/staging）配置一致或文档说明差异。 |

---

## 4. 推荐配置（两档）

### 4.1 最小集（已足够起步）

用于快速看 hybrid + 命中 + top1 距离：

- `hybrid_lexical_mode`
- `hybrid_lexical_outcome`
- `retrieve_hit_count`
- `retrieve_top1_distance`
- `retrieve_top1_distance_bucket`

### 4.2 扩展集（分布 / hybrid 体量 / rerank / 门控）

在最小集基础上按需追加（均为 Vagent 当前可能写入的键）：

- `retrieve_topk_distance_p50`
- `retrieve_topk_distance_p95`
- `retrieve_topk_distance_buckets`
- `hybrid_enabled`
- `hybrid_primary_chunk_id_count`
- `hybrid_lexical_chunk_id_count`
- `hybrid_fused_chunk_id_count`
- `hybrid_chunk_id_delta_rate`
- `rerank_enabled`
- `rerank_outcome`
- `rerank_latency_ms`
- `low_confidence`
- `retrieval_candidate_total`
- `retrieval_candidate_limit_n`
- `behavior`（SSE 首帧 `meta` 与评测根级对齐时；eval 结果 `meta` 若落库也可选带）
- `error_code`（同上；成功路径常为缺省/`null`）

**说明**：`0` 命中时部分距离键可能不存在，compare 里缺键属预期，不单独算失败。

---

## 5. Vagent `meta` 键清单（维护用）

以下由 `EvalChatController` 与 `RagRetrieveResult#putRetrievalTrace` 写入（随代码演进；变更时请改本节并对照测试）。

**评测上下文 / 头**：`mode`，`x_eval_run_id`，`x_eval_dataset_id`，`x_eval_case_id`，`x_eval_target_id`，`x_eval_membership_top_n`（视请求而定）。

**检索与 membership**：`retrieve_hit_count`，`canonical_hit_id_scheme`，`retrieval_candidate_total`，`retrieval_candidate_limit_n`，`retrieval_hit_id_hashes`。

**Hybrid / rerank**：`hybrid_enabled`，`hybrid_lexical_outcome`，`hybrid_lexical_mode`，`hybrid_primary_chunk_id_count`，`hybrid_lexical_chunk_id_count`，`hybrid_fused_chunk_id_count`，`hybrid_chunk_id_delta_rate`，`rerank_enabled`，`rerank_outcome`，`rerank_latency_ms`。

**距离（有命中时）**：`retrieve_top1_distance`，`retrieve_top1_distance_bucket`，`retrieve_topk_distance_p50`，`retrieve_topk_distance_p95`，`retrieve_topk_distance_buckets`。

**门控 / 安全 / 反思（分支依赖）**：`low_confidence`，`low_confidence_reasons`，`disabled_reason`，`eval_safety_rule_id`，`guardrail_triggered`，`reflection_outcome`，`reflection_reasons`；**quote-only**（与 `quote_only` 请求同开时）：`quote_only`，`quote_only_strictness`，`quote_only_scope`，`quote_only_passed`；**P1-0**：短路路径可含 **`behavior`**、**`error_code`**（与评测 JSON 根级同值，便于 SSE 与 eval 对照）；条件满足时曾有 `retrieval_hit_ids`（不建议进 trace）。

**可选完整 `answer`（`vagent.eval.api.full-answer-enabled=true`）**：`eval_full_answer`（boolean），`eval_full_answer_outcome`（`ok` / `timeout` / `interrupted` / `error`）；未门控短路且走聚合 LLM 时出现；compare 是否纳入 trace 由 Regression Owner 决定（默认可不进最小集，避免与基线噪声）。

---

## 6. 具体执行步骤（顺序）

1. **Regression Owner** 与 Vagent 对齐：正式 compare 更关心「最小集」还是「扩展集」（可先最小，两周后再扩）。  
2. **eval 运维** 在 **vagent-eval** 的 `application.yml`（或等价配置源）中，对 **`target-id: vagent`** 写入 `meta-trace-keys` 列表（snake_case，与 §4 一致）。  
3. **重启或热加载** eval（按你们部署方式），确认配置生效。  
4. **验收**：同一 `dataset_id` 跑 `base` + `cand`，`GET .../compare`；在 `regressions` / `improvements` 中任取一行，确认 `base_meta_trace` / `cand_meta_trace` **仅含**配置键且值与 `GET .../results` 中对应 case 的 `meta` 一致。  
5. **登记**：在内部运维表或本页 **§7** 记下「生效日期 + 环境 + 配置 commit」。

---

## 7. 配置登记（运维填写）

| 环境 | 生效日期 | meta-trace-keys 档位 | 配置来源（commit/工单） |
|------|----------|----------------------|-------------------------|
| dev | `2026-04-18` | **扩展档**（与 `run_e4d7fa1ce57f47b3a0ef4ae2198a0918` 的 `results[].meta` 键集合对齐；含 hybrid / distance / rerank / `low_confidence` / candidate 上限） | 以 vagent-eval 中 `target-id=vagent` 的 `meta-trace-keys` 为准；对照本文 §4.2 |
| staging | （待填） | 最小 / 扩展 | 上线后由 Regression Owner 补一行 |

---

## 8. 相关文档

- 回归基线与 compare 前提：`plans/regression-baseline-convention.md`
- 标准 compare 与留证（P0-3）：`plans/regression-compare-standard-runbook.md`
- P1 report/看板与契约治理：`plans/regression-p1-report-governance.md`
- Eval API：`plans/eval-upgrade.md`
- Vagent 升级与契约：`plans/vagent-upgrade.md`
