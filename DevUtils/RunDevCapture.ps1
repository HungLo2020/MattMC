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
    [int]$MinRenderedFrames = 8
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

function Write-Result {
    param(
        [string]$Path,
        [string]$Result,
        [string]$Reason,
        [int]$ExitCode,
        [object]$Status
    )

    $actualWorld = $null
    $backendName = $null
    $screenshot = $null
    if ($Status) {
        $actualWorld = $Status.actualWorld
        $backendName = $Status.backend
        $screenshot = $Status.screenshot
    }

    $json = [ordered]@{
        result = $Result
        reason = $Reason
        exitCode = $ExitCode
        backend = $script:Backend
        shaders = $script:Shaders
        world = $script:World
        actualWorld = $actualWorld
        clientBackend = $backendName
        timeoutSeconds = $script:TimeoutSeconds
        screenshot = $screenshot
        artifactDir = $script:ArtifactDir
        completedAt = (Get-Date).ToString("o")
    } | ConvertTo-Json -Depth 5

    Set-Content -LiteralPath $Path -Value $json -Encoding utf8
}

$script:Backend = $Backend
$script:Shaders = $Shaders
$script:World = $World
$script:TimeoutSeconds = $TimeoutSeconds
$script:RunLogPath = $null
$script:ArtifactDir = $null
$exitCode = 1
$result = "failed"
$reason = "unexpected failure"
$repoRoot = $null
$gradlePath = $null
$process = $null
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$oldRunCapture = $env:MATTMC_DEV_RUN_CAPTURE
$oldRunCaptureWorld = $env:MATTMC_DEV_RUN_CAPTURE_WORLD
$oldRunCaptureStatus = $env:MATTMC_DEV_RUN_CAPTURE_STATUS
$oldRunCaptureScreenshot = $env:MATTMC_DEV_RUN_CAPTURE_SCREENSHOT
$oldRunCaptureFrames = $env:MATTMC_DEV_RUN_CAPTURE_MIN_RENDERED_FRAMES
$optionsBytes = $null
$irisBytes = $null
$optionsPath = $null
$irisPath = $null
$status = $null
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

    $statusPath = Join-Path $script:ArtifactDir "run-capture-status.json"
    $screenshotPath = Join-Path $script:ArtifactDir "screenshots\rendered.png"
    $stdoutPath = Join-Path $script:ArtifactDir "logs\runClient.stdout.log"
    $stderrPath = Join-Path $script:ArtifactDir "logs\runClient.stderr.log"
    $latestLogPath = Join-Path $runDir "logs\latest.log"

    $toolOptions = @("-Dmattmc.dev.runCapture=true")
    if (-not [string]::IsNullOrWhiteSpace($oldJavaToolOptions)) {
        $env:JAVA_TOOL_OPTIONS = "$oldJavaToolOptions $($toolOptions -join ' ')"
    } else {
        $env:JAVA_TOOL_OPTIONS = $toolOptions -join " "
    }
    $env:MATTMC_DEV_RUN_CAPTURE = "true"
    $env:MATTMC_DEV_RUN_CAPTURE_WORLD = $World
    $env:MATTMC_DEV_RUN_CAPTURE_STATUS = $statusPath
    $env:MATTMC_DEV_RUN_CAPTURE_SCREENSHOT = $screenshotPath
    $env:MATTMC_DEV_RUN_CAPTURE_MIN_RENDERED_FRAMES = [string]$MinRenderedFrames

    $gradleArgs = @("runClient", "--no-daemon")
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
    $ready = $false
    $startupSeen = $false
    $crashReason = $null

    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1

        $latestLogIsFresh = $false
        if (Test-Path -LiteralPath $latestLogPath) {
            $latestLogIsFresh = (Get-Item -LiteralPath $latestLogPath).LastWriteTime -ge $startTime
        }
        if (-not $startupSeen -and ($latestLogIsFresh -or (Test-Path -LiteralPath $statusPath))) {
            $startupSeen = $true
            Write-RunLog "Startup activity detected."
        }

        $crashReason = Test-LogForCrash -Paths @($stdoutPath, $stderrPath, $latestLogPath) -Since $startTime
        if ($crashReason) {
            $exitCode = 4
            $result = "crash"
            $reason = $crashReason
            Write-RunLog $reason
            break
        }

        $status = Read-StatusJson -Path $statusPath
        if ($status -and $status.status -eq "failed") {
            $exitCode = 4
            $result = "failed"
            $reason = "development capture hook failed: $($status.reason)"
            Write-RunLog $reason
            break
        }

        if ($status -and $status.status -eq "rendered") {
            $actualWorld = [string]$status.actualWorld
            if ($actualWorld -and $actualWorld -ne $World) {
                $exitCode = 3
                $result = "world-load-failed"
                $reason = "Loaded world '$actualWorld' did not match requested world '$World'."
                Write-RunLog $reason
                break
            }
            if (-not $ready) {
                $ready = $true
                Write-RunLog "World '$World' reached usable rendered state."
            }
        }

        if ($process.HasExited) {
            $status = Read-StatusJson -Path $statusPath
            if (-not $ready) {
                $exitCode = 3
                $result = "startup-failed"
                $reason = "Gradle/client exited before the world reached a rendered state. Exit code: $($process.ExitCode)"
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

    if (-not $process.HasExited -and -not $crashReason) {
        if ($ready) {
            $exitCode = 0
            $result = "passed"
            $reason = "World reached rendered state and remained alive for $TimeoutSeconds seconds; terminating scheduled run."
            Write-RunLog $reason
        } else {
            $exitCode = 124
            $result = "timeout"
            $reason = "Timed out after $TimeoutSeconds seconds before the world reached a rendered state."
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

    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
    $env:MATTMC_DEV_RUN_CAPTURE = $oldRunCapture
    $env:MATTMC_DEV_RUN_CAPTURE_WORLD = $oldRunCaptureWorld
    $env:MATTMC_DEV_RUN_CAPTURE_STATUS = $oldRunCaptureStatus
    $env:MATTMC_DEV_RUN_CAPTURE_SCREENSHOT = $oldRunCaptureScreenshot
    $env:MATTMC_DEV_RUN_CAPTURE_MIN_RENDERED_FRAMES = $oldRunCaptureFrames

    $finalStatus = $null
    if ($script:ArtifactDir) {
        $statusPath = Join-Path $script:ArtifactDir "run-capture-status.json"
        $finalStatus = Read-StatusJson -Path $statusPath
        Write-Result -Path (Join-Path $script:ArtifactDir "result.json") -Result $result -Reason $reason -ExitCode $exitCode -Status $finalStatus
        Write-RunLog "Result: $result ($exitCode) - $reason"
    }
}

exit $exitCode
