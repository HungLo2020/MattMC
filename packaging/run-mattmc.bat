@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM MattMC Client Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
set JAVA_VERSION=25

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Require system Java on PATH and enforce Java 25
set JAVA_CMD=java
where %JAVA_CMD% >nul 2>&1
if errorlevel 1 (
    echo Error: Java was not found on PATH.
    echo Please install Java %JAVA_VERSION% and ensure 'java' is available from the command line.
    exit /b 1
)

set "JAVA_OK="
for /f "delims=" %%L in ('"%JAVA_CMD%" -version 2^>^&1') do (
    echo %%L | findstr /R /C:"%JAVA_VERSION%\." >nul && set "JAVA_OK=1"
)

if not defined JAVA_OK (
    echo Error: Java %JAVA_VERSION% is required.
    echo Current java version output:
    "%JAVA_CMD%" -version
    exit /b 1
)

echo Using system Java %JAVA_VERSION%

REM Build classpath dynamically from all jars in lib/ so mixed-platform native jars are safe.
set "CLASSPATH="
for %%F in ("%SCRIPT_DIR%\lib\*.jar") do (
    if not defined CLASSPATH (
        set "CLASSPATH=%%~fF"
    ) else (
        set "CLASSPATH=!CLASSPATH!;%%~fF"
    )
)

if not defined CLASSPATH (
    echo Error: no JAR files found in %SCRIPT_DIR%\lib
    exit /b 1
)

REM Launch the game with Fabric Loader
REM Note: Minecraft classes are included in the main JAR, no separate game JAR needed
REM Note: Assets are loaded directly from JAR classpath - no --assetsDir needed
"%JAVA_CMD%" -Xmx8G -Xms4G ^
    -XX:+UseZGC ^
    -XX:+UseCompactObjectHeaders ^
    -Dfabric.development=true ^
    -cp "!CLASSPATH!" ^
    net.fabricmc.loader.impl.launch.knot.KnotClient ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run
