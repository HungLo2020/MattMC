param(
    [switch]$Force,
    [string]$Repo = 'HungLo2020/MattMC'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$CurrentVersion = '@VERSION@'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installDir = (Resolve-Path -LiteralPath $scriptDir).Path

function Normalize-Version {
    param([string]$Version)

    if ([string]::IsNullOrWhiteSpace($Version)) {
        return ''
    }

    return $Version.Trim().TrimStart('v', 'V')
}

function Get-ExpectedAssetName {
    param(
        [string]$Architecture
    )

    $archToken = switch ($Architecture.ToLowerInvariant()) {
        'x64' { 'x64' }
        'arm64' { 'aarch64' }
        default { $Architecture.ToLowerInvariant() }
    }

    return "MattMC-Client-win-$archToken.zip"
}

function Get-ExactReleaseAsset {
    param(
        [object[]]$Assets,
        [string]$ExpectedName
    )

    $matches = @($Assets | Where-Object { [string]$_.name -eq $ExpectedName })

    if ($matches.Count -ne 1) {
        throw "Expected exactly one GitHub release asset named '$ExpectedName', found $($matches.Count)."
    }

    return $matches[0]
}

function Get-PayloadRoot {
    param([string]$ExtractDir)

    $mattmcRoot = Join-Path $ExtractDir 'MattMC'
    if (Test-Path -LiteralPath $mattmcRoot) {
        return (Resolve-Path -LiteralPath $mattmcRoot).Path
    }

    $dirs = @(Get-ChildItem -LiteralPath $ExtractDir -Force -Directory)
    if ($dirs.Count -eq 1) {
        return $dirs[0].FullName
    }

    $hasInstallShape =
        (Test-Path -LiteralPath (Join-Path $ExtractDir 'lib')) -or
        (Test-Path -LiteralPath (Join-Path $ExtractDir 'run-mattmc.bat')) -or
        (Test-Path -LiteralPath (Join-Path $ExtractDir 'run-mattmc.sh'))

    if ($hasInstallShape) {
        return $ExtractDir
    }

    throw "Unable to find MattMC payload root inside downloaded archive."
}

function Copy-UpdatePayload {
    param(
        [string]$SourceRoot,
        [string]$DestinationRoot
    )

    Get-ChildItem -LiteralPath $SourceRoot -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $DestinationRoot -Recurse -Force
    }
}

Write-Host "MattMC updater"
Write-Host "Install: $installDir"
Write-Host "Current version: $CurrentVersion"

$apiUrl = "https://api.github.com/repos/$Repo/releases/latest"
$headers = @{
    'Accept' = 'application/vnd.github+json'
    'User-Agent' = 'MattMC-Updater'
}

$release = Invoke-RestMethod -Uri $apiUrl -Headers $headers
$latestVersion = [string]$release.tag_name

if (-not $Force -and (Normalize-Version $latestVersion) -eq (Normalize-Version $CurrentVersion)) {
    Write-Host "Already up to date: $latestVersion"
    exit 0
}

$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
$expectedAssetName = Get-ExpectedAssetName -Architecture $arch
$asset = Get-ExactReleaseAsset -Assets @($release.assets) -ExpectedName $expectedAssetName

Write-Host "Latest version: $latestVersion"
Write-Host "Expected asset: $expectedAssetName"
Write-Host "Downloading: $($asset.name)"

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mattmc-update-" + [System.Guid]::NewGuid().ToString('N'))
$downloadPath = Join-Path $tempRoot $asset.name
$extractDir = Join-Path $tempRoot 'extract'

New-Item -ItemType Directory -Path $tempRoot, $extractDir -Force | Out-Null

try {
    Invoke-WebRequest -Uri $asset.browser_download_url -Headers $headers -OutFile $downloadPath
    Expand-Archive -LiteralPath $downloadPath -DestinationPath $extractDir -Force

    $payloadRoot = Get-PayloadRoot -ExtractDir $extractDir
    Write-Host "Applying update from: $payloadRoot"

    Copy-UpdatePayload -SourceRoot $payloadRoot -DestinationRoot $installDir

    Write-Host "Update complete."
    Write-Host "Installed version: $latestVersion"
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
