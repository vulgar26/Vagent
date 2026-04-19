# 评测知识库（`target_id=vagent`）操作说明

评测请求里 **`X-Eval-Target-Id: vagent`** 时，检索租户为 **`EvalStableUserId.forDefaultVagentTarget()`**（与 `scripts/seed-eval-kb-user.sql` 中的用户 `id` 一致）。向 KB 灌文档必须落在该 `user_id` 下，否则 eval 侧长期 `sources_count=0`。

## 1. 空库跑「空命中 / 澄清」桶

P0 dataset 中大量 `expected/clarify`、`rag/empty` 依赖 **`meta.retrieve_hit_count == 0`** 或应用内低置信门控。若本机曾灌过泛化文档，向量仍可能误命中。

**做法**：跑 eval 前执行清空脚本（仅删该评测用户的文档与分块）：

```bash
psql -U postgres -d vagent -f scripts/reset-eval-kb-for-vagent-target.sql
```

然后再启动 vagent-eval 全量 run。需要带引用的 **answer** 类用例时，再在空库或按需执行 `POST /api/v1/kb/documents` 灌入**与用例问题强相关**的短文档（见 `plans/datasets/p0-dataset-v0.jsonl`）。

**一键灌 gold（PowerShell）**：在仓库根目录执行 `.\scripts\ingest-p0-eval-gold-kb.ps1`（默认 `http://localhost:8080`）。正文在 **`scripts/ingest-p0-eval-gold-kb-body.txt`**（UTF-8），脚本显式按 UTF-8 读取，避免 Windows PowerShell 5.1 解析脚本内中文 here-string 报错；亦可传 `-BaseUrl`。

## 2. 拆两轮跑（推荐日报口径）

| 轮次 | KB 状态 | 目的 |
|------|---------|------|
| A | 执行 `reset-eval-kb-for-vagent-target.sql` 后**不灌**或只灌与「负例问句」正交的语料 | 拉高 `rag/empty`、`部分 clarify` |
| B | 按用例灌 **gold** 片段 | 拉高 `requires_citations`、`expected/answer` |

两轮分别记 `run_id` 与 KB 策略说明，避免「一份库想同时满足 32 条」的不可达期望。

## 3. 混合检索开关对比（同一 `dataset_id`）

在评测服务上对**同一冻结题集**分别跑「hybrid 关 / hybrid 开」等 run，用 `scripts/compare-eval-runs.ps1` 做差分并加 **`-RequireSameDataset` + `-StrictContractGate`** 做门禁（契约类 `error_code` 不得由 PASS 恶化）。步骤与参数说明见 **`scripts/README-hybrid-rerank-ab.md`**。

## 4. 应用内门控（可选）

在 `application-local.yml`（勿提交密钥）可对 `vagent.eval.api` 配置：

- **`low-confidence-cosine-distance-threshold`**：首条命中余弦距离 **大于** 该值则 `clarify`（距离越小越相似，需按嵌入模型调参）。
- **`low-confidence-query-substrings`**：query 含任一子串则 `clarify`，用于对齐「这个东西」「那个项目」等指代不明问句。

详见 `application-local.example.yml` 中注释示例。默认均为关闭，不改变既有单测基线。（上文「混合检索」见 §3。）

## 5. `tool_policy=real`（MCP 真调用）

当 **`vagent.mcp.enabled=true`**、**`vagent.mcp.allowed-tools`** 非空且进程内存在 **`McpClient`** Bean 时，`POST /api/v1/eval/chat` 在 **`expected_behavior=tool`** + **`tool_policy=real`** 下会调用 MCP：JSON **`tool_stub_id`** 为工具名（须在白名单内），超时与桩工具共用 **`vagent.eval.api.stub-tool-timeout-ms`**。MCP 未就绪时返回 **`behavior=clarify`** 与说明文案，避免误走 RAG 占位 `answer`。
