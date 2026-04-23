param(
    [string]$RemoteSyncRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$localDir = $scriptDir
$localName = Split-Path -Leaf $localDir

function Resolve-RemoteSyncRoot {
    param(
        [string]$ExplicitRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitRoot)) {
        return $ExplicitRoot
    }

    if (-not [string]::IsNullOrWhiteSpace($env:MATTMC_SYNC_ROOT)) {
        return $env:MATTMC_SYNC_ROOT
    }

    if (Test-Path 'Z:\') {
        return 'Z:\Storage\Sync'
    }

    throw "Unable to determine remote sync root. Provide -RemoteSyncRoot, set MATTMC_SYNC_ROOT, or mount Z:."
}

$remoteSyncRootResolved = Resolve-RemoteSyncRoot -ExplicitRoot $RemoteSyncRoot
$remoteDir = Join-Path $remoteSyncRootResolved $localName

function Invoke-RobocopySync {
    param(
        [string]$Source,
        [string]$Destination
    )

    $robocopyArgs = @(
        $Source,
        $Destination,
        '/E',
        '/COPY:DAT',
        '/DCOPY:DAT',
        '/R:2',
        '/W:2',
        '/XJ',
        '/FFT',
        '/NP'
    )

    & robocopy @robocopyArgs | Out-Host
    $exitCode = $LASTEXITCODE

    if ($exitCode -ge 8) {
        throw "robocopy failed with exit code $exitCode"
    }
}

function Sync-Up {
    if (-not (Test-Path -LiteralPath $remoteDir)) {
        New-Item -ItemType Directory -Path $remoteDir -Force | Out-Null
    }

    Write-Host "Sync direction: up"
    Write-Host "Local  -> Remote"
    Write-Host "From: $localDir"
    Write-Host "To:   $remoteDir"

    Invoke-RobocopySync -Source $localDir -Destination $remoteDir

    Write-Host "Done. Remote updated with local changes (files never deleted)."
}

function Sync-Down {
    if (-not (Test-Path -LiteralPath $remoteDir)) {
        throw "Remote directory does not exist: $remoteDir"
    }

    Write-Host "Sync direction: down"
    Write-Host "Remote -> Local"
    Write-Host "From: $remoteDir"
    Write-Host "To:   $localDir"

    Invoke-RobocopySync -Source $remoteDir -Destination $localDir

    Write-Host "Done. Local updated with remote changes (files never deleted)."
}

function Main {
    if ($localName -ne 'MattMC') {
        Write-Warning "Script is currently located in '$localDir'."
        Write-Warning "Expected final location is inside a directory named 'MattMC'."
        Write-Warning "Remote target will use directory name: '$localName'."
    }

    if (-not (Test-Path -LiteralPath $remoteSyncRootResolved)) {
        Write-Warning "Remote sync root not found right now: $remoteSyncRootResolved"
        Write-Warning "If this is a network mount/share, make sure it is connected before syncing."
    }

    while ($true) {
        $direction = (Read-Host "Enter sync direction ('up' or 'down')").Trim().ToLowerInvariant()

        switch ($direction) {
            'up' {
                Sync-Up
                break
            }
            'down' {
                Sync-Down
                break
            }
            default {
                Write-Host "Please enter exactly: up or down"
            }
        }
    }
}

Main
