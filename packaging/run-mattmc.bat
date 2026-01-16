@echo off
REM MattMC Client Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
set JAVA_VERSION=25

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Require bundled JDK - do not fall back to system Java
set BUNDLED_JAVA=%SCRIPT_DIR%\run\jdk-%JAVA_VERSION%\bin\java.exe
if not exist "%BUNDLED_JAVA%" (
    echo Error: Bundled JDK not found at: %BUNDLED_JAVA%
    echo Please ensure the distribution includes the bundled JDK.
    echo To build with bundled JDK, run: gradlew downloadJdk copyJdkToRun clientDist
    exit /b 1
)

set JAVA_CMD=%BUNDLED_JAVA%
echo Using bundled JDK %JAVA_VERSION%

REM Launch the game with Fabric Loader
REM Note: Minecraft classes are included in the main JAR, no separate game JAR needed
REM Note: Assets are loaded directly from JAR classpath - no --assetsDir needed
"%JAVA_CMD%" -Xmx8G -Xms4G ^
    -XX:+UseZGC ^
    -XX:+UseCompactObjectHeaders ^
    -Dfabric.development=true ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.fabricmc.loader.impl.launch.knot.KnotClient ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run
