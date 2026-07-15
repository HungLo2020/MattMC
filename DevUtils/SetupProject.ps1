param(
    [switch]$NoInstall,
    [switch]$SkipBuildTools,
    [switch]$VerifyBuild
)

# SetupProject.ps1 - Prepare a Windows machine for MattMC local development.
#
# The MattMC build compiles mandatory Rust native code. That Rust crate builds
# shaderc from source, which requires Rust stable, Cargo, CMake, Ninja, Git,
# Python 3, and the MSVC C++ build tools on Windows.

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

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ''
    Write-Host "==> $Message"
}

function Write-Ok {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host "OK: $Message"
}

function Write-Warn {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Warning $Message
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

function Test-WindowsStorePythonAlias {
    param([Parameter(Mandatory = $true)][string]$Path)
    return ($Path -replace '\\', '/') -like '*/Microsoft/WindowsApps/*'
}

function Get-UsableCommand {
    param(
        [Parameter(Mandatory = $true)][string[]]$Names,
        [string[]]$VersionArguments = @('--version'),
        [switch]$RejectWindowsStorePythonAlias
    )

    foreach ($name in $Names) {
        $commands = @(Get-Command $name -ErrorAction SilentlyContinue)
        foreach ($command in $commands) {
            if ($RejectWindowsStorePythonAlias -and (Test-WindowsStorePythonAlias -Path $command.Source)) {
                continue
            }

            if ($VersionArguments.Count -eq 0) {
                return $command
            }

            $previousErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $output = & $command.Source @VersionArguments 2>&1
                if ($LASTEXITCODE -eq 0) {
                    return $command
                }
            }
            finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }
        }
    }

    return $null
}

function Invoke-InstallCommand {
    param(
        [Parameter(Mandatory = $true)][string]$DisplayName,
        [Parameter(Mandatory = $true)][string[]]$WingetArguments,
        [Parameter(Mandatory = $true)][string[]]$ChocoArguments
    )

    if ($NoInstall) {
        throw "$DisplayName is missing. Re-run without -NoInstall to install it."
    }

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        Write-Host "Installing $DisplayName with winget..."
        Invoke-Checked -FilePath $winget.Source -Arguments $WingetArguments
        return
    }

    $choco = Get-Command choco -ErrorAction SilentlyContinue
    if ($choco) {
        Write-Host "Installing $DisplayName with Chocolatey..."
        Invoke-Checked -FilePath $choco.Source -Arguments $ChocoArguments
        return
    }

    throw "$DisplayName is missing and neither winget nor Chocolatey is available."
}

function Update-ProcessPath {
    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = @($machinePath, $userPath) -join [IO.Path]::PathSeparator

    $cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
    if (Test-Path -LiteralPath $cargoBin) {
        $env:Path = "$cargoBin$([IO.Path]::PathSeparator)$env:Path"
    }
}

function Ensure-Rust {
    Write-Step 'Checking Rust toolchain'

    $cargo = Get-UsableCommand -Names @('cargo')
    if (-not $cargo) {
        $rustup = Get-UsableCommand -Names @('rustup')
        if (-not $rustup) {
            if ($NoInstall) {
                throw 'Rust/Cargo is missing. Re-run without -NoInstall to install rustup.'
            }

            Write-Host 'Installing rustup and Rust stable...'
            $installer = Join-Path ([IO.Path]::GetTempPath()) 'rustup-init.exe'
            Invoke-WebRequest -Uri 'https://win.rustup.rs/x86_64' -OutFile $installer
            try {
                Invoke-Checked -FilePath $installer -Arguments @('-y', '--profile', 'minimal', '--default-toolchain', 'stable')
            }
            finally {
                Remove-Item -LiteralPath $installer -Force -ErrorAction SilentlyContinue
            }
        }

        Update-ProcessPath
    }

    $rustup = Get-UsableCommand -Names @('rustup')
    if ($rustup) {
        Invoke-Checked -FilePath $rustup.Source -Arguments @('toolchain', 'install', 'stable', '--profile', 'minimal')
        Invoke-Checked -FilePath $rustup.Source -Arguments @('default', 'stable')
        Invoke-Checked -FilePath $rustup.Source -Arguments @('component', 'add', 'rustfmt', 'clippy')
    }

    Update-ProcessPath
    $cargo = Get-UsableCommand -Names @('cargo')
    if (-not $cargo) {
        throw 'Rust installation finished, but cargo is still not available. Open a new PowerShell window and rerun this script.'
    }

    Write-Ok (& $cargo.Source --version)
}

function Ensure-Python {
    Write-Step 'Checking Python 3'

    $python = Get-UsableCommand -Names @('python3', 'python') -RejectWindowsStorePythonAlias
    if (-not $python) {
        Invoke-InstallCommand `
            -DisplayName 'Python 3' `
            -WingetArguments @('install', '--id', 'Python.Python.3.12', '--exact', '--source', 'winget', '--accept-package-agreements', '--accept-source-agreements', '--silent') `
            -ChocoArguments @('install', 'python', '-y', '--no-progress')
        Update-ProcessPath
        $python = Get-UsableCommand -Names @('python3', 'python') -RejectWindowsStorePythonAlias
    }

    if (-not $python) {
        throw 'Python 3 installation finished, but python is still not available. Open a new PowerShell window and rerun this script.'
    }

    Write-Ok (& $python.Source --version)
    Invoke-Checked -FilePath $python.Source -Arguments @('-m', 'pip', 'install', '--upgrade', 'pip')
}

function Ensure-CMake {
    Write-Step 'Checking CMake'

    $cmake = Get-UsableCommand -Names @('cmake')
    if (-not $cmake) {
        Invoke-InstallCommand `
            -DisplayName 'CMake' `
            -WingetArguments @('install', '--id', 'Kitware.CMake', '--exact', '--source', 'winget', '--accept-package-agreements', '--accept-source-agreements', '--silent') `
            -ChocoArguments @('install', 'cmake', '-y', '--no-progress', '--installargs', 'ADD_CMAKE_TO_PATH=System')
        Update-ProcessPath
        $cmake = Get-UsableCommand -Names @('cmake')
    }

    if (-not $cmake) {
        throw 'CMake installation finished, but cmake is still not available. Open a new PowerShell window and rerun this script.'
    }

    Write-Ok (& $cmake.Source --version | Select-Object -First 1)
}

function Ensure-Ninja {
    Write-Step 'Checking Ninja'

    $ninja = Get-UsableCommand -Names @('ninja')
    if (-not $ninja) {
        Invoke-InstallCommand `
            -DisplayName 'Ninja' `
            -WingetArguments @('install', '--id', 'Ninja-build.Ninja', '--exact', '--source', 'winget', '--accept-package-agreements', '--accept-source-agreements', '--silent') `
            -ChocoArguments @('install', 'ninja', '-y', '--no-progress')
        Update-ProcessPath
        $ninja = Get-UsableCommand -Names @('ninja')
    }

    if (-not $ninja) {
        throw 'Ninja installation finished, but ninja is still not available. Open a new PowerShell window and rerun this script.'
    }

    Write-Ok (& $ninja.Source --version)
}

function Ensure-Git {
    Write-Step 'Checking Git'

    $git = Get-UsableCommand -Names @('git')
    if (-not $git) {
        Invoke-InstallCommand `
            -DisplayName 'Git' `
            -WingetArguments @('install', '--id', 'Git.Git', '--exact', '--source', 'winget', '--accept-package-agreements', '--accept-source-agreements', '--silent') `
            -ChocoArguments @('install', 'git', '-y', '--no-progress')
        Update-ProcessPath
        $git = Get-UsableCommand -Names @('git')
    }

    if (-not $git) {
        throw 'Git installation finished, but git is still not available. Open a new PowerShell window and rerun this script.'
    }

    Write-Ok (& $git.Source --version)
}

function Test-MsvcBuildTools {
    $cl = Get-UsableCommand -Names @('cl') -VersionArguments @()
    if ($cl) {
        return $true
    }

    $vswherePaths = @(
        "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe",
        "$env:ProgramFiles\Microsoft Visual Studio\Installer\vswhere.exe"
    )

    foreach ($vswherePath in $vswherePaths) {
        if (-not (Test-Path -LiteralPath $vswherePath)) {
            continue
        }

        $installPath = & $vswherePath -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($installPath)) {
            return $true
        }
    }

    return $false
}

function Ensure-MsvcBuildTools {
    Write-Step 'Checking MSVC C++ build tools'

    if ($SkipBuildTools) {
        Write-Warn 'Skipping MSVC C++ build tools check because -SkipBuildTools was supplied.'
        return
    }

    if (Test-MsvcBuildTools) {
        Write-Ok 'MSVC C++ build tools are installed.'
        return
    }

    Invoke-InstallCommand `
        -DisplayName 'Visual Studio Build Tools with MSVC C++ tools' `
        -WingetArguments @(
            'install',
            '--id', 'Microsoft.VisualStudio.2022.BuildTools',
            '--exact',
            '--source', 'winget',
            '--accept-package-agreements',
            '--accept-source-agreements',
            '--silent',
            '--override', '--quiet --wait --norestart --nocache --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended'
        ) `
        -ChocoArguments @('install', 'visualstudio2022buildtools', 'visualstudio2022-workload-vctools', '-y', '--no-progress')

    if (-not (Test-MsvcBuildTools)) {
        throw 'Visual Studio Build Tools installation finished, but MSVC C++ tools were not detected. Reboot or open a new Developer PowerShell, then rerun this script.'
    }

    Write-Ok 'MSVC C++ build tools are installed.'
}

function Invoke-OptionalBuildVerification {
    if (-not $VerifyBuild) {
        return
    }

    Write-Step 'Verifying MattMC Rust native build'
    Push-Location $projectRoot
    try {
        Invoke-Checked -FilePath (Join-Path $projectRoot 'gradlew.bat') -Arguments @('buildRustNative', '--no-daemon')
    }
    finally {
        Pop-Location
    }
}

if ($env:OS -ne 'Windows_NT') {
    throw 'SetupProject.ps1 is intended for Windows. Use DevUtils/SetupProject.sh on Unix-like systems.'
}

Write-Host '========================================='
Write-Host '  MattMC Windows Project Setup'
Write-Host '========================================='
Write-Host "Project root: $projectRoot"

Ensure-Git
Ensure-Python
Ensure-CMake
Ensure-Ninja
Ensure-MsvcBuildTools
Ensure-Rust
Invoke-OptionalBuildVerification

Write-Host ''
Write-Host 'Setup complete.'
Write-Host 'If any installer changed PATH, open a new PowerShell window before running Gradle.'
