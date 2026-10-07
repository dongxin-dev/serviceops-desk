#requires -Version 5.1
<#
.SYNOPSIS
    Build the ServiceOps Desk backend and run its automated tests
    using the project's Maven Wrapper.

.DESCRIPTION
    Executes `mvnw.cmd clean verify` inside the `backend/` module.
    This lifecycle phase covers compile, packaging, unit tests and
    integration verification (Spring Boot repackage, etc.).

    Never uses a globally installed Maven - always the checked-in
    Wrapper so CI and local runs produce the same result.

    Exit codes:
      0  Maven build + tests passed
      1  Maven build or tests failed
      2  Could not locate backend / mvnw.cmd
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- locate repo root -------------------------------------------------------
try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
} catch {
    Write-Host "[verify-backend] git is not available on PATH." -ForegroundColor Red
    exit 2
}
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Host "[verify-backend] Not inside a git repository." -ForegroundColor Red
    exit 2
}

$backendDir = Join-Path $repoRoot 'backend'
if (-not (Test-Path -LiteralPath $backendDir -PathType Container)) {
    Write-Host "[verify-backend] Backend directory not found: $backendDir" -ForegroundColor Red
    exit 2
}

# Resolve wrapper for the current OS.
# Prefer mvnw.cmd when it exists (Windows); otherwise use ./mvnw.
$cmdWrapper = Join-Path $backendDir 'mvnw.cmd'
$shWrapper  = Join-Path $backendDir 'mvnw'
if (Test-Path -LiteralPath $cmdWrapper -PathType Leaf) {
    $isWindows = $true
    $wrapper   = $cmdWrapper
} elseif (Test-Path -LiteralPath $shWrapper -PathType Leaf) {
    $isWindows = $false
    $wrapper   = $shWrapper
} else {
    Write-Host "[verify-backend] Maven Wrapper (mvnw / mvnw.cmd) not found under $backendDir" -ForegroundColor Red
    exit 2
}

Push-Location -Path $backendDir
try {
    Write-Host "[verify-backend] Working directory: $backendDir" -ForegroundColor Cyan
    Write-Host "[verify-backend] Running: `"$wrapper`" clean verify" -ForegroundColor Cyan

    if ($isWindows) {
        & cmd.exe /d /c "`"$wrapper`"" clean verify
    } else {
        & "bash" "./mvnw" clean verify
    }
    $mvnExit = $LASTEXITCODE

    if ($mvnExit -ne 0) {
        Write-Host ("[verify-backend] FAIL - Maven lifecycle exited with code {0}." -f $mvnExit) -ForegroundColor Red
        exit 1
    }

    Write-Host "[verify-backend] OK - backend compiled and tests passed." -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
