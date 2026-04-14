#Requires -Version 5.1
# Ingest P0 eval gold KB as user eval_kb_vagent (UTF-8 body in sibling .txt for Windows PS 5.1 encoding safety).
param(
    [string] $BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$username = "eval_kb_vagent"
$password = "VagentEvalKb2026!a7"

$loginBody = @{ username = $username; password = $password } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
    -ContentType "application/json; charset=utf-8" -Body $loginBody
if (-not $auth.token) { throw "Login failed: no token in response." }
$token = $auth.token

$headers = @{ Authorization = "Bearer $token" }

$bodyPath = Join-Path $PSScriptRoot "ingest-p0-eval-gold-kb-body.txt"
if (-not (Test-Path -LiteralPath $bodyPath)) { throw "Missing body file: $bodyPath" }
$content = [System.IO.File]::ReadAllText($bodyPath, [System.Text.UTF8Encoding]::new($false))

$title = "P0 Eval Gold (eval tenant KB)"
$docBody = @{ title = $title; content = $content } | ConvertTo-Json -Depth 5
$ingest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/kb/documents" -Method Post `
    -Headers $headers -ContentType "application/json; charset=utf-8" -Body $docBody

Write-Host "Ingest OK: documentId=$($ingest.documentId) chunkCount=$($ingest.chunkCount)"
