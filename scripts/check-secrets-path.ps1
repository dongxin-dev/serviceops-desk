#requires -Version 5.1
<#
.SYNOPSIS
    Guard against Secret / local-config files being tracked, staged, or
    added to the ServiceOps Desk git repository.

.DESCRIPTION
    Enumerates every file path that could plausibly end up in a commit
    and fails if any of them matches a forbidden pattern.

    Sources checked:
      - HEAD-tracked files           (git ls-files)
      - Staged / index additions     (git diff --cached --name-only)
      - Non-ignored untracked files  (git ls-files --others --exclude-standard)

    Forbidden:
      .env
      .env.*                (except .env.example)
      **/application-local.yml
      **/application-local.yaml
      **/application-local.properties
      *.pem
      *.key
      *.p12
      *.pfx
      settings.xml          (repo copy of the user's Maven settings)
      settings-*.xml        (repo Maven settings, except the sanctioned
                             **/.mvn/settings-public.xml template)

    Exit codes:
      0  all clear
      1  one or more forbidden paths detected
      2  script could not run (git missing, not a repo, etc.)
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- locate repo root -------------------------------------------------------
try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
} catch {
    Write-Host "[check-secrets-path] ERROR: git is not available on PATH." -ForegroundColor Red
    exit 2
}
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Host "[check-secrets-path] ERROR: not inside a git repository." -ForegroundColor Red
    exit 2
}
Push-Location -Path $repoRoot
try {
    # --- gather candidate paths --------------------------------------------
    $tracked = @()
    $staged  = @()
    $others  = @()

    $trackedRaw = git ls-files 2>$null
    if ($LASTEXITCODE -eq 0 -and $trackedRaw) { $tracked = @($trackedRaw) }

    $stagedRaw = git diff --cached --name-only --diff-filter=ACMR 2>$null
    if ($LASTEXITCODE -eq 0 -and $stagedRaw) { $staged = @($stagedRaw) }

    $othersRaw = git ls-files --others --exclude-standard 2>$null
    if ($LASTEXITCODE -eq 0 -and $othersRaw) { $others = @($othersRaw) }

    # Normalise to forward-slash, dedupe, tag source for reporting.
    $seen = @{}
    $candidates = @()
    foreach ($entry in @(
        @{ Tag = 'tracked'; List = $tracked },
        @{ Tag = 'staged';  List = $staged  },
        @{ Tag = 'untracked-not-ignored'; List = $others }
    )) {
        foreach ($p in $entry.List) {
            if ([string]::IsNullOrWhiteSpace($p)) { continue }
            $norm = ($p -replace '\\','/')
            $key = "$($entry.Tag)|$norm"
            if ($seen.ContainsKey($key)) { continue }
            $seen[$key] = $true
            $candidates += [pscustomobject]@{ Source = $entry.Tag; Path = $norm }
        }
    }

    # --- predicates --------------------------------------------------------
    function Test-IsAllowedException([string]$p) {
        # .env.example is the ONLY .env.* that is allowed.
        if ($p -ieq '.env.example') { return $true }
        # The templated public Maven settings under **/.mvn/ is allowed.
        if ($p -match '(^|/)\.mvn/settings-public\.xml$') { return $true }
        return $false
    }

    function Get-ForbiddenReason([string]$p) {
        if (Test-IsAllowedException $p) { return $null }

        $name = Split-Path -Leaf ($p -replace '/','\')

        if ($p -ieq '.env')                              { return 'Dotenv file (.env)' }
        if ($name -like '.env.*')                        { return "Dotenv variant ($name)" }
        if ($name -ieq 'application-local.yml')          { return 'Local Spring config (application-local.yml)' }
        if ($name -ieq 'application-local.yaml')         { return 'Local Spring config (application-local.yaml)' }
        if ($name -ieq 'application-local.properties')   { return 'Local Spring config (application-local.properties)' }
        if ($name -like '*.pem')                         { return 'PEM certificate / private key' }
        if ($name -like '*.key')                         { return 'Private key file' }
        if ($name -like '*.p12')                         { return 'PKCS#12 keystore' }
        if ($name -like '*.pfx')                         { return 'PKCS#12 container (.pfx)' }
        if ($name -ieq 'settings.xml')                   { return 'Maven settings.xml (may contain credentials)' }
        if ($name -match '^settings-.*\.xml$')           { return "Maven settings variant ($name)" }
        return $null
    }

    # --- evaluate ----------------------------------------------------------
    $violations = @()
    foreach ($c in $candidates) {
        $reason = Get-ForbiddenReason $c.Path
        if ($reason) {
            $violations += [pscustomobject]@{
                Source = $c.Source
                Path   = $c.Path
                Reason = $reason
            }
        }
    }

    if ($violations.Count -eq 0) {
        Write-Host ("[check-secrets-path] OK - scanned {0} path(s); no forbidden secret / local-config files." -f $candidates.Count) -ForegroundColor Green
        exit 0
    }

    Write-Host "[check-secrets-path] FAIL - forbidden secret / local-config path(s) detected:" -ForegroundColor Red
    foreach ($v in $violations) {
        Write-Host ("  - [{0}] {1}  ({2})" -f $v.Source, $v.Path, $v.Reason) -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Remove the file(s) from the index / working tree, or add them to .gitignore." -ForegroundColor Yellow
    Write-Host "Allowed exception: .env.example (placeholders only) and backend/.mvn/settings-public.xml." -ForegroundColor Yellow
    exit 1
}
finally {
    Pop-Location
}
