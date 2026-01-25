# PowerShell script to download GraalVM for Windows
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

$JdkDir = Join-Path $ScriptDir "jdk-$JavaVersion"

# GraalVM version configuration
# Using GraalVM for JDK 21 as JDK 25 is not yet available for GraalVM
$GraalVMVersion = "21.0.5"
$GraalVMBuild = "21.0.5+9.1"

# Detect architecture
$Arch = $env:PROCESSOR_ARCHITECTURE
if ($Arch -eq "AMD64") {
    $Platform = "windows-x64"
    $JdkUrl = "https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_windows-x64_bin.zip"
    $JdkArchive = "graalvm-jdk-21_windows-x64_bin.zip"
    $JdkExtractedDir = "graalvm-jdk-$GraalVMBuild"
} elseif ($Arch -eq "ARM64") {
    Write-Host "[ERROR] GraalVM for Windows ARM64 is not officially available yet" -ForegroundColor Red
    Write-Host "   Please use x64 emulation or download manually from:" -ForegroundColor Yellow
    Write-Host "   https://www.graalvm.org/downloads/" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "[ERROR] Unsupported architecture: $Arch" -ForegroundColor Red
    exit 1
}

# Check if JDK already exists
$JavaExe = Join-Path $JdkDir "bin\java.exe"
$NativeImageExe = Join-Path $JdkDir "bin\native-image.cmd"
if ((Test-Path $JdkDir) -and (Test-Path $JavaExe) -and (Test-Path $NativeImageExe)) {
    Write-Host "[OK] GraalVM already exists at: $JdkDir" -ForegroundColor Green
    exit 0
}

Write-Host "[DOWNLOAD] Downloading GraalVM $GraalVMVersion for $Platform..." -ForegroundColor Cyan
Write-Host "   URL: $JdkUrl"

# Create temporary directory
$TempDir = Join-Path $env:TEMP "jdk-download-$([System.Guid]::NewGuid())"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

try {
    $ArchivePath = Join-Path $TempDir $JdkArchive
    
    # Download JDK using Invoke-WebRequest for better PowerShell compatibility
    Write-Host "[INFO] Downloading..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $JdkUrl -OutFile $ArchivePath -UseBasicParsing
    
    Write-Host "[INFO] Extracting GraalVM..." -ForegroundColor Yellow
    
    # Extract using built-in PowerShell
    $ExtractPath = Join-Path $TempDir "extracted"
    Expand-Archive -Path $ArchivePath -DestinationPath $ExtractPath -Force
    
    # Move to final location
    Write-Host "[INFO] Installing GraalVM to: $JdkDir" -ForegroundColor Yellow
    
    # Remove old JDK if exists
    if (Test-Path $JdkDir) {
        Remove-Item -Path $JdkDir -Recurse -Force
    }
    
    # Move extracted directory to final location
    $ExtractedJdkPath = Join-Path $ExtractPath $JdkExtractedDir
    Move-Item -Path $ExtractedJdkPath -Destination $JdkDir -Force
    
    Write-Host "[SUCCESS] GraalVM installed successfully!" -ForegroundColor Green
    
    # Verify installation
    $JavaExe = Join-Path $JdkDir "bin\java.exe"
    & $JavaExe -version
    
    Write-Host ""
    Write-Host "[INFO] Verifying native-image tool..." -ForegroundColor Yellow
    $NativeImageExe = Join-Path $JdkDir "bin\native-image.cmd"
    if (Test-Path $NativeImageExe) {
        Write-Host "[SUCCESS] native-image tool is available" -ForegroundColor Green
    } else {
        Write-Host "[WARNING] native-image tool not found, it should be included in GraalVM" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "[SUCCESS] GraalVM $GraalVMVersion is ready to use at: $JdkDir" -ForegroundColor Green
    Write-Host "   Native Image compilation is now available!" -ForegroundColor Green
    
} finally {
    # Clean up temporary directory
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
    }
}
