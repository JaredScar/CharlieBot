param(
  [string]$Repo = "JaredScar/CharlieBot",           # "owner/repo" for updater checks, optional
  [switch]$AutoUpdate          # when set, updater will check and download newest release
)

$ErrorActionPreference = "Stop"

if ($AutoUpdate.IsPresent) {
  if ([string]::IsNullOrWhiteSpace($Repo)) {
    throw "Repo is required when -AutoUpdate is used. Provide -Repo 'owner/repo'."
  }

  # Call updater; it exits with code 100 when an update was downloaded.
  # We ignore the exit code here and always proceed to start the jar below.
  & "$PSScriptRoot\release-updater.ps1" -Repo $Repo -DownloadDir $PSScriptRoot
}

$jarPath = Join-Path $PSScriptRoot "CharlieBot.jar"
if (-not (Test-Path $jarPath)) {
  # Fallback: if jar is in repo root, try there.
  $jarPath = Join-Path $PSScriptRoot "..\CharlieBot.jar"
}
if (-not (Test-Path $jarPath)) {
  throw "CharlieBot.jar not found. Expected in scripts/ or repo root."
}

Write-Host "Starting bot from: $jarPath"

# Start the bot in the current process so Task Scheduler / service wrappers manage lifecycle.
& java -jar $jarPath

