<#
.SYNOPSIS
    Build the current branch via the existing GitHub Actions CI workflow, then
    download the resulting debug APK and install it on a connected device -
    without pushing to main or creating a release tag.

.DESCRIPTION
    1. Pushes the current branch (if it has unpushed commits).
    2. Waits for the GitHub Actions run for the current commit to finish.
    3. Downloads the "sharesonic-debug" artifact.
    4. Installs the APK on the connected device via `adb install -r`.

.PREREQUISITES
    - GitHub CLI (`gh`) installed and authenticated: run `gh auth login` once.
    - A device connected and authorized: `adb devices` should list it.

.EXAMPLE
    .\scripts\test-on-device.ps1
#>

param(
    [string]$AdbPath = "",
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"

# -- Resolve adb --------------------------------------------------------------
function Resolve-Adb {
    param([string]$Override)
    if ($Override) { return $Override }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v4.0\adb.exe"
    if (Test-Path $fallback) { return $fallback }
    throw "adb not found. Pass -AdbPath, or install Android platform-tools / scrcpy."
}

$adb = Resolve-Adb -Override $AdbPath
$adbArgs = @()
if ($DeviceSerial) { $adbArgs += @("-s", $DeviceSerial) }

Write-Host "Using adb: $adb"
& $adb @adbArgs devices

# -- Push current branch if needed -------------------------------------------
$branch = (git branch --show-current).Trim()
if (-not $branch) { throw "Not on a branch (detached HEAD?)." }

$ahead = (git rev-list '@{u}..HEAD' --count 2>$null)
if ($LASTEXITCODE -ne 0) {
    Write-Host "Branch '$branch' has no upstream yet - pushing with -u..."
    git push -u origin $branch
} elseif ([int]$ahead -gt 0) {
    Write-Host "Pushing $ahead unpushed commit(s) on '$branch'..."
    git push
} else {
    Write-Host "Branch '$branch' is up to date with origin."
}

$sha = (git rev-parse HEAD).Trim()
Write-Host "Branch: $branch  Commit: $sha"

# -- Find the workflow run for this commit (poll until it appears) ----------
Write-Host "Waiting for the CI run to appear..."
$runId = $null
for ($i = 0; $i -lt 30; $i++) {
    $runsJson = gh run list --branch $branch --json databaseId,headSha,status,conclusion,createdAt --limit 10
    $runs = $runsJson | ConvertFrom-Json
    $match = $runs | Where-Object { $_.headSha -eq $sha } | Select-Object -First 1
    if ($match) { $runId = $match.databaseId; break }
    Start-Sleep -Seconds 5
}
if (-not $runId) { throw "No workflow run found for commit $sha after 2.5 minutes." }

Write-Host "Run ID: $runId - watching until it finishes (this can take a few minutes)..."
gh run watch $runId --exit-status
if ($LASTEXITCODE -ne 0) { throw "CI run $runId failed. Run 'gh run view $runId --log-failed' for details." }

# -- Download the debug APK artifact -----------------------------------------
$dest = Join-Path $env:TEMP "sharesonic-debug-$sha"
if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Path $dest | Out-Null

Write-Host "Downloading 'sharesonic-debug' artifact to $dest ..."
gh run download $runId -n sharesonic-debug -D $dest

$apk = Get-ChildItem -Path $dest -Filter *.apk -Recurse | Select-Object -First 1
if (-not $apk) { throw "No APK found in downloaded artifact." }

# -- Install on device --------------------------------------------------------
Write-Host "Installing $($apk.Name) on device..."
& $adb @adbArgs install -r $apk.FullName

Write-Host "Done. Launch Sharesonic on the device to test."
