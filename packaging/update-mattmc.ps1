param(
    [string]$Repo = 'HungLo2020/MattMC'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installDir = (Resolve-Path -LiteralPath $scriptDir).Path

function Normalize-ReleaseTag {
    param([string]$Version)

    if ([string]::IsNullOrWhiteSpace($Version)) {
        return ''
    }

    return $Version.Trim().TrimStart('v', 'V')
}

function Get-WindowsPlatformSuffix {
    param(
        [string]$Architecture
    )

    $archToken = switch ($Architecture.ToLowerInvariant()) {
        'x64' { 'x64' }
        'arm64' { 'aarch64' }
        default { $Architecture.ToLowerInvariant() }
    }

    return "windows-$archToken"
}

function Get-WindowsArchitecture {
    $architecture = $env:PROCESSOR_ARCHITEW6432
    if ([string]::IsNullOrWhiteSpace($architecture)) {
        $architecture = $env:PROCESSOR_ARCHITECTURE
    }

    switch ($architecture.ToLowerInvariant()) {
        'amd64' { return 'x64' }
        'x86_64' { return 'x64' }
        'arm64' { return 'arm64' }
        'aarch64' { return 'arm64' }
        default { throw "Unsupported Windows architecture: $architecture" }
    }
}

function Get-ExactReleaseAsset {
    param(
        [object[]]$Assets,
        [string]$PlatformSuffix,
        [string]$ReleaseTag
    )

    $tagVersion = Normalize-ReleaseTag $ReleaseTag
    if ($tagVersion -and $tagVersion -ne 'latest') {
        $expectedName = "MattMC-Client-$tagVersion-$PlatformSuffix.zip"
        $matches = @($Assets | Where-Object { [string]$_.name -eq $expectedName })

        if ($matches.Count -ne 1) {
            throw "Expected exactly one GitHub release asset named '$expectedName', found $($matches.Count)."
        }

        return [pscustomobject]@{
            Asset = $matches[0]
            Version = $tagVersion
            ExpectedName = $expectedName
        }
    }

    $pattern = "^MattMC-Client-(.+)-$([regex]::Escape($PlatformSuffix))\.zip$"
    $matches = @(
        $Assets | Where-Object {
            [string]$_.name -match $pattern
        }
    )

    if ($matches.Count -ne 1) {
        throw "Expected exactly one GitHub release asset matching 'MattMC-Client-<version>-$PlatformSuffix.zip', found $($matches.Count)."
    }

    $version = [regex]::Match([string]$matches[0].name, $pattern).Groups[1].Value

    return [pscustomobject]@{
        Asset = $matches[0]
        Version = $version
        ExpectedName = [string]$matches[0].name
    }
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

function Save-ReleaseAsset {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [string]$Destination
    )

    $client = New-Object System.Net.WebClient
    try {
        foreach ($key in $Headers.Keys) {
            $client.Headers.Add($key, [string]$Headers[$key])
        }

        $client.DownloadFile($Uri, $Destination)
    }
    finally {
        $client.Dispose()
    }
}

Write-Host "MattMC updater"
Write-Host "Install: $installDir"

$apiUrl = "https://api.github.com/repos/$Repo/releases/latest"
$headers = @{
    'Accept' = 'application/vnd.github+json'
    'User-Agent' = 'MattMC-Updater'
}

$release = Invoke-RestMethod -Uri $apiUrl -Headers $headers
$arch = Get-WindowsArchitecture
$platformSuffix = Get-WindowsPlatformSuffix -Architecture $arch
$assetInfo = Get-ExactReleaseAsset -Assets @($release.assets) -PlatformSuffix $platformSuffix -ReleaseTag ([string]$release.tag_name)
$asset = $assetInfo.Asset

Write-Host "Release: $($release.name) ($($release.tag_name))"
Write-Host "Expected asset: $($assetInfo.ExpectedName)"
Write-Host "Downloading: $($asset.name)"

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mattmc-update-" + [System.Guid]::NewGuid().ToString('N'))
$downloadPath = Join-Path $tempRoot $asset.name
$extractDir = Join-Path $tempRoot 'extract'

New-Item -ItemType Directory -Path $tempRoot, $extractDir -Force | Out-Null

try {
    Save-ReleaseAsset -Uri $asset.browser_download_url -Headers $headers -Destination $downloadPath
    Expand-Archive -LiteralPath $downloadPath -DestinationPath $extractDir -Force

    $payloadRoot = Get-PayloadRoot -ExtractDir $extractDir
    Write-Host "Applying update from: $payloadRoot"

    Copy-UpdatePayload -SourceRoot $payloadRoot -DestinationRoot $installDir

    Write-Host "Update complete."
    Write-Host "Installed release asset: $($asset.name)"
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
