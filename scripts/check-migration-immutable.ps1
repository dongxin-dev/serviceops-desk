#requires -Version 5.1
<#
.SYNOPSIS
    Enforce immutability of already-committed Flyway migrations.

.DESCRIPTION
    Compares the current working tree (which includes committed,
    staged, and unstaged changes) against the base branch and fails
    if any file under the Flyway migration directory that already
    exists in the base has been modified, deleted, or renamed.

    Adding NEW migration files is always allowed.

    Base ref resolution order:
      1. origin/main (if it exists)
      2. main        (if it exists)
      3. If neither resolves, the guard cannot perform its job and
         exits with code 2 (fail closed). The quality gate must NOT
         treat an unresolvable baseline as a PASS.

    Exit codes:
      0  guard passed: no existing migration was touched (new
         migrations are allowed and count as a pass)
      1  immutable migration violation (modified / deleted / renamed)
      2  guard itself could not reliably execute (git missing, not a
         repo, no baseline ref, git diff failure, etc.)
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- locate repo root -------------------------------------------------------
try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
} catch {
    Write-Host "[check-migration-immutable] ERROR: git is not available on PATH." -ForegroundColor Red
    exit 2
}
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Host "[check-migration-immutable] ERROR: not inside a git repository." -ForegroundColor Red
    exit 2
}
Push-Location -Path $repoRoot
try {
    $migrationDir = 'backend/src/main/resources/db/migration'

    # --- resolve base ref (fail closed if none) -----------------------------
    function Test-Ref([string]$ref) {
        git rev-parse --verify --quiet $ref 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    }

    $baseRef = $null
    foreach ($candidate in @('origin/main', 'main')) {
        if (Test-Ref $candidate) { $baseRef = $candidate; break }
    }

    if (-not $baseRef) {
        Write-Host "[check-migration-immutable] ERROR: no baseline ref found (tried 'origin/main', 'main'). Migration immutability cannot be enforced without a baseline - failing closed." -ForegroundColor Red
        Write-Host "[check-migration-immutable] Fetch the base branch (e.g. 'git fetch origin main') or create a local 'main' before running the quality gate." -ForegroundColor Yellow
        exit 2
    }

    # --- collect changed paths in migration dir vs working tree -------------
    # `git diff --name-status <base>` compares base to the working tree,
    # so committed + staged + unstaged changes are all covered.
    $diffRaw = git diff --name-status $baseRef -- $migrationDir 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[check-migration-immutable] ERROR: git diff against $baseRef failed (exit $LASTEXITCODE)." -ForegroundColor Red
        exit 2
    }

    $issues = @()
    if ($diffRaw) {
        foreach ($line in @($diffRaw)) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            # Format: "<STATUS>\t<path>" (renames append a second tab field)
            $parts  = $line -split "`t"
            $status = $parts[0].Trim()
            $path   = $parts[1].Trim() -replace '\\','/'
            $code   = $status.Substring(0,1)

            switch ($code) {
                'A' {
                    # New migration - allowed.
                }
                'M' { $issues += [pscustomobject]@{ Kind = 'MODIFIED'; Path = $path } }
                'D' { $issues += [pscustomobject]@{ Kind = 'DELETED';  Path = $path } }
                'R' {
                    $old = $parts[1].Trim() -replace '\\','/'
                    $new = if ($parts.Count -ge 3) { $parts[2].Trim() -replace '\\','/' } else { '(?)' }
                    $issues += [pscustomobject]@{ Kind = 'RENAMED'; Path = "$old -> $new" }
                }
                'C' { $issues += [pscustomobject]@{ Kind = 'COPIED';       Path = $path } }
                'T' { $issues += [pscustomobject]@{ Kind = 'TYPE-CHANGED'; Path = $path } }
                'U' { $issues += [pscustomobject]@{ Kind = 'UNMERGED';     Path = $path } }
                default { $issues += [pscustomobject]@{ Kind = "OTHER($status)"; Path = $path } }
            }
        }
    }

    # Defensive second pass: any file present in $baseRef must still exist
    # on disk (catches index-only deletions that the working-tree diff
    # might not surface).
    $baseFilesRaw = git ls-tree -r --name-only $baseRef -- $migrationDir 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[check-migration-immutable] ERROR: git ls-tree against $baseRef failed." -ForegroundColor Red
        exit 2
    }
    if ($baseFilesRaw) {
        foreach ($bf in @($baseFilesRaw)) {
            $norm = ($bf -replace '\\','/')
            $disk = Join-Path $repoRoot ($norm -replace '/','\')
            if (-not (Test-Path -LiteralPath $disk)) {
                if (-not ($issues | Where-Object { $_.Path -eq $norm -and $_.Kind -in @('DELETED','RENAMED') })) {
                    $issues += [pscustomobject]@{ Kind = 'MISSING-ON-DISK'; Path = $norm }
                }
            }
        }
    }

    if ($issues.Count -eq 0) {
        $newCount = 0
        if ($diffRaw) {
            foreach ($line in @($diffRaw)) {
                if ($line -match '^A\s') { $newCount++ }
            }
        }
        if ($newCount -gt 0) {
            Write-Host ("[check-migration-immutable] OK - {0} new migration(s) added against {1}; no existing migration touched." -f $newCount, $baseRef) -ForegroundColor Green
        } else {
            Write-Host ("[check-migration-immutable] OK - no migration changes against {0}." -f $baseRef) -ForegroundColor Green
        }
        exit 0
    }

    Write-Host ("[check-migration-immutable] FAIL - existing Flyway migration(s) under {0} are immutable but have been touched (base={1}):" -f $migrationDir, $baseRef) -ForegroundColor Red
    foreach ($i in $issues) {
        Write-Host ("  - {0}: {1}" -f $i.Kind, $i.Path) -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Restore the affected file(s) to their committed state and create a NEW migration (V{n}__description.sql) instead." -ForegroundColor Yellow
    exit 1
}
finally {
    Pop-Location
}
