@echo off
REM MattMC Client Launcher

REM Get the directory containing this script (should be project root in distribution)
cd /d "%~dp0"
set SCRIPT_DIR=%CD%

REM Use bundled JDK if available, otherwise use system java
set BUNDLED_JAVA=%SCRIPT_DIR%\run\jdk-21\bin\java.exe
if exist "%BUNDLED_JAVA%" (
    set JAVA_CMD=%BUNDLED_JAVA%
    echo Using bundled JDK
) else (
    set JAVA_CMD=java
    echo Using system Java
)

"%JAVA_CMD%" -Xmx2G -Xms512M ^
    -XX:+UseG1GC ^
    -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC ^
    -XX:G1NewSizePercent=30 ^
    -XX:G1MaxNewSizePercent=40 ^
    -XX:G1HeapRegionSize=8M ^
    -XX:G1ReservePercent=20 ^
    -XX:G1HeapWastePercent=5 ^
    -XX:G1MixedGCCountTarget=4 ^
    -XX:InitiatingHeapOccupancyPercent=15 ^
    -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:G1RSetUpdatingPauseTimePercent=5 ^
    -XX:SurvivorRatio=32 ^
    -XX:+PerfDisableSharedMem ^
    -XX:MaxTenuringThreshold=1 ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.minecraft.client.main.Main ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run ^
    --assetsDir run\assets ^
    --assetIndex @ASSET_INDEX@
