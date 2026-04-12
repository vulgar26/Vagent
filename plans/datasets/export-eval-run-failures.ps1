# Export non-PASS eval results for Day6 regression assignment.
# Usage:
#   .\export-eval-run-failures.ps1 -RunId "run_..." [-EvalBase "http://localhost:8099"] [-OutCsv ".\failures.csv"]
param(
    [Parameter(Mandatory = $true)][string]$RunId,
    [string]$EvalBase = "http://localhost:8099",
    [string]$OutCsv = ""
)

$offset = 0
$limit = 200
$all = New-Object System.Collections.ArrayList

while ($true) {
    $uri = "$EvalBase/api/v1/eval/runs/$RunId/results?offset=$offset&limit=$limit"
    $resp = Invoke-RestMethod -Uri $uri -Method Get
    if (-not $resp.results -or $resp.results.Count -eq 0) { break }
    foreach ($row in $resp.results) { [void]$all.Add($row) }
    if ($resp.results.Count -lt $limit) { break }
    $offset += $limit
}

$fail = $all | Where-Object { $_.verdict -ne "PASS" -and $_.verdict -ne "SKIPPED_UNSUPPORTED" }

function Suggest-Owner {
    param([string]$Code, [string]$TargetId)
    if ($Code -eq "CONTRACT_VIOLATION") {
        if ($TargetId -match "travel-ai") { return "C (travel-ai)" }
        if ($TargetId -match "vagent") { return "B (Vagent)" }
        return "B/C (target owner)"
    }
    if ($Code -eq "UNKNOWN") { return "A (eval)" }
    if ($Code -eq "AUTH" -or $Code -eq "TIMEOUT" -or $Code -eq "UPSTREAM_UNAVAILABLE") { return "A+C (eval+target)" }
    return "TBD"
}

function Get-NextAction {
    param([string]$ErrorCode, [string]$TargetId)
    switch ($ErrorCode) {
        "UNKNOWN" { return "RunEvaluator: map HTTP/body/field gaps to concrete SSOT error_code; add debug in eval_result." }
        "CONTRACT_VIOLATION" { return "Align target POST /api/v1/eval/chat response with P0 contract (answer/behavior/latency_ms/capabilities/meta)." }
        "AUTH" { return "Check X-Eval-Token / eval.api.enabled / allowlist on target and eval." }
        "TIMEOUT" { return "Raise client timeout or reduce target latency; check travel-ai deps (LLM/DB)." }
        default { return "Triage with results.debug + target logs; update evaluator mapping if misclassified." }
    }
}

$runMeta = Invoke-RestMethod -Uri "$EvalBase/api/v1/eval/runs/$RunId" -Method Get
$tid = [string]$runMeta.target_id

$rows = foreach ($r in $fail) {
    [PSCustomObject]@{
        case_id     = $r.case_id
        verdict     = $r.verdict
        error_code  = $r.error_code
        latency_ms  = $r.latency_ms
        owner       = (Suggest-Owner -Code $r.error_code -TargetId $tid)
        next_action = (Get-NextAction -ErrorCode $r.error_code -TargetId $tid)
    }
}

$rows | Format-Table -AutoSize

if ($OutCsv -ne "") {
    $rows | Export-Csv -Path $OutCsv -Encoding UTF8 -NoTypeInformation
    Write-Host "Wrote $OutCsv"
}
