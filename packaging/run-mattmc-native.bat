@echo off
REM MattMC Native Client Launcher
REM This script launches the native executable version of MattMC

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Check for native executable
set NATIVE_EXEC=%SCRIPT_DIR%\MattMC.exe

if not exist "%NATIVE_EXEC%" (
    echo Error: Native executable not found at: %NATIVE_EXEC%
    echo.
    echo This appears to be a JAR-based distribution, not a native executable distribution.
    echo Please use the JAR-based launcher instead: run-mattmc.bat
    echo.
    echo To build a native executable distribution, run:
    echo   gradlew nativeClientDist
    exit /b 1
)

echo [32m🚀 Launching MattMC Native Client...[0m
echo.

REM Launch the native executable
REM Native executables don't need JVM arguments - they're already compiled!
"%NATIVE_EXEC%" --version @VERSION@ --accessToken 0 --gameDir run
