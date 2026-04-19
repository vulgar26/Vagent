#!/usr/bin/env bash
# 从 CI 或本机触发 vagent-eval 一次完整 run（请求体由环境变量注入）。
# 依赖：curl、jq。
# 环境变量：
#   EVAL_BASE_URL        必填，例如 https://eval.example.com
#   EVAL_RUN_PAYLOAD_JSON 必填，POST /api/v1/eval/runs 的 JSON 字符串
#   EVAL_HTTP_TOKEN      可选，设置则发送 Authorization: Bearer <token>
#
# 结束轮询：run JSON 含 finished_at，或 status 属于 completed/succeeded/done（不区分大小写）。

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

for _ in $(seq 1 120); do
  BODY="$(curl -fsS "${HDR[@]}" "${BASE}/api/v1/eval/runs/${RUN_ID}")"
  FIN="$(echo "$BODY" | jq -r '.finished_at // empty')"
  ST="$(echo "$BODY" | jq -r '.status // empty' | tr '[:upper:]' '[:lower:]')"
  if [[ -n "$FIN" && "$FIN" != "null" ]]; then
    echo "finished_at set; run finished."
    break
  fi
  case "$ST" in
    completed|succeeded|done)
      echo "status=${ST}; run finished."
      break
      ;;
  esac
  sleep 15
done

echo "--- report summary ---"
curl -fsS "${HDR[@]}" "${BASE}/api/v1/eval/runs/${RUN_ID}/report?error_code_top_n=20" | jq .
