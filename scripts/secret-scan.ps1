<#
.SYNOPSIS
  Lightweight, repeatable secret scan of tracked/working-tree text files (regex based).
.DESCRIPTION
  Local approximation of a dedicated scanner. CI additionally runs gitleaks over the full git history.
  Exclusions and exact synthetic test-fixture fingerprints are kept explicit below. Exits non-zero on
  matches or any candidate file that could not be read. Matched values are never printed.
#>
param(
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$excludedDirectories = @("target", ".git", "agent-workspace", "surefire-reports", "node_modules")
$excludedExtensions = @(".class", ".jar", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".zip", ".pdf")
$excludedDirectoryNames = ($excludedDirectories | ForEach-Object { [regex]::Escape($_) }) -join "|"
$excludedDirectoryPattern = "[\\/](?:$excludedDirectoryNames)[\\/]"

$patterns = [ordered]@{
    "AWS access key id"      = 'AKIA[0-9A-Z]{16}'
    "GitHub token"           = 'gh[pousr]_[A-Za-z0-9]{36,}'
    "Private key block"      = '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
    "Slack token"            = 'xox[baprs]-[A-Za-z0-9-]{10,}'
    "Generic credential"     = '(?i)(password|passwd|secret|api[_-]?key|token)\s*[:=]\s*["'']?[A-Za-z0-9/+_\-]{16,}["'']?'
    "JWT"                    = 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
}

$allowedFixtureHashes = @{
    "src\test\java\com\schwab\agentic\guardrails\PolicyEngineTest.java:23|Generic credential" = "c33f51b5cc404bd55ecb1931baae023ff73ecc7c5fbb3b59486a83fe2d9a25d3"
    "src\test\java\com\schwab\agentic\security\ProductionAuthorizationBoundaryTest.java:25|Generic credential" = "912a5e64c2dd016ef5e5682f7d3c100431b6250fd1440f412ae5800f95dc21cb"
    "src\test\java\com\schwab\agentic\security\ProductionFailClosedTest.java:23|Generic credential" = "8c90ddc8409a99773a17f3c0b33d1a2335f2c7190f8a5cddae4ee44d410d69c4"
    "src\test\java\com\schwab\agentic\security\ProductionSecurityIntegrationTest.java:27|Generic credential" = "912a5e64c2dd016ef5e5682f7d3c100431b6250fd1440f412ae5800f95dc21cb"
}

function Get-LineHash {
    param([string]$Line)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Line)
        return ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

try {
    $files = @(Get-ChildItem -Path $root -Recurse -File -Force -ErrorAction Stop | Where-Object {
        $_.FullName -notmatch $excludedDirectoryPattern -and
        $_.Extension -notin $excludedExtensions
    })
} catch {
    Write-Output "Secret scan could not enumerate candidate files (error type: $($_.Exception.GetType().Name))"
    exit 2
}

$hits = New-Object System.Collections.Generic.List[string]
$allowedHits = New-Object System.Collections.Generic.List[string]
$readErrors = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($root.Length + 1).Replace("/", "\")
    $lineNumber = 0
    try {
        foreach ($line in [IO.File]::ReadLines($file.FullName)) {
            $lineNumber++
            foreach ($name in $patterns.Keys) {
                if ($line -match $patterns[$name]) {
                    $entry = "${relativePath}:${lineNumber} [$name] (value redacted)"
                    $fixtureKey = "${relativePath}:${lineNumber}|$name"
                    if ($allowedFixtureHashes.ContainsKey($fixtureKey) -and
                        (Get-LineHash $line) -ceq $allowedFixtureHashes[$fixtureKey]) {
                        $allowedHits.Add("ALLOWED synthetic fixture $entry")
                    } else {
                        $hits.Add($entry)
                    }
                }
            }
        }
    } catch {
        $readErrors.Add("UNREADABLE ${relativePath} (error type: $($_.Exception.GetType().Name))")
    }
}

$summary = @(
    "Secret scan date=$((Get-Date).ToString('s')) files=$($files.Count) unreadable=$($readErrors.Count) unallowed=$($hits.Count) allowed-fixtures=$($allowedHits.Count)"
) + $allowedHits + $hits + $readErrors
$summary | ForEach-Object { Write-Output $_ }
if ($OutFile) {
    $summary | Set-Content -Path $OutFile -Encoding UTF8
}
if ($readErrors.Count -gt 0) {
    exit 2
}
if ($hits.Count -gt 0) {
    exit 1
}
