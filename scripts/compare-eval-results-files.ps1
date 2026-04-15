param(
  [Parameter(Mandatory = $true)][string]$BaseResultsJson,
  [Parameter(Mandatory = $true)][string]$CandResultsJson,
  [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"

function Read-ResultsFromFile([string]$path) {
  if (-not (Test-Path $path)) { throw "results json not found: $path" }
  $raw = Get-Content -Path $path -Raw -Encoding utf8
  $obj = $raw | ConvertFrom-Json
  if ($obj -and $obj.results) { return @($obj.results) }
  if ($obj -is [System.Array]) { return @($obj) }
  throw "unrecognized results json shape: $path"
}

function Guess-RunIdFromFile([string]$path) {
  $name = [System.IO.Path]::GetFileNameWithoutExtension($path)
  # 常见形态：eval_run_run_<hex>_results 或 eval_run_<runId>_results
  if ($name -match "eval_run_(run_[a-z0-9]+)_results$") { return $Matches[1] }
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

$BaseRunId = Guess-RunIdFromFile $BaseResultsJson
$CandRunId = Guess-RunIdFromFile $CandResultsJson

$baseResults = Read-ResultsFromFile $BaseResultsJson
$candResults = Read-ResultsFromFile $CandResultsJson

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

$compare = [ordered]@{
  compare_version = "run.compare.files.v1"
  base_run_id = $BaseRunId
  cand_run_id = $CandRunId
  base_verdict_counts = Count-By $baseResults "verdict"
  cand_verdict_counts = Count-By $candResults "verdict"
  base_error_code_counts = Count-By $baseResults "error_code"
  cand_error_code_counts = Count-By $candResults "error_code"
  regressions = $regressions
  improvements = $improvements
  changed = $changed
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$jsonPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.json"
$mdPath = Join-Path $OutDir "eval_compare_${BaseRunId}_vs_${CandRunId}.md"

($compare | ConvertTo-Json -Depth 50) | Set-Content -Path $jsonPath -Encoding utf8

$md = @()
$md += ("# run.compare (files) - {0} vs {1}" -f $BaseRunId, $CandRunId)
$md += ""
$md += "## Summary"
$md += "- regressions: $($regressions.Count)"
$md += "- improvements: $($improvements.Count)"
$md += "- changed verdicts: $($changed.Count)"
$md += ""
$md += "## Regressions (PASS -> non-PASS)"
if ($regressions.Count -eq 0) { $md += "- (none)" } else {
  foreach ($r in $regressions) {
    $md += "- $($r.case_id): $($r.base_verdict) -> $($r.cand_verdict) (base=$($r.base_error_code) cand=$($r.cand_error_code))"
  }
}
$md += ""
$md += "## Improvements (non-PASS -> PASS)"
if ($improvements.Count -eq 0) { $md += "- (none)" } else {
  foreach ($r in $improvements) {
    $md += "- $($r.case_id): $($r.base_verdict) -> $($r.cand_verdict) (base=$($r.base_error_code) cand=$($r.cand_error_code))"
  }
}

$md -join "`n" | Set-Content -Path $mdPath -Encoding utf8

Write-Host "Wrote: $jsonPath"
Write-Host "Wrote: $mdPath"

