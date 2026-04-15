param(
  [Parameter(Mandatory = $true)][string]$EvalBase,
  [Parameter(Mandatory = $true)][string]$BaseRunId,
  [Parameter(Mandatory = $true)][string]$CandRunId,
  [int]$Limit = 500,
  [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"

function Get-RunReport([string]$runId) {
  # 先取 JSON 字符串再 ConvertFrom-Json，避免 Invoke-RestMethod 在某些环境下内存膨胀
  $raw = Invoke-WebRequest -Uri "$EvalBase/api/v1/eval/runs/$runId/report?error_code_top_n=20" -Method Get -UseBasicParsing
  return ($raw.Content | ConvertFrom-Json)
}

function Get-RunResultsAll([string]$runId) {
  $offset = 0
  $all = @()
  while ($true) {
    $raw = Invoke-WebRequest -Uri "$EvalBase/api/v1/eval/runs/$runId/results?offset=$offset&limit=$Limit" -Method Get -UseBasicParsing
    $resp = $raw.Content | ConvertFrom-Json
    if (-not $resp -or -not $resp.results) { break }
    $batch = @($resp.results)
    $all += $batch
    if ($batch.Count -lt $Limit) { break }
    $offset += $Limit
  }
  return $all
}

function Index-ByCaseId($results) {
  $m = @{}
  foreach ($r in $results) {
    if ($null -eq $r) { continue }
    $cid = $r.case_id
    if ([string]::IsNullOrWhiteSpace($cid)) { continue }
    $m[$cid] = $r
  }
  return $m
}

function Count-By($results, [string]$field) {
  return $results |
    Group-Object -Property $field |
    Sort-Object Count -Descending |
    ForEach-Object { [PSCustomObject]@{ name = $_.Name; count = $_.Count } }
}

$baseReport = Get-RunReport $BaseRunId
$candReport = Get-RunReport $CandRunId

$baseResults = Get-RunResultsAll $BaseRunId
$candResults = Get-RunResultsAll $CandRunId

$baseById = Index-ByCaseId $baseResults
$candById = Index-ByCaseId $candResults

$allCaseIds = @($baseById.Keys + $candById.Keys) | Sort-Object -Unique

$regressions = @()
$improvements = @()
$changed = @()

foreach ($cid in $allCaseIds) {
  $b = $baseById[$cid]
  $c = $candById[$cid]
  $bv = if ($b) { $b.verdict } else { "MISSING" }
  $cv = if ($c) { $c.verdict } else { "MISSING" }
  if ($bv -ne $cv) {
    $row = [PSCustomObject]@{
      case_id = $cid
      base_verdict = $bv
      cand_verdict = $cv
      base_error_code = if ($b) { $b.error_code } else { $null }
      cand_error_code = if ($c) { $c.error_code } else { $null }
      base_latency_ms = if ($b) { $b.latency_ms } else { $null }
      cand_latency_ms = if ($c) { $c.latency_ms } else { $null }
    }
    $changed += $row
    if ($bv -eq "PASS" -and ($cv -ne "PASS")) { $regressions += $row }
    if (($bv -ne "PASS") -and $cv -eq "PASS") { $improvements += $row }
  }
}

$baseVerdictCounts = Count-By $baseResults "verdict"
$candVerdictCounts = Count-By $candResults "verdict"
$baseErrorCounts = Count-By $baseResults "error_code"
$candErrorCounts = Count-By $candResults "error_code"

$compare = [ordered]@{
  compare_version = "run.compare.client.v1"
  eval_base = $EvalBase
  base_run_id = $BaseRunId
  cand_run_id = $CandRunId
  base_report = $baseReport
  cand_report = $candReport
  base_verdict_counts = $baseVerdictCounts
  cand_verdict_counts = $candVerdictCounts
  base_error_code_counts = $baseErrorCounts
  cand_error_code_counts = $candErrorCounts
  regressions = $regressions
  improvements = $improvements
  changed = $changed
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$jsonPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.json"
$mdPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.md"

($compare | ConvertTo-Json -Depth 50) | Set-Content -Path $jsonPath -Encoding utf8

$md = @()
$md += ("# run.compare (client) - {0} vs {1}" -f $BaseRunId, $CandRunId)
$md += ""
$md += "## Summary"
$md += "- base pass_rate: $($baseReport.pass_rate)  p95_latency_ms: $($baseReport.p95_latency_ms)"
$md += "- cand pass_rate: $($candReport.pass_rate)  p95_latency_ms: $($candReport.p95_latency_ms)"
$md += "- regressions: $($regressions.Count)"
$md += "- improvements: $($improvements.Count)"
$md += "- changed verdicts: $($changed.Count)"
$md += ""
$md += "## Regressions (PASS -> non-PASS)"
if ($regressions.Count -eq 0) {
  $md += "- (none)"
} else {
  foreach ($r in $regressions) {
    $md += "- $($r.case_id): $($r.base_verdict) -> $($r.cand_verdict) (base=$($r.base_error_code) cand=$($r.cand_error_code))"
  }
}
$md += ""
$md += "## Improvements (non-PASS -> PASS)"
if ($improvements.Count -eq 0) {
  $md += "- (none)"
} else {
  foreach ($r in $improvements) {
    $md += "- $($r.case_id): $($r.base_verdict) -> $($r.cand_verdict) (base=$($r.base_error_code) cand=$($r.cand_error_code))"
  }
}

$md -join "`n" | Set-Content -Path $mdPath -Encoding utf8

Write-Host "Wrote: $jsonPath"
Write-Host "Wrote: $mdPath"

