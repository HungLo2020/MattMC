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

# Only MattMC\run is synchronized: worlds, settings, shader packs and the
# dedicated server (run\server). The bundled JDK (run\jdk) is replaced by
# updates instead and is never synced. Application files outside run\ are
# never touched in either direction.
$localRunDir = Join-Path $localDir 'run'
$remoteRunDir = Join-Path (Join-Path $remoteSyncRootResolved $localName) 'run'

function Invoke-RobocopySync {
    param(
        [string]$Source,
        [string]$Destination
    )

    # Mirror one run tree onto the other (deletes enabled). Excluding jdk on
    # both sides keeps it out of the copy and out of the purge.
    $robocopyArgs = @(
        $Source,
        $Destination,
        '/MIR',
        '/COPY:DAT',
        '/DCOPY:DAT',
        '/R:2',
        '/W:2',
        '/XJ',
        '/FFT',
        '/XD',
        (Join-Path $Source 'jdk'),
        (Join-Path $Destination 'jdk'),
        '/NP'
    )

    & robocopy @robocopyArgs | Out-Host
    $exitCode = $LASTEXITCODE

    if ($exitCode -ge 8) {
        throw "robocopy failed with exit code $exitCode"
    }
}

function Sync-Up {
    if (-not (Test-Path -LiteralPath $localRunDir)) {
        throw "Local run directory does not exist: $localRunDir"
    }
    if (-not (Test-Path -LiteralPath $remoteRunDir)) {
        New-Item -ItemType Directory -Path $remoteRunDir -Force | Out-Null
    }

    Write-Host "Sync direction: up"
    Write-Host "Local  -> Remote"
    Write-Host "From: $localRunDir"
    Write-Host "To:   $remoteRunDir"

    Invoke-RobocopySync -Source $localRunDir -Destination $remoteRunDir

    Write-Host "Done. Remote run mirrored from local (deletes enabled, run\jdk excluded)."
}

function Sync-Down {
    if (-not (Test-Path -LiteralPath $remoteRunDir)) {
        throw "Remote run directory does not exist: $remoteRunDir"
    }
    if (-not (Test-Path -LiteralPath $localRunDir)) {
        New-Item -ItemType Directory -Path $localRunDir -Force | Out-Null
    }

    Write-Host "Sync direction: down"
    Write-Host "Remote -> Local"
    Write-Host "From: $remoteRunDir"
    Write-Host "To:   $localRunDir"

    Invoke-RobocopySync -Source $remoteRunDir -Destination $localRunDir

    Write-Host "Done. Local run mirrored from remote (deletes enabled, run\jdk excluded)."
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
