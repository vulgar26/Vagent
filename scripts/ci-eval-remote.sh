#!/usr/bin/env bash
# 从 CI 或本机触发 vagent-eval 一次完整 run（请求体由环境变量注入）。
# 依赖：curl、jq。
# 环境变量：
#   EVAL_BASE_URL           必填，例如 https://eval.example.com
#   EVAL_RUN_PAYLOAD_JSON   必填，POST /api/v1/eval/runs 的 JSON 字符串
#   EVAL_HTTP_TOKEN         可选，设置则发送 Authorization: Bearer <token>
#   EVAL_POLL_MAX_ROUNDS    可选，轮询次数，默认 120
#   EVAL_POLL_SLEEP_SEC     可选，每轮间隔秒数，默认 15
#
# 结束轮询：run JSON 含 finished_at，或 status 属于 completed/succeeded/done（不区分大小写），
#           或 status 为 failed/error/canceled/cancelled（立即结束并视为失败）。
#
# 退出码：0 成功；1 run 失败 / 报告门禁未过 / 无法解析 run_id；2 轮询超时仍未结束。

set -euo pipefail

if [[ -z "${EVAL_BASE_URL:-}" ]]; then
  echo "EVAL_BASE_URL is empty; skipping remote eval."
  exit 0
fi

if [[ -z "${EVAL_RUN_PAYLOAD_JSON:-}" ]]; then
  echo "EVAL_RUN_PAYLOAD_JSON is empty; skipping remote eval."
  exit 0
fi

BASE="${EVAL_BASE_URL%/}"
POLL_MAX="${EVAL_POLL_MAX_ROUNDS:-120}"
POLL_SLEEP="${EVAL_POLL_SLEEP_SEC:-15}"

TMP="$(mktemp)"
echo "$EVAL_RUN_PAYLOAD_JSON" >"$TMP"

HDR=()
if [[ -n "${EVAL_HTTP_TOKEN:-}" ]]; then
  HDR=(-H "Authorization: Bearer ${EVAL_HTTP_TOKEN}")
fi

echo "POST ${BASE}/api/v1/eval/runs"
CREATED="$(curl -fsS "${HDR[@]}" -X POST "${BASE}/api/v1/eval/runs" \
  -H "Content-Type: application/json" \
  --data-binary @"$TMP")"
rm -f "$TMP"

RUN_ID="$(echo "$CREATED" | jq -r '.run_id // .id // .runId // empty')"
if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
  echo "Could not parse run id from response:" >&2
  echo "$CREATED" >&2
  exit 1
fi

echo "run_id=${RUN_ID}"

DONE=0
RUN_FAIL=0
for _ in $(seq 1 "$POLL_MAX"); do
  BODY="$(curl -fsS "${HDR[@]}" "${BASE}/api/v1/eval/runs/${RUN_ID}")"
  FIN="$(echo "$BODY" | jq -r '.finished_at // empty')"
  ST="$(echo "$BODY" | jq -r '.status // empty' | tr '[:upper:]' '[:lower:]')"
  if [[ -n "$FIN" && "$FIN" != "null" ]]; then
    echo "finished_at set; run finished."
    DONE=1
    break
  fi
  case "$ST" in
    completed|succeeded|done)
      echo "status=${ST}; run finished."
      DONE=1
      break
      ;;
    failed|error|canceled|cancelled)
      echo "status=${ST}; run ended (failure terminal state)."
      DONE=1
      RUN_FAIL=1
      break
      ;;
  esac
  sleep "$POLL_SLEEP"
done

if [[ "$DONE" -ne 1 ]]; then
  echo "Timed out waiting for run to finish (${POLL_MAX} rounds, ${POLL_SLEEP}s sleep)." >&2
  exit 2
fi

FINAL="$(curl -fsS "${HDR[@]}" "${BASE}/api/v1/eval/runs/${RUN_ID}")"
ST="$(echo "$FINAL" | jq -r '.status // empty' | tr '[:upper:]' '[:lower:]')"
case "$ST" in
  failed|error|canceled|cancelled)
    RUN_FAIL=1
    ;;
esac

REPORT="$(curl -fsS "${HDR[@]}" "${BASE}/api/v1/eval/runs/${RUN_ID}/report?error_code_top_n=20")"

REPORT_FILE="eval-remote-report.json"
if ! echo "$REPORT" | jq . >"$REPORT_FILE" 2>/dev/null; then
  echo "$REPORT" >"$REPORT_FILE"
fi

echo "--- report summary ---"
jq . "$REPORT_FILE" 2>/dev/null || cat "$REPORT_FILE"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Remote eval (vagent-eval)"
    echo "- **run_id**: \`${RUN_ID}\`"
    echo "- **base**: \`${BASE}\`"
    echo "- **report file**: \`${REPORT_FILE}\` (artifact in GitHub Actions when uploaded)"
  } >>"$GITHUB_STEP_SUMMARY"
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "eval_run_id=${RUN_ID}"
    echo "eval_report_file=${REPORT_FILE}"
  } >>"$GITHUB_OUTPUT"
fi

# 若报告含布尔门禁字段且为 false，则判失败（字段缺失时不判）
if echo "$REPORT" | jq -e '
    (has("p0_hard_gate_passed") and (.p0_hard_gate_passed == false))
    or (has("overall_pass") and (.overall_pass == false))
    or (has("p0_gate_passed") and (.p0_gate_passed == false))
  ' >/dev/null 2>&1; then
  echo "Report indicates gate failure (p0_hard_gate_passed / overall_pass / p0_gate_passed)." >&2
  exit 1
fi

if [[ "$RUN_FAIL" -eq 1 ]]; then
  echo "Eval run failed (terminal status on server)." >&2
  exit 1
fi

exit 0
