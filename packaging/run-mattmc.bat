@echo off
REM MattMC Client Launcher

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Read Java version from gradle.properties if it exists
set JAVA_VERSION=25
if exist gradle.properties (
    for /f "tokens=2 delims==" %%a in ('findstr "^java_version=" gradle.properties') do set JAVA_VERSION=%%a
)

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
