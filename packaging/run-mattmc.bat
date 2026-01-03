@echo off
REM MattMC Client Launcher

REM Java version used in this distribution
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
    echo Using system Java
)

REM Launch the game with Fabric Loader
REM Note: Minecraft classes are included in the main JAR, no separate game JAR needed
"%JAVA_CMD%" -Xmx8G -Xms4G ^
    -XX:+UseZGC ^
    -XX:+ZGenerational ^
    -XX:+UseCompactObjectHeaders ^
    -Dfabric.development=true ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.fabricmc.loader.impl.launch.knot.KnotClient ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run ^
    --assetsDir run\assets ^
    --assetIndex @ASSET_INDEX@
