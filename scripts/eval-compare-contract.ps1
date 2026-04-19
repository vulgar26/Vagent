# Dot-source only: helpers for compare-eval-runs.ps1 / compare-eval-results-files.ps1
# 契约类归因（与 plans/vagent-upgrade.md「必须可归因的错误码」对齐，用于 compare 门禁）

function Get-EvalContractErrorCodes {
    return [string[]]@(
        'CONTRACT_VIOLATION',
        'PARSE_ERROR',
        'SOURCE_NOT_IN_HITS',
        'SECURITY_BOUNDARY_VIOLATION'
    )
}

function Test-EvalContractErrorCode {
    param([string]$ErrorCode)
    if ([string]::IsNullOrWhiteSpace($ErrorCode)) { return $false }
    $ec = $ErrorCode.Trim()
    return (Get-EvalContractErrorCodes) -contains $ec
}

function Get-EvalRegressionContractRows {
    param([object[]]$Regressions)
    $out = @()
    foreach ($r in $Regressions) {
        $cec = if ($r) { [string]$r.cand_error_code } else { '' }
        if (Test-EvalContractErrorCode $cec) {
            $out += $r
        }
    }
    return $out
}

function Get-EvalRunObject {
    param(
        [Parameter(Mandatory)][string]$EvalBase,
        [Parameter(Mandatory)][string]$RunId
    )
    $base = $EvalBase.TrimEnd('/')
    $u = "$base/api/v1/eval/runs/$RunId"
    $raw = Invoke-WebRequest -Uri $u -Method Get -UseBasicParsing
    return ($raw.Content | ConvertFrom-Json)
}

function Read-EvalDatasetIdFromRun {
    param([Parameter(Mandatory)]$RunObj)
    if ($null -eq $RunObj) { return $null }
    $names = @($RunObj.PSObject.Properties.Name)
    if ($names -contains 'dataset_id') {
        $v = $RunObj.dataset_id
        if ($null -ne $v -and "$v".Trim().Length -gt 0) { return "$v".Trim() }
    }
    if ($names -contains 'run' -and $null -ne $RunObj.run) {
        $inner = @($RunObj.run.PSObject.Properties.Name)
        if ($inner -contains 'dataset_id') {
            $v2 = $RunObj.run.dataset_id
            if ($null -ne $v2 -and "$v2".Trim().Length -gt 0) { return "$v2".Trim() }
        }
    }
    return $null
}

function Assert-EvalSameDatasetForCompare {
    param(
        [Parameter(Mandatory)][string]$EvalBase,
        [Parameter(Mandatory)][string]$BaseRunId,
        [Parameter(Mandatory)][string]$CandRunId,
        [switch]$RequireSameDataset
    )
    try {
        $br = Get-EvalRunObject -EvalBase $EvalBase -RunId $BaseRunId
        $cr = Get-EvalRunObject -EvalBase $EvalBase -RunId $CandRunId
        $bd = Read-EvalDatasetIdFromRun -RunObj $br
        $cd = Read-EvalDatasetIdFromRun -RunObj $cr
        if ($RequireSameDataset -and ((-not $bd) -or (-not $cd))) {
            throw "RequireSameDataset: could not read dataset_id from GET .../eval/runs/{id} (base='$bd' cand='$cd'). Check eval API JSON shape."
        }
        if ($bd -and $cd -and ($bd -ne $cd)) {
            throw "Compare aborted: dataset_id differs (base_run=$BaseRunId dataset=$bd) vs (cand_run=$CandRunId dataset=$cd). Use the same frozen dataset."
        }
        if ((-not $bd) -or (-not $cd)) {
            Write-Warning "Compare: dataset_id not verified (missing field on one or both runs); ensure base/cand are the same dataset manually."
        }
    }
    catch {
        if ($_.Exception.Message -match '^Compare aborted:' -or $_.Exception.Message -match '^RequireSameDataset:') {
            throw
        }
        if ($RequireSameDataset) {
            throw "RequireSameDataset: failed to load runs for dataset check: $($_.Exception.Message)"
        }
        Write-Warning "Compare: dataset_id check skipped: $($_.Exception.Message)"
    }
}
