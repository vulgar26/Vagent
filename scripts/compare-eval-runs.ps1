param(
  # online mode（从 eval 服务拉取）
  [string]$EvalBase,
  [string]$BaseRunId,
  [string]$CandRunId,
  # offline mode（直接读落盘的 results.json）
  [Parameter()][string]$BaseResultsPath,
  [Parameter()][string]$CandResultsPath,
  [int]$Limit = 500,
  [string]$OutDir = ".",
  # 在线模式：GET .../eval/runs/{id} 校验两端 dataset_id（-RequireSameDataset 时缺失字段直接失败）
  [switch]$RequireSameDataset,
  # 任一条「PASS -> 非 PASS」且 cand_error_code 为契约类时 exit 1（用于 hybrid/rerank A/B 门禁）
  [switch]$StrictContractGate,
  # 可选：Bearer token（与 ci-eval-remote.sh 一致）；未传时读环境变量 EVAL_HTTP_TOKEN
  [string]$EvalHttpToken
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\eval-compare-contract.ps1"

function Is-NonEmpty([string]$s) {
  return -not [string]::IsNullOrWhiteSpace($s)
}

# PowerShell 5.1 在少数环境下可能出现“部分参数名未绑定”的怪异现象；
# 兜底：从 $args 中抓取 results.json 路径（按出现顺序）。
if (-not (Is-NonEmpty $BaseResultsPath) -or -not (Is-NonEmpty $CandResultsPath)) {
  $fileArgs = @()
  foreach ($a in $args) {
    if ($a -is [string]) {
      $s = $a.ToString()
      if ($s -like "*results.json*") {
        $fileArgs += $s
      }
    }
  }
  if (-not (Is-NonEmpty $BaseResultsPath) -and $fileArgs.Count -ge 1) {
    $BaseResultsPath = $fileArgs[0]
  }
  if (-not (Is-NonEmpty $CandResultsPath) -and $fileArgs.Count -ge 2) {
    $CandResultsPath = $fileArgs[1]
  }
}

function Get-EvalInvokeHeaders {
  $h = @{}
  if (Is-NonEmpty $script:CompareEvalBearer) {
    $h['Authorization'] = "Bearer $($script:CompareEvalBearer.Trim())"
  }
  return $h
}

function Get-RunReport([string]$runId) {
  # 先取 JSON 字符串再 ConvertFrom-Json，避免 Invoke-RestMethod 在某些环境下内存膨胀
  $hdr = Get-EvalInvokeHeaders
  $params = @{ Uri = "$EvalBase/api/v1/eval/runs/$runId/report?error_code_top_n=20"; Method = 'Get'; UseBasicParsing = $true }
  if ($hdr.Count -gt 0) { $params['Headers'] = $hdr }
  $raw = Invoke-WebRequest @params
  return ($raw.Content | ConvertFrom-Json)
}

function Get-RunResultsAll([string]$runId) {
  $offset = 0
  $all = @()
  while ($true) {
    $hdr = Get-EvalInvokeHeaders
    $params = @{ Uri = "$EvalBase/api/v1/eval/runs/$runId/results?offset=$offset&limit=$Limit"; Method = 'Get'; UseBasicParsing = $true }
    if ($hdr.Count -gt 0) { $params['Headers'] = $hdr }
    $raw = Invoke-WebRequest @params
    $resp = $raw.Content | ConvertFrom-Json
    if (-not $resp -or -not $resp.results) { break }
    $batch = @($resp.results)
    $all += $batch
    if ($batch.Count -lt $Limit) { break }
    $offset += $Limit
  }
  return $all
}

function Read-ResultsFromFile([string]$path) {
  if (-not (Test-Path $path)) { throw "results json not found: $path" }
  $raw = Get-Content -Path $path -Raw -Encoding utf8
  $obj = $raw | ConvertFrom-Json
  if ($obj -and $obj.results) { return @($obj.results) }
  # 兼容“直接是数组”的形态（若未来落盘改格式）
  if ($obj -is [System.Array]) { return @($obj) }
  throw "unrecognized results json shape: $path"
}

function Guess-RunIdFromFile([string]$path) {
  $name = [System.IO.Path]::GetFileNameWithoutExtension($path)
  # 期望形态：eval_run_run_<runId>_results
  if ($name -match "eval_run_run_(run_[a-z0-9]+)_results$") { return $Matches[1] }
  if ($name -match "(run_[a-z0-9]+)") { return $Matches[1] }
  return $name
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

if ((Is-NonEmpty $BaseResultsPath) -and -not (Is-NonEmpty $CandResultsPath)) {
  throw ("offline mode requires both -BaseResultsJson and -CandResultsJson (CandResultsJson is empty). bound={0}" -f (($PSBoundParameters.Keys | Sort-Object) -join ","))
}
if (-not (Is-NonEmpty $BaseResultsPath) -and (Is-NonEmpty $CandResultsPath)) {
  throw ("offline mode requires both -BaseResultsJson and -CandResultsJson (BaseResultsJson is empty). bound={0}" -f (($PSBoundParameters.Keys | Sort-Object) -join ","))
}

# 兼容：若用户把 results 路径误填到了 BaseRunId/CandRunId，也可离线 compare
if (-not (Is-NonEmpty $BaseResultsPath) -and (Is-NonEmpty $BaseRunId) -and (Test-Path $BaseRunId)) {
  $BaseResultsPath = $BaseRunId
  $BaseRunId = $null
}
if (-not (Is-NonEmpty $CandResultsPath) -and (Is-NonEmpty $CandRunId) -and (Test-Path $CandRunId)) {
  $CandResultsPath = $CandRunId
  $CandRunId = $null
}

$offline = (Is-NonEmpty $BaseResultsPath) -and (Is-NonEmpty $CandResultsPath)

$script:CompareEvalBearer = $null
if (Is-NonEmpty $EvalHttpToken) {
  $script:CompareEvalBearer = $EvalHttpToken
}
elseif (Is-NonEmpty $env:EVAL_HTTP_TOKEN) {
  $script:CompareEvalBearer = $env:EVAL_HTTP_TOKEN
}

if ($offline) {
  if (-not $BaseRunId) { $BaseRunId = Guess-RunIdFromFile $BaseResultsPath }
  if (-not $CandRunId) { $CandRunId = Guess-RunIdFromFile $CandResultsPath }
  $baseReport = $null
  $candReport = $null
  $baseResults = Read-ResultsFromFile $BaseResultsPath
  $candResults = Read-ResultsFromFile $CandResultsPath
} else {
  if (-not $EvalBase) { throw "online mode requires -EvalBase" }
  if (-not $BaseRunId) { throw "online mode requires -BaseRunId" }
  if (-not $CandRunId) { throw "online mode requires -CandRunId" }
  $dsHeaders = Get-EvalInvokeHeaders
  Assert-EvalSameDatasetForCompare -EvalBase $EvalBase -BaseRunId $BaseRunId -CandRunId $CandRunId -RequireSameDataset:$RequireSameDataset -HttpHeaders $dsHeaders
  $baseReport = Get-RunReport $BaseRunId
  $candReport = Get-RunReport $CandRunId
  $baseResults = Get-RunResultsAll $BaseRunId
  $candResults = Get-RunResultsAll $CandRunId
}

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

$contractRegressions = @(Get-EvalRegressionContractRows -Regressions @($regressions))

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
  contract_regression_count = $contractRegressions.Count
  contract_regressions = $contractRegressions
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$jsonPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.json"
$mdPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.md"

($compare | ConvertTo-Json -Depth 50) | Set-Content -Path $jsonPath -Encoding utf8

$md = @()
$md += ("# run.compare (client) - {0} vs {1}" -f $BaseRunId, $CandRunId)
$md += ""
$md += "## Summary"
if ($baseReport -and $candReport) {
  $md += ("- base pass_rate: {0}  p95_latency_ms: {1}" -f $baseReport.pass_rate, $baseReport.p95_latency_ms)
  $md += ("- cand pass_rate: {0}  p95_latency_ms: {1}" -f $candReport.pass_rate, $candReport.p95_latency_ms)
} else {
  $md += "- base report: (offline mode: not fetched)"
  $md += "- cand report: (offline mode: not fetched)"
}
$md += "- regressions: $($regressions.Count)"
$md += "- improvements: $($improvements.Count)"
$md += "- changed verdicts: $($changed.Count)"
$md += "- contract regressions (subset): $($contractRegressions.Count)"
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
$md += "## Contract regressions (PASS -> non-PASS, cand_error_code in contract set)"
if ($contractRegressions.Count -eq 0) {
  $md += "- (none)"
}
else {
  foreach ($r in $contractRegressions) {
    $md += "- $($r.case_id): $($r.base_verdict) -> $($r.cand_verdict) (cand_error_code=$($r.cand_error_code))"
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

if ($StrictContractGate -and $contractRegressions.Count -gt 0) {
  Write-Host ("StrictContractGate FAIL: {0} regression(s) with contract-class cand_error_code" -f $contractRegressions.Count) -ForegroundColor Red
  exit 1
}
