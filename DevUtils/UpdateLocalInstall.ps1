param(
    [string]$InstallRoot = 'C:\Users\mcshe\Games\MattMC'
)

# UpdateLocalInstall.ps1 - Build MattMC and update the local exported install.
# Builds a fresh jar and Windows Rust native library, then copies them into:
#   C:\Users\mcshe\Games\MattMC\lib\
#   C:\Users\mcshe\Games\MattMC\natives\
# Only the native library produced for the current platform is overwritten.
# It also refreshes launcher/helper scripts from packaging/.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = $scriptDir

while (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat'))) {
    $parent = Split-Path -Parent $projectRoot
    if ([string]::IsNullOrWhiteSpace($parent) -or $parent -eq $projectRoot) {
        throw 'Could not find gradlew.bat. Are you in the MattMC project?'
    }

    $projectRoot = $parent
}

$libDir = Join-Path $InstallRoot 'lib'
$nativesDir = Join-Path $InstallRoot 'natives'
$serverDir = Join-Path $InstallRoot 'server'
$packagingDir = Join-Path $projectRoot 'packaging'
$builtNativesDir = Join-Path $projectRoot 'build\rust\native'

function Invoke-Gradle {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    Push-Location $projectRoot
    try {
        & .\gradlew.bat @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE for arguments: $($Arguments -join ' ')"
        }
    }
    finally {
        Pop-Location
    }
}

function Resolve-RustNativeFileName {
    if ($env:OS -ne 'Windows_NT') {
        throw "UpdateLocalInstall.ps1 is intended to build the Windows Rust native, but this host OS is not Windows."
    }

    $rawArch = if (-not [string]::IsNullOrWhiteSpace($env:PROCESSOR_ARCHITEW6432)) {
        $env:PROCESSOR_ARCHITEW6432
    } else {
        $env:PROCESSOR_ARCHITECTURE
    }

    $archPart = switch -Regex ($rawArch.ToLowerInvariant()) {
        '^(amd64|x86_64)$' { 'x64'; break }
        '^(arm64|aarch64)$' { 'aarch64'; break }
        default { throw "Unsupported architecture for Rust native library: $rawArch" }
    }

    return "mattmc_rust-win-$archPart.dll"
}

function Get-ProjectVersion {
    Push-Location $projectRoot
    try {
        $propertiesOutput = & .\gradlew.bat properties -q --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle properties failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    foreach ($line in $propertiesOutput) {
        if ($line -match '^version:\s+(.+)$') {
            return $Matches[1].Trim()
        }
    }

    throw 'Could not determine project version.'
}

function Copy-Template {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceFile,
        [Parameter(Mandatory = $true)]
        [string]$DestinationFile,
        [Parameter(Mandatory = $true)]
        [string]$Version
    )

    $destinationParent = Split-Path -Parent $DestinationFile
    if (-not [string]::IsNullOrWhiteSpace($destinationParent)) {
        New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
    }

    $content = [System.IO.File]::ReadAllText($SourceFile).Replace('@VERSION@', $Version)
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($DestinationFile, $content, $utf8NoBom)
}

Write-Host '========================================='
Write-Host '  MattMC Local Install Update'
Write-Host '========================================='
Write-Host ''

Write-Host '[1/6] Building fresh jar and optimized Rust native...'
Invoke-Gradle -Arguments @('clean', 'buildRustNative', 'jar', '-PmattmcRustProfile=release', '--rerun-tasks', '--no-daemon')

Write-Host '[2/6] Locating built jar...'
$jarFile = Get-ChildItem -Path (Join-Path $projectRoot 'build\libs') -Filter 'MattMC*.jar' -File |
    Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jarFile) {
    throw "Could not find built jar in $(Join-Path $projectRoot 'build\libs')."
}

Write-Host "    Found: $($jarFile.FullName)"

Write-Host '[3/6] Ensuring local install directories exist...'
New-Item -ItemType Directory -Path $libDir, $nativesDir, $serverDir -Force | Out-Null

Write-Host "[4/6] Copying jar to $libDir..."
Copy-Item -LiteralPath $jarFile.FullName -Destination (Join-Path $libDir $jarFile.Name) -Force

Write-Host "[5/6] Refreshing Rust native in $nativesDir..."
if (-not (Test-Path -LiteralPath $builtNativesDir -PathType Container)) {
    throw "Rust native output directory does not exist: $builtNativesDir"
}

$rustNativeFileName = Resolve-RustNativeFileName
$builtNativeFile = Join-Path $builtNativesDir $rustNativeFileName
if (-not (Test-Path -LiteralPath $builtNativeFile -PathType Leaf)) {
    throw "Expected Rust native library was not produced: $builtNativeFile"
}

$destinationNativeFile = Join-Path $nativesDir $rustNativeFileName
Copy-Item -LiteralPath $builtNativeFile -Destination $destinationNativeFile -Force
Write-Host "    Updated: $destinationNativeFile"

$version = Get-ProjectVersion

Write-Host '[6/6] Refreshing packaging scripts...'
Get-ChildItem -LiteralPath $packagingDir -File | ForEach-Object {
    $scriptFile = $_
    $fileName = $scriptFile.Name

    switch -Wildcard ($fileName) {
        'run-server.sh' {
            Copy-Template -SourceFile $scriptFile.FullName -DestinationFile (Join-Path $serverDir $fileName) -Version $version
        }
        'run-server.bat' {
            Copy-Template -SourceFile $scriptFile.FullName -DestinationFile (Join-Path $serverDir $fileName) -Version $version
        }
        'SERVER-README.md' {
            Copy-Item -LiteralPath $scriptFile.FullName -Destination (Join-Path $serverDir 'README.md') -Force
        }
        '*.sh' {
            Copy-Template -SourceFile $scriptFile.FullName -DestinationFile (Join-Path $InstallRoot $fileName) -Version $version
        }
        '*.bat' {
            Copy-Template -SourceFile $scriptFile.FullName -DestinationFile (Join-Path $InstallRoot $fileName) -Version $version
        }
        '*.ps1' {
            Copy-Template -SourceFile $scriptFile.FullName -DestinationFile (Join-Path $InstallRoot $fileName) -Version $version
        }
        default {
            Copy-Item -LiteralPath $scriptFile.FullName -Destination (Join-Path $InstallRoot $fileName) -Force
        }
    }
}

Write-Host ''
Write-Host '========================================='
Write-Host '  Export Complete!'
Write-Host '========================================='
Write-Host ''
Write-Host 'Updated local install:'
Write-Host "  $InstallRoot"
Write-Host ''
Write-Host 'Jar:'
Write-Host "  $(Join-Path $libDir $jarFile.Name)"
Write-Host ''
Write-Host 'Rust natives:'
Get-ChildItem -LiteralPath $nativesDir -File |
    Where-Object { $_.Extension -in @('.so', '.dll', '.dylib') } |
    Sort-Object FullName |
    ForEach-Object { Write-Host "  $($_.FullName)" }
Write-Host ''
