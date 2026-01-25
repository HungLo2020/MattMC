@echo off
REM MattMC Native Server Launcher
REM This script launches the native executable version of MattMC Server

REM Get the directory containing this script (should be in server directory)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Check for native executable in parent directory
set NATIVE_EXEC=%SCRIPT_DIR%\..\MattMC-Server.exe

if not exist "%NATIVE_EXEC%" (
    echo Error: Native server executable not found at: %NATIVE_EXEC%
    echo.
    echo This appears to be a JAR-based distribution, not a native executable distribution.
    echo Please use the JAR-based launcher instead: run-server.bat
    echo.
    echo To build a native executable distribution, run:
    echo   gradlew nativeServerCompile
    exit /b 1
)

echo [32m🚀 Launching MattMC Native Server...[0m
echo.

REM Launch the native server executable
REM Native executables don't need JVM arguments - they're already compiled!
REM Server runs in headless mode by default
"%NATIVE_EXEC%" --nogui
