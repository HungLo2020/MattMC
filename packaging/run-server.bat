@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM MattMC Server Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
set JAVA_VERSION=25

REM This script lives in MattMC\run\server, which is also the server's working
REM directory: server.properties and the world are created here.
cd /d "%~dp0"
set SCRIPT_DIR=%CD%
REM The install root holds lib\ and natives\; run\ holds the bundled JDK.
for %%I in ("%SCRIPT_DIR%\..") do set "RUN_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "INSTALL_DIR=%%~fI"

REM Prefer bundled platform-specific JDK in distribution
set "JAVA_CMD="
for %%J in (
    "%RUN_DIR%\jdk\win-x64\bin\java.exe"
    "%RUN_DIR%\jdk\win-aarch64\bin\java.exe"
) do (
    if not defined JAVA_CMD if exist %%~J set "JAVA_CMD=%%~J"
)

if not defined JAVA_CMD (
    echo Error: Bundled JDK not found.
    echo Expected one of:
    echo   %RUN_DIR%\jdk\win-x64\bin\java.exe
    echo   %RUN_DIR%\jdk\win-aarch64\bin\java.exe
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

echo Using bundled JDK: %JAVA_CMD%

REM Build classpath dynamically from all jars in lib\ so mixed-platform native jars are safe.
set "CLASSPATH="
for %%F in ("%INSTALL_DIR%\lib\*.jar") do (
    if not defined CLASSPATH (
        set "CLASSPATH=%%~fF"
    ) else (
        set "CLASSPATH=!CLASSPATH!;%%~fF"
    )
)

if not defined CLASSPATH (
    echo Error: no JAR files found in %INSTALL_DIR%\lib
    exit /b 1
)

REM Launch the dedicated server
REM Note: Server runs in headless mode by default (--nogui)
REM Remove --nogui to run with GUI
"%JAVA_CMD%" -Xmx2G -Xms1G ^
    -XX:+UseZGC ^
    -XX:+UseCompactObjectHeaders ^
    --enable-native-access=ALL-UNNAMED ^
    -Dmattmc.rust.natives.dir="%INSTALL_DIR%\natives" ^
    -cp "!CLASSPATH!" ^
    net.minecraft.server.Main ^
    --nogui
