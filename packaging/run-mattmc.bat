@echo off
REM MattMC Client Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
set JAVA_VERSION=25

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Use bundled JDK if available, otherwise use system java
set BUNDLED_JAVA=%SCRIPT_DIR%\run\jdk-%JAVA_VERSION%\bin\java.exe
if exist "%BUNDLED_JAVA%" (
    set JAVA_CMD=%BUNDLED_JAVA%
    echo Using bundled JDK %JAVA_VERSION%
) else (
    set JAVA_CMD=java
    echo Using system Java (bundled JDK not found)
)

REM Detect Java version - more robust parsing
set DETECTED_JAVA_VERSION=0
for /f "tokens=*" %%a in ('"%JAVA_CMD%" -version 2^>^&1') do (
    echo %%a | findstr /r "version" >nul
    if not errorlevel 1 (
        for /f "tokens=2 delims= " %%b in ("%%a") do (
            set VERSION_STRING=%%b
            goto :parse_version
        )
    )
)

:parse_version
REM Remove quotes from version string
set VERSION_STRING=%VERSION_STRING:"=%
REM Handle both legacy (1.8.0) and modern (11+) version formats
REM Extract major version number
echo %VERSION_STRING% | findstr /r "^1\." >nul
if not errorlevel 1 (
    REM Legacy format like 1.8.0 - extract second number
    for /f "tokens=2 delims=._+" %%c in ("%VERSION_STRING%") do set DETECTED_JAVA_VERSION=%%c
) else (
    REM Modern format like 17.0.1 - extract first number
    for /f "tokens=1 delims=._+" %%c in ("%VERSION_STRING%") do set DETECTED_JAVA_VERSION=%%c
)

REM Validate version is numeric using simpler approach
echo %DETECTED_JAVA_VERSION% | findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo Warning: Could not detect Java version, using basic JVM arguments
    set DETECTED_JAVA_VERSION=0
)

REM Build JVM arguments based on Java version
set JVM_ARGS=-Xmx8G -Xms4G

REM Add garbage collector flags (available in Java 21+)
if %DETECTED_JAVA_VERSION% GEQ 21 (
    set JVM_ARGS=%JVM_ARGS% -XX:+UseZGC -XX:+ZGenerational
)

REM Add Compact Object Headers flag (only available in Java 25+)
if %DETECTED_JAVA_VERSION% GEQ 25 (
    set JVM_ARGS=%JVM_ARGS% -XX:+UseCompactObjectHeaders
    echo Using Java %DETECTED_JAVA_VERSION% with Compact Object Headers enabled
) else if %DETECTED_JAVA_VERSION% GTR 0 (
    echo Warning: Java %DETECTED_JAVA_VERSION% detected. Compact Object Headers requires Java 25+
    echo          Performance may be suboptimal. Consider using the bundled JDK.
)

REM Launch the game with Fabric Loader
REM Note: Minecraft classes are included in the main JAR, no separate game JAR needed
"%JAVA_CMD%" %JVM_ARGS% ^
    -Dfabric.development=true ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.fabricmc.loader.impl.launch.knot.KnotClient ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run ^
    --assetsDir run\assets ^
    --assetIndex @ASSET_INDEX@
