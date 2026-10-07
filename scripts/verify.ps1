#requires -Version 5.1
<#
.SYNOPSIS
    ServiceOps Desk unified quality gate.

.DESCRIPTION
    Runs, in order:
      1. scripts/check-secrets-path.ps1          (Secrets Guard)
      2. scripts/check-migration-immutable.ps1   (Migration Guard)
      3. scripts/verify-backend.ps1              (Backend Build + Tests)

    Any failing step aborts the pipeline. On success prints
    "ServiceOps Desk QUALITY GATE: PASS"; on failure prints
    "ServiceOps Desk QUALITY GATE: FAIL".

    Exit codes:
      0  all gates passed
      1  at least one gate failed
      2  harness could not run (missing script, wrong cwd, etc.)

    Can be invoked from anywhere in the repo; scripts resolve the
    repository root themselves.
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- locate repo root / scripts folder --------------------------------------
try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
} catch {
    Write-Host "[verify] git is not available on PATH." -ForegroundColor Red
    Write-Host "ServiceOps Desk QUALITY GATE: FAIL" -ForegroundColor Red
    exit 2
}
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Host "[verify] Not inside a git repository." -ForegroundColor Red
    Write-Host "ServiceOps Desk QUALITY GATE: FAIL" -ForegroundColor Red
    exit 2
}

$scriptsDir = Join-Path $repoRoot 'scripts'
if (-not (Test-Path -LiteralPath $scriptsDir -PathType Container)) {
    Write-Host "[verify] scripts/ directory not found at $scriptsDir" -ForegroundColor Red
    Write-Host "ServiceOps Desk QUALITY GATE: FAIL" -ForegroundColor Red
    exit 2
}

$steps = @(
    @{ Name = 'Secrets Guard';        Script = 'check-secrets-path.ps1'        },
    @{ Name = 'Migration Guard';      Script = 'check-migration-immutable.ps1' },
    @{ Name = 'Backend Build & Test'; Script = 'verify-backend.ps1'            }
)

# Resolve a PowerShell host that can execute child scripts in their
# own process, so an `exit N` inside a step does NOT kill this wrapper.
# Prefer pwsh (PS 6+) then powershell (5.1).
$psExe = $null
foreach ($candidate in @('pwsh.exe','pwsh','powershell.exe','powershell')) {
    $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source -and (Test-Path -LiteralPath $cmd.Source)) {
        $psExe = $cmd.Source
        break
    }
}
if ([string]::IsNullOrWhiteSpace($psExe)) {
    Write-Host "[verify] No PowerShell host available to run step scripts." -ForegroundColor Red
    Write-Host "ServiceOps Desk QUALITY GATE: FAIL" -ForegroundColor Red
    exit 2
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Path
    )
    Write-Host ""
    Write-Host ("=== {0} ===" -f $Name) -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-Host ("[verify] Missing step script: {0}" -f $Path) -ForegroundColor Red
        return 2
    }
    # Run in a child PowerShell process to isolate `exit` semantics.
    # Start-Process with -NoNewWindow lets the child inherit our console,
    # so its colored output prints directly. -Wait + -PassThru gives us
    # a real Process object with a trustworthy ExitCode, independent of
    # the PowerShell pipeline (which would mangle native stderr into
    # ErrorRecords under $ErrorActionPreference='Stop').
    $argList = '-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $Path
    $proc = Start-Process -FilePath $psExe -ArgumentList $argList -NoNewWindow -Wait -PassThru
    return $proc.ExitCode
}

Push-Location -Path $repoRoot
try {
    $overall = 0
    foreach ($s in $steps) {
        $path = Join-Path $scriptsDir $s.Script
        $code = Invoke-Step -Name $s.Name -Path $path
        if ($code -ne 0) {
            Write-Host ("[verify] Step '{0}' failed with exit code {1}." -f $s.Name, $code) -ForegroundColor Red
            $overall = 1
            break
        }
    }

    Write-Host ""
    if ($overall -eq 0) {
        Write-Host "ServiceOps Desk QUALITY GATE: PASS" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "ServiceOps Desk QUALITY GATE: FAIL" -ForegroundColor Red
        exit 1
    }
}
finally {
    Pop-Location
}
