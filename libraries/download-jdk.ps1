# PowerShell script to download Temurin OpenJDK 21 for Windows
# This script checks if the JDK is already present and downloads it if needed

$ErrorActionPreference = "Stop"

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JdkDir = Join-Path $ScriptDir "jdk-21"

# JDK version configuration - change these to update version
$JdkVersion = "21.0.5+11"
$JdkBuild = "21.0.5_11"

# Detect architecture
$Arch = $env:PROCESSOR_ARCHITECTURE
if ($Arch -eq "AMD64") {
    $Platform = "windows-x64"
    $JdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-$JdkVersion/OpenJDK21U-jdk_x64_windows_hotspot_$JdkBuild.zip"
    $JdkArchive = "OpenJDK21U-jdk_x64_windows_hotspot_$JdkBuild.zip"
    $JdkExtractedDir = "jdk-$JdkVersion"
} elseif ($Arch -eq "ARM64") {
    $Platform = "windows-aarch64"
    $JdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-$JdkVersion/OpenJDK21U-jdk_aarch64_windows_hotspot_$JdkBuild.zip"
    $JdkArchive = "OpenJDK21U-jdk_aarch64_windows_hotspot_$JdkBuild.zip"
    $JdkExtractedDir = "jdk-$JdkVersion"
} else {
    Write-Host "❌ Unsupported architecture: $Arch" -ForegroundColor Red
    exit 1
}

# Check if JDK already exists
$JavaExe = Join-Path $JdkDir "bin\java.exe"
if ((Test-Path $JdkDir) -and (Test-Path $JavaExe)) {
    Write-Host "✅ JDK already exists at: $JdkDir" -ForegroundColor Green
    exit 0
}

Write-Host "📥 Downloading Temurin OpenJDK 21 for $Platform..." -ForegroundColor Cyan
Write-Host "   URL: $JdkUrl"

# Create temporary directory
$TempDir = Join-Path $env:TEMP "jdk-download-$(Get-Random)"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

try {
    $ArchivePath = Join-Path $TempDir $JdkArchive
    
    # Download JDK using .NET WebClient for better progress reporting
    Write-Host "⏬ Downloading..." -ForegroundColor Yellow
    $webClient = New-Object System.Net.WebClient
    
    # Add progress handler
    $webClient.DownloadProgressChanged += {
        param($sender, $e)
        Write-Progress -Activity "Downloading JDK" -Status "$($e.ProgressPercentage)% Complete" -PercentComplete $e.ProgressPercentage
    }
    
    $webClient.DownloadFileTaskAsync($JdkUrl, $ArchivePath).GetAwaiter().GetResult()
    Write-Progress -Activity "Downloading JDK" -Completed
    
    Write-Host "📦 Extracting JDK..." -ForegroundColor Yellow
    
    # Extract using built-in PowerShell
    $ExtractPath = Join-Path $TempDir "extracted"
    Expand-Archive -Path $ArchivePath -DestinationPath $ExtractPath -Force
    
    # Move to final location
    Write-Host "📂 Installing JDK to: $JdkDir" -ForegroundColor Yellow
    
    # Remove old JDK if exists
    if (Test-Path $JdkDir) {
        Remove-Item -Path $JdkDir -Recurse -Force
    }
    
    # Move extracted directory to final location
    $ExtractedJdkPath = Join-Path $ExtractPath $JdkExtractedDir
    Move-Item -Path $ExtractedJdkPath -Destination $JdkDir -Force
    
    Write-Host "✅ JDK installed successfully!" -ForegroundColor Green
    
    # Verify installation
    $JavaExe = Join-Path $JdkDir "bin\java.exe"
    & $JavaExe -version
    
    Write-Host ""
    Write-Host "🎉 Temurin OpenJDK 21 is ready to use at: $JdkDir" -ForegroundColor Green
    
} finally {
    # Clean up temporary directory
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
    }
}
