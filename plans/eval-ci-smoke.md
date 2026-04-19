# 评测链路 CI 烟测（本仓）

## 目的

- **先**跑 `com.vagent.eval` 下全部 `*Test.java`（`POST /api/v1/eval/chat` 契约、门控、桩工具、quote-only 等），**尽快**暴露评测回归。  
- **再**跑其余模块单测，且 **排除** `**/eval/**/*Test.java`，避免同一 CI job 里连续加载多轮完整 Spring 上下文导致 **native OOM**（Windows 等资源紧张环境尤甚）。

实现方式：**Maven profiles** `eval-smoke` 与 `skip-eval-in-ci`（见根 `pom.xml`），由 **[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)** 顺序执行。

## 本地命令

```bash
./mvnw test -P eval-smoke
./mvnw test -P skip-eval-in-ci
```

与 CI 一致；本机仍可直接 **`./mvnw test`** 跑全量（含 eval），不做拆分。

## 可观测与回归基线

- **在线 eval 全量 / 夜间**：仍由 **[`ci-eval-github-actions.md`](ci-eval-github-actions.md)**、**`eval-remote.yml`** 与 secrets 驱动；与上述 **单元/烟测** 互补（前者不拉起本仓 JVM 跑题，后者不依赖外网 eval）。  
- **基线 run + compare 留证**：按 **[`regression-compare-standard-runbook.md`](regression-compare-standard-runbook.md)** §1–§3，冻结同一 `dataset_id` 下 `base` / `cand` run，并用 **`scripts/compare-eval-runs.ps1`**（可选 **`-StrictContractGate`**）做契约类回归门禁。
