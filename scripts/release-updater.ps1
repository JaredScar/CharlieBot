param(
  [Parameter(Mandatory = $true)]
  [string]$Repo = "JaredScar/CharlieBot",              # e.g. "owner/repo"
  [string]$AssetName = "CharlieBot.jar",
  [string]$DownloadDir = "."
)

$ErrorActionPreference = "Stop"

function Get-LatestReleaseTag {
  param([string]$RepoName)

  $headers = @{}
  if ($env:GH_TOKEN) {
    $headers["Authorization"] = "Bearer $env:GH_TOKEN"
  }

  $uri = "https://api.github.com/repos/$RepoName/releases/latest"
  $res = Invoke-RestMethod -Method GET -Uri $uri -Headers $headers -ContentType "application/json"
  return $res.tag_name
}

function Download-AssetFromLatestRelease {
  param(
    [string]$RepoName,
    [string]$WantedAssetName,
    [string]$OutDir
  )

  $headers = @{}
  if ($env:GH_TOKEN) {
    $headers["Authorization"] = "Bearer $env:GH_TOKEN"
  }

  $uri = "https://api.github.com/repos/$RepoName/releases/latest"
  $res = Invoke-RestMethod -Method GET -Uri $uri -Headers $headers -ContentType "application/json"

  $asset = $res.assets | Where-Object { $_.name -eq $WantedAssetName } | Select-Object -First 1
  if (-not $asset) {
    throw "Asset '$WantedAssetName' not found in latest release '$($res.tag_name)'."
  }

  $outPath = Join-Path $OutDir $WantedAssetName

  # Use the browser_download_url so GitHub handles redirects/auth as appropriate.
  Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $outPath -UseBasicParsing
  return $outPath
}

function Main {
  $tag = Get-LatestReleaseTag -RepoName $Repo
  $localJar = Join-Path $DownloadDir "CharlieBot.jar"

  # If you want smarter "only download if changed", you can store a local tag file.
  $tagFile = Join-Path $DownloadDir "last_release_tag.txt"
  $prevTag = ""
  if (Test-Path $tagFile) {
    $prevTag = (Get-Content $tagFile -Raw).Trim()
  }

  if ($prevTag -eq $tag -and (Test-Path $localJar)) {
    Write-Host "Already on latest release ($tag)."
    return
  }

  Write-Host "Updating to latest release ($tag)..."
  $null = New-Item -ItemType Directory -Force -Path $DownloadDir
  $downloaded = Download-AssetFromLatestRelease -RepoName $Repo -WantedAssetName $AssetName -OutDir $DownloadDir
  Set-Content -Path $tagFile -Value $tag -Encoding UTF8
  Write-Host "Downloaded: $downloaded"

  # Exit so an external wrapper (Task Scheduler / restart script) can restart the bot.
  exit 100
}

Main

