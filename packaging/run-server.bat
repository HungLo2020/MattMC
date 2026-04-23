@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM MattMC Server Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
set JAVA_VERSION=25

REM Get the directory containing this script (should be in server directory)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Require bundled JDK - located in parent run directory
set BUNDLED_JAVA=%SCRIPT_DIR%\..\run\jdk-%JAVA_VERSION%\bin\java.exe
if not exist "%BUNDLED_JAVA%" (
    echo Error: Bundled JDK not found at: %BUNDLED_JAVA%
    echo Please ensure the distribution includes the bundled JDK.
    exit /b 1
)

set JAVA_CMD=%BUNDLED_JAVA%
echo Using bundled JDK %JAVA_VERSION%

REM Build classpath dynamically from all jars in ..\lib so mixed-platform native jars are safe.
set "CLASSPATH="
for %%F in ("%SCRIPT_DIR%\..\lib\*.jar") do (
    if not defined CLASSPATH (
        set "CLASSPATH=%%~fF"
    ) else (
        set "CLASSPATH=!CLASSPATH!;%%~fF"
    )
)

if not defined CLASSPATH (
    echo Error: no JAR files found in %SCRIPT_DIR%\..\lib
    exit /b 1
)

REM Launch the dedicated server
REM Note: Server runs in headless mode by default (--nogui)
REM Remove --nogui to run with GUI
"%JAVA_CMD%" -Xmx2G -Xms1G ^
    -XX:+UseZGC ^
    -XX:+UseCompactObjectHeaders ^
    -cp "!CLASSPATH!" ^
    net.minecraft.server.Main ^
    --nogui
