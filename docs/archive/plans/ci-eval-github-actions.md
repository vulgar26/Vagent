# CI / 夜间连接「真实 eval target」说明

本仓库 **可以** 在 GitHub Actions 里跑「连真实评测服务」的步骤；前提不是「业务站必须上公网」，而是：

**GitHub 托管的 `ubuntu-latest` _runner 只能访问公网可达的 HTTP(S) 地址。**

因此三选一即可：

| 方案 | 适用场景 |
|------|-----------|
| **A. 评测服务暴露在公网**（HTTPS + 鉴权） | 最小工程量：在 GitHub **Repository secrets** 里配置 `EVAL_BASE_URL`、`EVAL_HTTP_TOKEN`、`EVAL_RUN_PAYLOAD_JSON` 等，由 workflow 调用。 |
| **B. 自托管 GitHub Actions Runner** | 评测与 target 都在内网：runner 与 `EVAL_BASE_URL` 同一 VPC，无需把 eval 暴露公网。 |
| **C. 隧道 / 反向代理** | 临时联调：例如在内网起 frp/cloudflared，把 eval 的 URL 映射到一个公网域名，再在 secrets 里写该 URL。 |

若 **既不暴露 eval，也不用自托管 runner**，则 **无法在 GitHub 云端**直连内网 eval；此时 CI 仍可做 **`./mvnw test`**（本仓 `ci.yml`），远程全量评测留在本机或 B 方案执行。

## 本仓提供的自动化

- **`.github/workflows/ci.yml`**：默认 **不连 eval 服务**；先跑本仓 **eval 包烟测** 再跑其余单测（见 **[`eval-ci-smoke.md`](eval-ci-smoke.md)**）。  
- **`.github/workflows/eval-remote.yml`**：`workflow_dispatch`（手动）+ `schedule`（默认定时，可按需改 cron）。  
  - 若未配置 `EVAL_BASE_URL` secret：步骤会 **跳过** 远程调用并打印说明，**不把整 job 标红**（避免未接 eval 的 fork 天天失败）。  
  - 配置 secret 后：调用 **`scripts/ci-eval-remote.sh`** 发起一次 **全量** run（payload 由你方在 secret 里提供完整 JSON）；失败时 job 标红；后续步骤 **`actions/upload-artifact`** 上传 **`eval-remote-report.json`**（存在时才上传，避免误报）。
- **`.github/workflows/hybrid-ab-compare.yml`**：`workflow_dispatch`，对已有两次 **`run_id`** 调用 **`scripts/compare-eval-runs.ps1`**（**`-RequireSameDataset`** + **`-StrictContractGate`**）；依赖 **`EVAL_BASE_URL`** secret 与可选 **`EVAL_HTTP_TOKEN`**；未配置时跳过。详见 **`scripts/README-hybrid-rerank-ab.md`** §5。
- **`scripts/ci-eval-remote.sh`**：用 `curl` + `jq` 做 `POST /api/v1/eval/runs`、轮询 `GET /api/v1/eval/runs/{id}`、最后拉 **report** 打摘要，并把报告写入工作区 **`eval-remote-report.json`**（供 Actions 上传 artifact）。  
  - 轮询结束条件：响应 JSON 中出现 **`finished_at` 非空** 或 **`status` 为 `completed`/`succeeded`/`done`（大小写不敏感）** 之一即视为结束；若 **`status`** 为 **`failed`/`error`/`canceled`/`cancelled`**，立即结束并视为失败。若 eval 实现字段不同，请改脚本或让 eval 对齐其一。  
  - 轮询超时：默认 **120** 轮、每轮间隔 **15s**（可用环境变量 **`EVAL_POLL_MAX_ROUNDS`**、**`EVAL_POLL_SLEEP_SEC`** 覆盖）；超时退出码 **2**。  
  - 失败退出码 **1**：服务端 run 为失败终态；或 report JSON 中显式出现 **`p0_hard_gate_passed`/`overall_pass`/`p0_gate_passed` 之一为布尔 `false`**（字段缺失则不据此判失败）。  
  - 在 **GitHub Actions** 中若设置了 **`GITHUB_STEP_SUMMARY`**，脚本会追加简短 Markdown 摘要（含 `run_id`）。

## 需要在 vagent-eval 侧准备什么

1. **公网或 runner 可达** 的 `EVAL_BASE_URL`（无尾部斜杠亦可，脚本会 `trim`）。
2. 若需鉴权：把 token 放在 **`EVAL_HTTP_TOKEN`**（脚本以 `Authorization: Bearer …` 发送；若 eval 用 `X-Api-Key` 等，请改脚本或网关统一成 Bearer）。
3. **`EVAL_RUN_PAYLOAD_JSON`**：`POST /api/v1/eval/runs` 的 **完整 JSON 请求体**（含 dataset、targets、并发等），由你们按 vagent-eval 当前 API 维护；本仓不硬编码题集 ID，避免与 `*-upgrade.md` 双源。

夜间 **全量**：把 payload 配成与人工全量相同的 dataset / target 即可；脚本不截断题数。

## 安全注意

- **勿**把明文 token 写进 workflow 或仓库文件，只用 **Secrets**。  
- `EVAL_RUN_PAYLOAD_JSON` 可能含内网 hostname；若仓库对 fork 开放 PR，注意 **pull_request** 来自 fork 时 secrets 不可用（GitHub 默认行为），远程 eval 只适合 **`push` 到本仓库**、**`workflow_dispatch`** 或 **自托管 runner**。

## 与 `plans/README.md` 的关系

回归留证、compare 门禁仍以 [`regression-compare-standard-runbook.md`](regression-compare-standard-runbook.md) 与 `scripts/compare-eval-*.ps1` 为准；本页只说明 **GitHub 侧如何接到 eval**。
