@echo off
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

REM Launch the dedicated server
REM Note: Server runs in headless mode by default (--nogui)
REM Remove --nogui to run with GUI
"%JAVA_CMD%" -Xmx2G -Xms1G ^
    -XX:+UseZGC ^
    -XX:+UseCompactObjectHeaders ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.minecraft.server.Main ^
    --nogui
