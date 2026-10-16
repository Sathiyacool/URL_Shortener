<#
.SYNOPSIS
  Dependency vulnerability scan using the public OSV database (https://osv.dev).
.DESCRIPTION
  Resolves the runtime Maven dependency tree, queries OSV for each exact group:artifact@version, prints every
  advisory found and exits non-zero when any exist. Nothing is suppressed; triage belongs in the docs.
  Requires: Maven on PATH and outbound HTTPS to api.osv.dev.
.EXAMPLE
  powershell -File scripts\osv-scan.ps1 -OutFile evidence\dependency-scan-osv.txt
#>
param(
    [string]$OutFile = "",
    [string]$Scope = "runtime",
    # Advisories explicitly triaged in docs/SECURITY_SCANNING.md. They are still printed, labelled ACCEPTED.
    [string[]]$AcceptedIds = @()
)
$ErrorActionPreference = "Stop"
$AcceptedIds = @($AcceptedIds | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$deps = Join-Path ([System.IO.Path]::GetTempPath()) "osv-deps.txt"
& mvn -B -q dependency:list "-DincludeScope=$Scope" "-DoutputFile=$deps" "-DappendOutput=false" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "mvn dependency:list failed" }

$coords = Get-Content $deps | ForEach-Object {
    if ($_ -match '^\s+([^:\s]+):([^:\s]+):jar:([^:\s]+):') {
        [pscustomobject]@{ Name = "$($Matches[1]):$($Matches[2])"; Version = $Matches[3] }
    }
} | Sort-Object Name, Version -Unique

$queries = @($coords | ForEach-Object {
    @{ package = @{ ecosystem = "Maven"; name = $_.Name }; version = $_.Version }
})
$body = @{ queries = $queries } | ConvertTo-Json -Depth 6
$result = Invoke-RestMethod -Method Post -Uri "https://api.osv.dev/v1/querybatch" -ContentType "application/json" -Body $body

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("OSV dependency scan  date=$((Get-Date).ToString('s'))  scope=$Scope  artifacts=$($coords.Count)")
$findings = 0
for ($i = 0; $i -lt $coords.Count; $i++) {
    $vulns = $result.results[$i].vulns
    if ($vulns) {
        foreach ($v in $vulns) {
            $detail = Invoke-RestMethod -Uri "https://api.osv.dev/v1/vulns/$($v.id)"
            $sev = if ($detail.database_specific.severity) { $detail.database_specific.severity } else { "UNKNOWN" }
            $accepted = $AcceptedIds -contains $v.id
            $label = if ($accepted) { "ACCEPTED" } else { "FINDING" }
            $lines.Add("$label $($coords[$i].Name):$($coords[$i].Version) $($v.id) severity=$sev :: $($detail.summary)")
            if (-not $accepted) { $findings++ }
        }
    }
}
$lines.Add("UNACCEPTED FINDINGS: $findings")
$lines | ForEach-Object { Write-Output $_ }
if ($OutFile) { $lines | Set-Content -Path $OutFile -Encoding UTF8 }
if ($findings -gt 0) { exit 1 }
