[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("opengl", "vulkan")]
    [string]$Backend,

    [Parameter(Mandatory = $true)]
    [ValidateSet("on", "off")]
    [string]$Shaders,

    [string]$World = "Origin",

    [Parameter(Mandatory = $true)]
    [ValidateRange(15, 3600)]
    [int]$TimeoutSeconds,

    [string]$ArtifactRoot,

    [ValidateRange(1, 120)]
    [int]$MinRenderedFrames = 8,

    [switch]$RequireMeshingReport,

    [ValidateRange(0, 100)]
    [double]$MinNonBlackPixelPercent = 1.0,

    [ValidateRange(1, 20)]
    [int]$CaptureRetries = 6
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $scriptDir = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function New-SafeName {
    param([string]$Value)
    return ($Value -replace '[\\/:*?"<>| ]+', "_").Trim("_")
}

function Write-RunLog {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    if ($script:RunLogPath) {
        Add-Content -LiteralPath $script:RunLogPath -Value $line
    }
}

function Write-TextFile {
    param(
        [string]$Path,
        [string[]]$Lines
    )
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8)
}

function Set-KeyValueLine {
    param(
        [string]$Path,
        [string]$Key,
        [string]$Value
    )

    if (Test-Path -LiteralPath $Path) {
        $lines = [System.IO.File]::ReadAllLines($Path)
    } else {
        $parent = Split-Path -Parent $Path
        if ($parent) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        $lines = @()
    }

    $found = $false
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        if ($line -match ("^\s*" + [regex]::Escape($Key) + "\s*=")) {
            $out.Add("$Key=$Value")
            $found = $true
        } else {
            $out.Add($line)
        }
    }
    if (-not $found) {
        $out.Add("$Key=$Value")
    }

    Write-TextFile -Path $Path -Lines $out.ToArray()
}

function Get-MattMcProcesses {
    param([string]$RepoRoot)

    $root = (Resolve-Path $RepoRoot).Path
    $escapedRoot = [regex]::Escape($root)
    Get-CimInstance Win32_Process | Where-Object {
        $cmd = $_.CommandLine
        $cmd -and
            $_.ProcessId -ne $PID -and
            $cmd -match $escapedRoot -and
            $cmd -match "KnotClient|GradleWrapperMain|GradleDaemon|gradlew\.bat|net\.minecraft\.client"
    }
}

function Stop-ProcessTree {
    param(
        [int]$ProcessId,
        [string]$Reason
    )

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId) -Reason $Reason
    }

    try {
        $process = Get-Process -Id $ProcessId -ErrorAction Stop
        Write-RunLog "Stopping pid $ProcessId ($($process.ProcessName)): $Reason"
        Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
    } catch {
        return
    }
}

function Stop-MattMcProcesses {
    param(
        [string]$RepoRoot,
        [string]$Reason
    )

    $processes = @(Get-MattMcProcesses -RepoRoot $RepoRoot)
    foreach ($proc in $processes) {
        Stop-ProcessTree -ProcessId ([int]$proc.ProcessId) -Reason $Reason
    }
}

function Save-ProcessSnapshot {
    param(
        [string]$RepoRoot,
        [string]$Path
    )

    $processes = @(Get-MattMcProcesses -RepoRoot $RepoRoot | Sort-Object ProcessId)
    if ($processes.Count -eq 0) {
        "No MattMC client/Gradle processes found." | Out-File -LiteralPath $Path -Encoding utf8
        return
    }

    $processes |
        Select-Object ProcessId, ParentProcessId, Name, CommandLine |
        Format-List |
        Out-File -LiteralPath $Path -Encoding utf8
}

function Invoke-GradleStop {
    param(
        [string]$RepoRoot,
        [string]$GradlePath,
        [string]$OutputPath
    )

    try {
        Push-Location $RepoRoot
        & $GradlePath --stop *> $OutputPath
    } catch {
        Add-Content -LiteralPath $OutputPath -Value $_.Exception.Message
    } finally {
        Pop-Location
    }
}

function Read-StatusJson {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    try {
        return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Test-LogForCrash {
    param(
        [string[]]$Paths,
        [datetime]$Since
    )

    $pattern = "---- Minecraft Crash Report ----|A fatal error has been detected|EXCEPTION_ACCESS_VIOLATION|panicked at|fatal runtime error|native panic|hs_err_pid"
    foreach ($path in $Paths) {
        if (Test-Path -LiteralPath $path) {
            try {
                if (Select-String -LiteralPath $path -Pattern $pattern -Quiet -ErrorAction SilentlyContinue) {
                    return "crash or native panic pattern in $path"
                }
            } catch {
            }
        }
    }

    return $null
}

function Copy-RecentFiles {
    param(
        [string]$SourceDir,
        [string]$Pattern,
        [string]$DestDir,
        [datetime]$Since
    )

    if (-not (Test-Path -LiteralPath $SourceDir)) {
        return
    }

    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
    Get-ChildItem -LiteralPath $SourceDir -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $Since } |
        ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $DestDir $_.Name) -Force
        }
}

function Copy-IfExists {
    param(
        [string]$Source,
        [string]$Destination
    )

    if (Test-Path -LiteralPath $Source) {
        $parent = Split-Path -Parent $Destination
        if ($parent) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        Copy-Item -LiteralPath $Source -Destination $Destination -Force
    }
}

function Initialize-WindowCapture {
    if ("MattMcWindowCapture" -as [type]) {
        return
    }

    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class MattMcWindowCapture {
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT {
        public int X;
        public int Y;
    }

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
}
"@
}

function Get-ClientProcessIds {
    param([string]$RepoRoot)

    @(Get-MattMcProcesses -RepoRoot $RepoRoot |
        Where-Object {
            $_.CommandLine -match "KnotClient|net\.minecraft\.client" -and
            $_.CommandLine -notmatch "GradleWrapperMain|GradleDaemon"
        } |
        ForEach-Object { [int]$_.ProcessId })
}

function Find-ClientWindow {
    param([string]$RepoRoot)

    Initialize-WindowCapture
    $clientPids = @(Get-ClientProcessIds -RepoRoot $RepoRoot)
    if ($clientPids.Count -eq 0) {
        return $null
    }

    $windows = New-Object System.Collections.Generic.List[object]
    $callback = [MattMcWindowCapture+EnumWindowsProc] {
        param([IntPtr]$hWnd, [IntPtr]$lParam)

        if (-not [MattMcWindowCapture]::IsWindowVisible($hWnd)) {
            return $true
        }

        $windowProcessId = [uint32]0
        [void][MattMcWindowCapture]::GetWindowThreadProcessId($hWnd, [ref]$windowProcessId)
        if ($script:WindowCaptureClientPids -notcontains [int]$windowProcessId) {
            return $true
        }

        $clientRect = New-Object MattMcWindowCapture+RECT
        if (-not [MattMcWindowCapture]::GetClientRect($hWnd, [ref]$clientRect)) {
            return $true
        }

        $width = $clientRect.Right - $clientRect.Left
        $height = $clientRect.Bottom - $clientRect.Top
        if ($width -lt 320 -or $height -lt 200) {
            return $true
        }

        $origin = New-Object MattMcWindowCapture+POINT
        $origin.X = 0
        $origin.Y = 0
        if (-not [MattMcWindowCapture]::ClientToScreen($hWnd, [ref]$origin)) {
            return $true
        }

        $titleBuilder = New-Object System.Text.StringBuilder 512
        [void][MattMcWindowCapture]::GetWindowText($hWnd, $titleBuilder, $titleBuilder.Capacity)

        $script:WindowCaptureWindows.Add([pscustomobject]@{
            Handle = $hWnd
            ProcessId = [int]$windowProcessId
            Title = $titleBuilder.ToString()
            Left = $origin.X
            Top = $origin.Y
            Width = $width
            Height = $height
        })
        return $true
    }

    $script:WindowCaptureClientPids = $clientPids
    $script:WindowCaptureWindows = $windows
    [void][MattMcWindowCapture]::EnumWindows($callback, [IntPtr]::Zero)
    $script:WindowCaptureClientPids = @()

    return @($windows | Sort-Object @{ Expression = { if ($_.Title -match "Minecraft|MattMC") { 0 } else { 1 } } }, ProcessId | Select-Object -First 1)[0]
}

function Save-ClientWindowScreenshot {
    param(
        [string]$RepoRoot,
        [string]$Path
    )

    Initialize-WindowCapture
    $window = Find-ClientWindow -RepoRoot $RepoRoot
    if (-not $window) {
        throw "Could not find a visible MattMC client window to capture."
    }

    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    $bitmap = $null
    $graphics = $null
    try {
        $bitmap = New-Object System.Drawing.Bitmap $window.Width, $window.Height
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.CopyFromScreen($window.Left, $window.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if ($graphics) {
            $graphics.Dispose()
        }
        if ($bitmap) {
            $bitmap.Dispose()
        }
    }

    return [pscustomobject]@{
        path = $Path
        processId = $window.ProcessId
        title = $window.Title
        left = $window.Left
        top = $window.Top
        width = $window.Width
        height = $window.Height
    }
}

function Test-ScreenshotContent {
    param(
        [string]$Path,
        [double]$MinNonBlackPixelPercent
    )

    $result = [ordered]@{
        path = $Path
        exists = $false
        width = 0
        height = 0
        sampledPixels = 0
        nonBlackPixels = 0
        nonBlackPixelPercent = 0.0
        averageBrightness = 0.0
        minNonBlackPixelPercent = $MinNonBlackPixelPercent
        passed = $false
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]$result
    }

    Add-Type -AssemblyName System.Drawing
    $bitmap = $null
    try {
        $bitmap = [System.Drawing.Bitmap]::new($Path)
        $width = $bitmap.Width
        $height = $bitmap.Height
        $totalPixels = [double]($width * $height)
        $stride = [Math]::Max(1, [int][Math]::Floor([Math]::Sqrt($totalPixels / 250000.0)))
        $samples = 0L
        $nonBlack = 0L
        $brightnessTotal = 0L

        for ($y = 0; $y -lt $height; $y += $stride) {
            for ($x = 0; $x -lt $width; $x += $stride) {
                $pixel = $bitmap.GetPixel($x, $y)
                $brightness = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B))
                if ($pixel.A -gt 0 -and $brightness -ge 12) {
                    $nonBlack++
                }
                $brightnessTotal += $brightness
                $samples++
            }
        }

        $percent = 0.0
        $average = 0.0
        if ($samples -gt 0) {
            $percent = [double]$nonBlack * 100.0 / [double]$samples
            $average = [double]$brightnessTotal / [double]$samples
        }

        $result.exists = $true
        $result.width = $width
        $result.height = $height
        $result.sampledPixels = $samples
        $result.nonBlackPixels = $nonBlack
        $result.nonBlackPixelPercent = $percent
        $result.averageBrightness = $average
        $result.passed = $percent -ge $MinNonBlackPixelPercent
    } finally {
        if ($bitmap) {
            $bitmap.Dispose()
        }
    }

    return [pscustomobject]$result
}

function Test-LogPattern {
    param(
        [string[]]$Paths,
        [string]$Pattern
    )

    foreach ($path in $Paths) {
        if (Test-Path -LiteralPath $path) {
            if (Select-String -LiteralPath $path -Pattern $Pattern -Quiet -ErrorAction SilentlyContinue) {
                return $true
            }
        }
    }

    return $false
}

function Get-ActiveLogPaths {
    param(
        [string]$StdoutPath,
        [string]$StderrPath,
        [string]$LatestLogPath,
        [datetime]$Since
    )

    $paths = New-Object System.Collections.Generic.List[string]
    if (Test-Path -LiteralPath $StdoutPath) {
        $paths.Add($StdoutPath)
    }
    if (Test-Path -LiteralPath $StderrPath) {
        $paths.Add($StderrPath)
    }
    if ((Test-Path -LiteralPath $LatestLogPath) -and (Get-Item -LiteralPath $LatestLogPath).LastWriteTime -ge $Since) {
        $paths.Add($LatestLogPath)
    }

    return $paths.ToArray()
}

function Write-Result {
    param(
        [string]$Path,
        [string]$Result,
        [string]$Reason,
        [int]$ExitCode,
        [object]$WindowCapture,
        [object]$ScreenshotAnalysis,
        [bool]$FallbackReportObserved
    )

    $json = [ordered]@{
        result = $Result
        reason = $Reason
        exitCode = $ExitCode
        backend = $script:Backend
        shaders = $script:Shaders
        world = $script:World
        timeoutSeconds = $script:TimeoutSeconds
        minNonBlackPixelPercent = $script:MinNonBlackPixelPercent
        captureRetries = $script:CaptureRetries
        requireMeshingReport = $script:RequireMeshingReport.IsPresent
        fallbackReportObserved = $FallbackReportObserved
        windowCapture = $WindowCapture
        screenshotAnalysis = $ScreenshotAnalysis
        artifactDir = $script:ArtifactDir
        completedAt = (Get-Date).ToString("o")
    } | ConvertTo-Json -Depth 5

    Set-Content -LiteralPath $Path -Value $json -Encoding utf8
}

$script:Backend = $Backend
$script:Shaders = $Shaders
$script:World = $World
$script:TimeoutSeconds = $TimeoutSeconds
$script:MinNonBlackPixelPercent = $MinNonBlackPixelPercent
$script:CaptureRetries = $CaptureRetries
$script:RequireMeshingReport = $RequireMeshingReport
$script:RunLogPath = $null
$script:ArtifactDir = $null
$exitCode = 1
$result = "failed"
$reason = "unexpected failure"
$repoRoot = $null
$gradlePath = $null
$process = $null
$optionsBytes = $null
$irisBytes = $null
$optionsPath = $null
$irisPath = $null
$oldJavaToolOptions = $null
$windowCapture = $null
$screenshotAnalysis = $null
$fallbackReportObserved = $false
$startTime = Get-Date

try {
    $repoRoot = Resolve-RepoRoot
    $gradlePath = Join-Path $repoRoot "gradlew.bat"
    $buildGradle = Join-Path $repoRoot "build.gradle"
    $runDir = Join-Path $repoRoot "run"

    if (-not (Test-Path -LiteralPath $gradlePath)) {
        throw "This script must be run from the MattMC checkout; gradlew.bat was not found at $gradlePath."
    }
    if (-not (Test-Path -LiteralPath $buildGradle)) {
        throw "This script must be run from the MattMC checkout; build.gradle was not found at $buildGradle."
    }
    if (-not (Test-Path -LiteralPath $runDir)) {
        throw "The MattMC run directory does not exist at $runDir."
    }

    if ([string]::IsNullOrWhiteSpace($ArtifactRoot)) {
        $ArtifactRoot = Join-Path $repoRoot "build\dev-capture"
    }
    $ArtifactRoot = (New-Item -ItemType Directory -Path $ArtifactRoot -Force).FullName

    $runStamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $safeWorld = New-SafeName -Value $World
    $script:ArtifactDir = Join-Path $ArtifactRoot "$runStamp-$Backend-shaders-$Shaders-$safeWorld"
    New-Item -ItemType Directory -Path $script:ArtifactDir -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "logs") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "screenshots") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "config-before") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "config-effective") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "config-after") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $script:ArtifactDir "crash-reports") -Force | Out-Null

    $script:RunLogPath = Join-Path $script:ArtifactDir "run-dev-capture.log"
    Write-RunLog "Artifact directory: $($script:ArtifactDir)"

    $worldPath = Join-Path (Join-Path $runDir "saves") $World
    if (-not (Test-Path -LiteralPath $worldPath)) {
        $exitCode = 2
        $result = "invalid-world"
        $reason = "World '$World' was not found at $worldPath."
        Write-RunLog $reason
        throw $reason
    }

    $optionsPath = Join-Path $runDir "options.txt"
    $irisPath = Join-Path (Join-Path $runDir "config") "iris.properties"
    if (Test-Path -LiteralPath $optionsPath) {
        $optionsBytes = [System.IO.File]::ReadAllBytes($optionsPath)
        Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $script:ArtifactDir "config-before\options.txt") -Force
    }
    if (Test-Path -LiteralPath $irisPath) {
        $irisBytes = [System.IO.File]::ReadAllBytes($irisPath)
        Copy-Item -LiteralPath $irisPath -Destination (Join-Path $script:ArtifactDir "config-before\iris.properties") -Force
    }

    Save-ProcessSnapshot -RepoRoot $repoRoot -Path (Join-Path $script:ArtifactDir "processes-before.txt")
    Write-RunLog "Stopping stale Gradle/client processes before launch."
    Invoke-GradleStop -RepoRoot $repoRoot -GradlePath $gradlePath -OutputPath (Join-Path $script:ArtifactDir "gradle-stop-before.log")
    Stop-MattMcProcesses -RepoRoot $repoRoot -Reason "pre-run cleanup"

    Set-KeyValueLine -Path $optionsPath -Key "graphics_backend" -Value $Backend
    $shaderEnabled = "false"
    if ($Shaders -eq "on") {
        $shaderEnabled = "true"
    }
    Set-KeyValueLine -Path $irisPath -Key "enableShaders" -Value $shaderEnabled
    Set-KeyValueLine -Path $irisPath -Key "shaderPack" -Value "ComplementaryHungLoIfied.zip"
    Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $script:ArtifactDir "config-effective\options.txt") -Force
    Copy-Item -LiteralPath $irisPath -Destination (Join-Path $script:ArtifactDir "config-effective\iris.properties") -Force

    $oldJavaToolOptions = [Environment]::GetEnvironmentVariable("JAVA_TOOL_OPTIONS", "Process")
    if ($RequireMeshingReport) {
        $reportProperty = "-Dmattmc.nativeMeshing.reportFallbacks=true"
        if ([string]::IsNullOrWhiteSpace($oldJavaToolOptions)) {
            [Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", $reportProperty, "Process")
        } elseif ($oldJavaToolOptions -notmatch [regex]::Escape($reportProperty)) {
            [Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", "$oldJavaToolOptions $reportProperty", "Process")
        }
    }

    $stdoutPath = Join-Path $script:ArtifactDir "logs\runClient.stdout.log"
    $stderrPath = Join-Path $script:ArtifactDir "logs\runClient.stderr.log"
    $latestLogPath = Join-Path $runDir "logs\latest.log"

    $quickPlayArg = "--quickPlaySingleplayer=$World"
    $gradleArgs = @("runClient", "--no-daemon", "--args=$quickPlayArg")
    Write-RunLog "Launching .\gradlew.bat $($gradleArgs -join ' ')"
    $startTime = Get-Date
    $process = Start-Process -FilePath $gradlePath `
        -ArgumentList $gradleArgs `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    $deadline = $startTime.AddSeconds($TimeoutSeconds)
    $worldLoaded = $false
    $captureComplete = $false
    $captureAttempts = 0
    $startupSeen = $false
    $crashReason = $null

    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1

        $latestLogIsFresh = $false
        if (Test-Path -LiteralPath $latestLogPath) {
            $latestLogIsFresh = (Get-Item -LiteralPath $latestLogPath).LastWriteTime -ge $startTime
        }
        if (-not $startupSeen -and $latestLogIsFresh) {
            $startupSeen = $true
            Write-RunLog "Startup activity detected."
        }

        $activeLogPaths = Get-ActiveLogPaths -StdoutPath $stdoutPath -StderrPath $stderrPath -LatestLogPath $latestLogPath -Since $startTime
        $crashReason = Test-LogForCrash -Paths $activeLogPaths -Since $startTime
        if ($crashReason) {
            $exitCode = 4
            $result = "crash"
            $reason = $crashReason
            Write-RunLog $reason
            break
        }

        if (-not $worldLoaded -and @(Get-ClientProcessIds -RepoRoot $repoRoot).Count -gt 0 -and (Test-LogPattern -Paths $activeLogPaths -Pattern "Sent player skin to server|Timed out while waiting for the client to load chunks, letting the player into the world anyway")) {
            $worldLoaded = $true
            Write-RunLog "World-load activity detected for '$World'."
        }

        $fallbackReportObserved = Test-LogPattern -Paths $activeLogPaths -Pattern "Native meshing fallback report"

        if ($worldLoaded -and -not $captureComplete -and $captureAttempts -lt $CaptureRetries) {
            $attemptNumber = $captureAttempts + 1
            $attemptPath = Join-Path $script:ArtifactDir ("screenshots\client-attempt-{0}.png" -f $attemptNumber)
            try {
                $windowCapture = Save-ClientWindowScreenshot -RepoRoot $repoRoot -Path $attemptPath
                $captureAttempts = $attemptNumber
                $screenshotAnalysis = Test-ScreenshotContent -Path $attemptPath -MinNonBlackPixelPercent $MinNonBlackPixelPercent
                [ordered]@{
                    capture = $windowCapture
                    analysis = $screenshotAnalysis
                } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $script:ArtifactDir ("screenshots\client-attempt-{0}.analysis.json" -f $attemptNumber)) -Encoding utf8

                if ($screenshotAnalysis.passed) {
                    $captureComplete = $true
                    Write-RunLog "Client-area screenshot accepted on attempt ${attemptNumber}: nonBlackPixelPercent=$([Math]::Round($screenshotAnalysis.nonBlackPixelPercent, 4))."
                } else {
                    Write-RunLog "Client-area screenshot attempt $attemptNumber was too dark: nonBlackPixelPercent=$([Math]::Round($screenshotAnalysis.nonBlackPixelPercent, 4)) < $MinNonBlackPixelPercent."
                }
            } catch {
                Write-RunLog "Client-area screenshot attempt $attemptNumber deferred: $($_.Exception.Message)"
            }
        }

        if ($captureComplete -and (-not $RequireMeshingReport -or $fallbackReportObserved)) {
            $exitCode = 0
            $result = "passed"
            $reason = "World loaded and client-area screenshot contains nonblack rendered content."
            if ($RequireMeshingReport) {
                $reason = "$reason Native meshing fallback report was observed."
            }
            Write-RunLog $reason
            break
        }

        if ($worldLoaded -and -not $captureComplete -and $captureAttempts -ge $CaptureRetries) {
            $exitCode = 5
            $result = "black-screenshot"
            $reason = "All $CaptureRetries client-area screenshot attempts were missing or below the nonblack threshold."
            Write-RunLog $reason
            break
        }

        if ($process.HasExited) {
            if (-not $worldLoaded) {
                $exitCode = 3
                $result = "startup-failed"
                $reason = "Gradle/client exited before world-load activity was detected. Exit code: $($process.ExitCode)"
            } elseif (-not $captureComplete) {
                $exitCode = 5
                $result = "capture-failed"
                $reason = "Gradle/client exited before a nonblack client-area screenshot was captured. Exit code: $($process.ExitCode)"
            } elseif ($RequireMeshingReport -and -not $fallbackReportObserved) {
                $exitCode = 5
                $result = "fallback-report-missing"
                $reason = "Gradle/client exited before a Native meshing fallback report was observed. Exit code: $($process.ExitCode)"
            } elseif ($process.ExitCode -ne 0) {
                $exitCode = 4
                $result = "client-exited"
                $reason = "Gradle/client exited after startup with code $($process.ExitCode)."
            } else {
                $exitCode = 0
                $result = "passed"
                $reason = "Client exited cleanly after reaching rendered state."
            }
            Write-RunLog $reason
            break
        }
    }

    if ($exitCode -eq 1 -and -not $process.HasExited -and -not $crashReason) {
        $finalLogPaths = Get-ActiveLogPaths -StdoutPath $stdoutPath -StderrPath $stderrPath -LatestLogPath $latestLogPath -Since $startTime
        $fallbackReportObserved = Test-LogPattern -Paths $finalLogPaths -Pattern "Native meshing fallback report"
        if ($worldLoaded -and $captureComplete -and (-not $RequireMeshingReport -or $fallbackReportObserved)) {
            $exitCode = 0
            $result = "passed"
            $reason = "World loaded and client-area screenshot contains nonblack rendered content; terminating scheduled run."
            Write-RunLog $reason
        } elseif ($worldLoaded -and $captureComplete -and $RequireMeshingReport -and -not $fallbackReportObserved) {
            $exitCode = 5
            $result = "fallback-report-missing"
            $reason = "Timed out after $TimeoutSeconds seconds after capture succeeded, but no Native meshing fallback report was observed."
            Write-RunLog $reason
        } elseif ($worldLoaded) {
            $exitCode = 5
            $result = "capture-failed"
            $reason = "Timed out after $TimeoutSeconds seconds before a nonblack client-area screenshot was captured."
            Write-RunLog $reason
        } else {
            $exitCode = 124
            $result = "timeout"
            $reason = "Timed out after $TimeoutSeconds seconds before world-load activity was detected."
            Write-RunLog $reason
        }
    }
} catch {
    if ($exitCode -eq 1 -and $result -eq "failed") {
        $reason = $_.Exception.Message
    }
    if ($script:RunLogPath) {
        Write-RunLog "Failure: $reason"
    } else {
        Write-Host "Failure: $reason"
    }
} finally {
    if ($null -ne $oldJavaToolOptions) {
        [Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", $oldJavaToolOptions, "Process")
    } elseif ($RequireMeshingReport) {
        [Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", $null, "Process")
    }

    if ($process -and -not $process.HasExited) {
        Stop-ProcessTree -ProcessId $process.Id -Reason "scheduled run completion or failure"
    }

    if ($repoRoot -and $gradlePath -and (Test-Path -LiteralPath $gradlePath)) {
        Invoke-GradleStop -RepoRoot $repoRoot -GradlePath $gradlePath -OutputPath (Join-Path $script:ArtifactDir "gradle-stop-after.log")
        Stop-MattMcProcesses -RepoRoot $repoRoot -Reason "post-run cleanup"
    }

    if ($repoRoot -and $script:ArtifactDir) {
        $runDir = Join-Path $repoRoot "run"
        Copy-IfExists -Source (Join-Path $runDir "logs\latest.log") -Destination (Join-Path $script:ArtifactDir "logs\latest.log")
        Copy-IfExists -Source (Join-Path $runDir "logs\debug.log") -Destination (Join-Path $script:ArtifactDir "logs\debug.log")
        Copy-RecentFiles -SourceDir (Join-Path $runDir "crash-reports") -Pattern "*.txt" -DestDir (Join-Path $script:ArtifactDir "crash-reports") -Since $startTime
        Copy-RecentFiles -SourceDir $runDir -Pattern "hs_err_pid*.log" -DestDir (Join-Path $script:ArtifactDir "crash-reports") -Since $startTime
        Copy-RecentFiles -SourceDir $repoRoot -Pattern "hs_err_pid*.log" -DestDir (Join-Path $script:ArtifactDir "crash-reports") -Since $startTime
        Save-ProcessSnapshot -RepoRoot $repoRoot -Path (Join-Path $script:ArtifactDir "processes-after.txt")
    }

    if ($optionsPath -and $optionsBytes) {
        [System.IO.File]::WriteAllBytes($optionsPath, $optionsBytes)
    }
    if ($irisPath -and $irisBytes) {
        [System.IO.File]::WriteAllBytes($irisPath, $irisBytes)
    }
    if ($optionsPath -and (Test-Path -LiteralPath $optionsPath) -and $script:ArtifactDir) {
        Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $script:ArtifactDir "config-after\options.txt") -Force
    }
    if ($irisPath -and (Test-Path -LiteralPath $irisPath) -and $script:ArtifactDir) {
        Copy-Item -LiteralPath $irisPath -Destination (Join-Path $script:ArtifactDir "config-after\iris.properties") -Force
    }

    if ($script:ArtifactDir) {
        $fallbackReportObserved = Test-LogPattern -Paths @(
            (Join-Path $script:ArtifactDir "logs\runClient.stdout.log"),
            (Join-Path $script:ArtifactDir "logs\runClient.stderr.log"),
            (Join-Path $script:ArtifactDir "logs\latest.log")
        ) -Pattern "Native meshing fallback report"
        Write-Result -Path (Join-Path $script:ArtifactDir "result.json") -Result $result -Reason $reason -ExitCode $exitCode -WindowCapture $windowCapture -ScreenshotAnalysis $screenshotAnalysis -FallbackReportObserved $fallbackReportObserved
        Write-RunLog "Result: $result ($exitCode) - $reason"
    }
}

exit $exitCode
