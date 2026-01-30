# Rust Development Environment Setup Script for Windows
# This script installs Rust and Cargo on Windows systems using PowerShell

# Requires PowerShell 5.0 or higher
#Requires -Version 5.0

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Rust Development Environment Setup" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Function to check if MSVC linker is available
function Test-MSVCLinker {
    $linkExe = Get-Command link.exe -ErrorAction SilentlyContinue
    if ($linkExe) {
        # Verify it's the Microsoft linker, not another tool named link.exe
        try {
            $output = & link.exe 2>&1 | Out-String
            if ($output -match "Microsoft") {
                return $true
            }
        } catch {
            return $false
        }
    }
    return $false
}

# Function to check if current toolchain needs MSVC linker
function Test-NeedsMSVCLinker {
    $rustupInstalled = Get-Command rustup -ErrorAction SilentlyContinue
    if ($rustupInstalled) {
        try {
            $toolchainInfo = & rustup show 2>&1 | Out-String
            # If using GNU toolchain, MSVC linker is not needed
            if ($toolchainInfo -match "pc-windows-gnu") {
                return $false
            }
        } catch {
            # If we can't determine, assume MSVC is needed (default on Windows)
            return $true
        }
    }
    # Default Windows toolchain is MSVC
    return $true
}

# Check if Rust is already installed
$rustcInstalled = Get-Command rustc -ErrorAction SilentlyContinue
$cargoInstalled = Get-Command cargo -ErrorAction SilentlyContinue

if ($rustcInstalled -and $cargoInstalled) {
    Write-Host "Rust is already installed!" -ForegroundColor Green
    Write-Host ""
    rustc --version
    cargo --version
    Write-Host ""

    # Check for MSVC linker
    Write-Host "Checking for MSVC linker (link.exe)..." -ForegroundColor Cyan
    $needsMSVC = Test-NeedsMSVCLinker

    if ($needsMSVC) {
        $hasLinker = Test-MSVCLinker

        if (-not $hasLinker) {
            Write-Host ""
            Write-Host "WARNING: MSVC linker (link.exe) not found!" -ForegroundColor Red
            Write-Host ""
            Write-Host "You may encounter build errors. See solutions below." -ForegroundColor Yellow
            Write-Host ""
        } else {
            Write-Host "MSVC linker found! You're ready to build." -ForegroundColor Green
            Write-Host ""
        }
    } else {
        Write-Host "Using GNU toolchain - MSVC linker not required." -ForegroundColor Green
        Write-Host ""
        $hasLinker = $true  # Set to true to skip warnings
    }

    $update = Read-Host "Do you want to update Rust? (y/n)"
    if ($update -eq 'y' -or $update -eq 'Y') {
        $rustupInstalled = Get-Command rustup -ErrorAction SilentlyContinue
        if ($rustupInstalled) {
            Write-Host "Updating Rust via rustup..." -ForegroundColor Yellow
            rustup update
            Write-Host "Rust updated successfully!" -ForegroundColor Green
        } else {
            Write-Host "rustup not found. Please reinstall Rust." -ForegroundColor Red
        }
    }

    # If linker not found, show solutions before exiting
    if (-not $hasLinker) {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host "ACTION REQUIRED: Install MSVC linker" -ForegroundColor Red
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "OPTION 1: Install Visual Studio Build Tools (Recommended)" -ForegroundColor Cyan
        Write-Host "  Download: https://visualstudio.microsoft.com/visual-cpp-build-tools/" -ForegroundColor White
        Write-Host "  Select: 'Desktop development with C++'" -ForegroundColor White
        Write-Host ""
        Write-Host "OPTION 2: Switch to GNU toolchain" -ForegroundColor Cyan
        Write-Host "  rustup toolchain install stable-x86_64-pc-windows-gnu" -ForegroundColor Yellow
        Write-Host "  rustup default stable-x86_64-pc-windows-gnu" -ForegroundColor Yellow
        Write-Host "  (Requires MinGW-w64: choco install mingw)" -ForegroundColor White
        Write-Host ""
    }

    exit 0
}

Write-Host "Rust is not installed. Proceeding with installation..." -ForegroundColor Yellow
Write-Host ""

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Warning: Not running as Administrator." -ForegroundColor Yellow
    Write-Host "The installation may require elevated privileges." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "Installing Rust using rustup-init (official installer)..." -ForegroundColor Cyan
Write-Host "This will download and install the latest stable Rust toolchain." -ForegroundColor Cyan
Write-Host ""

# Create temporary directory for download
$tempDir = Join-Path $env:TEMP "rustup-init"
if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}

# Download rustup-init.exe
$architecture = $env:PROCESSOR_ARCHITECTURE.ToLower()
$rustupUrl = switch ($architecture) {
    "amd64" { "https://win.rustup.rs/x86_64" }
    "x86_64" { "https://win.rustup.rs/x86_64" }
    "arm64" { "https://win.rustup.rs/aarch64" }
    "x86" { "https://win.rustup.rs/i686" }
    default { "https://win.rustup.rs/x86_64" }
}

Write-Host "Detected architecture: $architecture" -ForegroundColor Cyan
$rustupPath = Join-Path $tempDir "rustup-init.exe"

Write-Host "Downloading rustup-init.exe..." -ForegroundColor Yellow

try {
    # Use TLS 1.2 and 1.3 for secure downloads
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

    Invoke-WebRequest -Uri $rustupUrl -OutFile $rustupPath -UseBasicParsing

    Write-Host "Download complete!" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "Error downloading rustup-init: $_" -ForegroundColor Red
    exit 1
}

# Run rustup-init
Write-Host "Running rustup-init installer..." -ForegroundColor Yellow
Write-Host "Please follow the on-screen prompts." -ForegroundColor Yellow
Write-Host ""

try {
    # Run with default profile (this installs stable toolchain)
    & $rustupPath -y

    Write-Host ""
    Write-Host "Installation complete!" -ForegroundColor Green
} catch {
    Write-Host "Error running rustup-init: $_" -ForegroundColor Red
    exit 1
}

# Clean up
Remove-Item -Path $rustupPath -Force -ErrorAction SilentlyContinue

# Add Cargo bin to PATH for current session
$cargoPath = Join-Path $env:USERPROFILE ".cargo\bin"
if (Test-Path $cargoPath) {
    $env:Path = "$cargoPath;$env:Path"
}

# Verify installation
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Verifying installation..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Note: The PATH will be updated for new sessions automatically
# For current session verification, we need to check the cargo directory directly
$cargoBinPath = Join-Path $env:USERPROFILE ".cargo\bin"
$rustcPath = Join-Path $cargoBinPath "rustc.exe"
$cargoPath = Join-Path $cargoBinPath "cargo.exe"

if (Test-Path $rustcPath) {
    & $rustcPath --version
} else {
    Write-Host "Error: rustc not found in $cargoBinPath" -ForegroundColor Red
    Write-Host "Installation may have failed. Please restart your PowerShell and try again." -ForegroundColor Yellow
    exit 1
}

if (Test-Path $cargoPath) {
    & $cargoPath --version
} else {
    Write-Host "Error: cargo not found in $cargoBinPath" -ForegroundColor Red
    Write-Host "Installation may have failed. Please restart your PowerShell and try again." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Setup Complete!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Rust has been installed successfully!" -ForegroundColor Green
Write-Host ""

# Check for MSVC linker
Write-Host "Checking for MSVC linker (link.exe)..." -ForegroundColor Cyan
$needsMSVC = Test-NeedsMSVCLinker

if ($needsMSVC) {
    $hasLinker = Test-MSVCLinker

    if (-not $hasLinker) {
        Write-Host ""
        Write-Host "WARNING: MSVC linker (link.exe) not found!" -ForegroundColor Red
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "The MSVC toolchain requires Visual Studio Build Tools with C++ support." -ForegroundColor Yellow
        Write-Host "Without this, you will get errors like 'linker link.exe not found' when building." -ForegroundColor Yellow
        Write-Host ""
        Write-Host "You have two options:" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "OPTION 1: Install Visual Studio Build Tools (Recommended)" -ForegroundColor White
        Write-Host "  1. Download from: https://visualstudio.microsoft.com/visual-cpp-build-tools/" -ForegroundColor White
        Write-Host "  2. Run the installer" -ForegroundColor White
        Write-Host "  3. Select 'Desktop development with C++'" -ForegroundColor White
        Write-Host "  4. Restart your terminal after installation" -ForegroundColor White
        Write-Host ""
        Write-Host "OPTION 2: Use GNU toolchain instead" -ForegroundColor White
        Write-Host "  Run these commands to switch to the GNU toolchain:" -ForegroundColor White
        Write-Host "    rustup toolchain install stable-x86_64-pc-windows-gnu" -ForegroundColor Yellow
        Write-Host "    rustup default stable-x86_64-pc-windows-gnu" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Note: GNU toolchain requires MinGW-w64. Install it with:" -ForegroundColor White
        Write-Host "    choco install mingw" -ForegroundColor Yellow
        Write-Host "  (Requires Chocolatey: https://chocolatey.org/install)" -ForegroundColor White
        Write-Host ""
    } else {
        Write-Host "MSVC linker found! You're ready to build Rust projects." -ForegroundColor Green
        Write-Host ""
    }
} else {
    Write-Host "Using GNU toolchain - MSVC linker not required." -ForegroundColor Green
    Write-Host ""
}

Write-Host "IMPORTANT: Please restart your PowerShell session or terminal" -ForegroundColor Yellow
Write-Host "for the PATH changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "To update Rust in the future, run:" -ForegroundColor Cyan
Write-Host "  rustup update" -ForegroundColor White
Write-Host ""
Write-Host "You can now build the project with:" -ForegroundColor Cyan
Write-Host "  cargo build" -ForegroundColor White
Write-Host "  cargo run" -ForegroundColor White
Write-Host ""