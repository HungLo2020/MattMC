# PowerShell script to download Temurin OpenJDK for Windows
# This script checks if the JDK is already present and downloads it if needed

$ErrorActionPreference = "Stop"

# Get script directory and project directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

# Read Java version from gradle.properties
$GradlePropertiesPath = Join-Path $ProjectDir "gradle.properties"
if (Test-Path $GradlePropertiesPath) {
    $JavaVersion = (Select-String -Path $GradlePropertiesPath -Pattern '^java_version=(.+)$').Matches.Groups[1].Value
    Write-Host "[INFO] Using Java version $JavaVersion from gradle.properties" -ForegroundColor Cyan
} else {
    Write-Host "[WARNING] gradle.properties not found, using default Java version" -ForegroundColor Yellow
    $JavaVersion = "25"
}

$JdkRoot = Join-Path $ScriptDir "jdk"

# JDK version configuration - Update these when a new Java version is released
# This is the specific build version to download (e.g., 25.0.1+8)
$JdkVersion = "25.0.1+8"
$JdkBuild = "25.0.1_8"

# Detect architecture
$Arch = $env:PROCESSOR_ARCHITECTURE
if ($Arch -eq "AMD64") {
    $Platform = "win-x64"
    $JdkUrl = "https://github.com/adoptium/temurin$JavaVersion-binaries/releases/download/jdk-$JdkVersion/OpenJDK$($JavaVersion)U-jdk_x64_windows_hotspot_$JdkBuild.zip"
    $JdkArchive = "OpenJDK$($JavaVersion)U-jdk_x64_windows_hotspot_$JdkBuild.zip"
    $JdkExtractedDir = "jdk-$JdkVersion"
} elseif ($Arch -eq "ARM64") {
    $Platform = "win-aarch64"
    $JdkUrl = "https://github.com/adoptium/temurin$JavaVersion-binaries/releases/download/jdk-$JdkVersion/OpenJDK$($JavaVersion)U-jdk_aarch64_windows_hotspot_$JdkBuild.zip"
    $JdkArchive = "OpenJDK$($JavaVersion)U-jdk_aarch64_windows_hotspot_$JdkBuild.zip"
    $JdkExtractedDir = "jdk-$JdkVersion"
} else {
    Write-Host "[ERROR] Unsupported architecture: $Arch" -ForegroundColor Red
    exit 1
}

$JdkDir = Join-Path $JdkRoot $Platform

# Check if JDK already exists
$JavaExe = Join-Path $JdkDir "bin\java.exe"
if ((Test-Path $JdkDir) -and (Test-Path $JavaExe)) {
    Write-Host "[OK] JDK already exists at: $JdkDir" -ForegroundColor Green
    exit 0
}

Write-Host "[DOWNLOAD] Downloading Temurin OpenJDK $JavaVersion for $Platform..." -ForegroundColor Cyan
Write-Host "   URL: $JdkUrl"

# Create temporary directory
$TempDir = Join-Path $env:TEMP "jdk-download-$([System.Guid]::NewGuid())"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

try {
    $ArchivePath = Join-Path $TempDir $JdkArchive
    
    # Download JDK using Invoke-WebRequest for better PowerShell compatibility
    Write-Host "[INFO] Downloading..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $JdkUrl -OutFile $ArchivePath -UseBasicParsing
    
    Write-Host "[INFO] Extracting JDK..." -ForegroundColor Yellow
    
    # Extract using built-in PowerShell
    $ExtractPath = Join-Path $TempDir "extracted"
    Expand-Archive -Path $ArchivePath -DestinationPath $ExtractPath -Force
    
    # Move to final location
    Write-Host "[INFO] Installing JDK to: $JdkDir" -ForegroundColor Yellow
    if (-not (Test-Path $JdkRoot)) {
        New-Item -ItemType Directory -Path $JdkRoot -Force | Out-Null
    }
    
    # Remove old JDK if exists
    if (Test-Path $JdkDir) {
        Remove-Item -Path $JdkDir -Recurse -Force
    }
    
    # Move extracted directory to final location
    $ExtractedJdkPath = Join-Path $ExtractPath $JdkExtractedDir
    Move-Item -Path $ExtractedJdkPath -Destination $JdkDir -Force
    
    Write-Host "[SUCCESS] JDK installed successfully!" -ForegroundColor Green
    
    # Verify installation
    $JavaExe = Join-Path $JdkDir "bin\java.exe"
    & $JavaExe -version
    
    Write-Host ""
    Write-Host "[SUCCESS] Temurin OpenJDK $JavaVersion is ready to use at: $JdkDir" -ForegroundColor Green
    
} finally {
    # Clean up temporary directory
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
    }
}
