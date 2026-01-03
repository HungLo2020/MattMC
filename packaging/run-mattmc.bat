@echo off
REM MattMC Client Launcher

REM Java version used in this distribution
REM Note: Hardcoded because gradle.properties is not included in distributions
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
    echo Using system Java (bundled JDK not found)
)

REM Detect Java version
for /f "tokens=3" %%a in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%a
    goto :version_found
)
:version_found
set JAVA_VERSION_STRING=%JAVA_VERSION_STRING:"=%
for /f "delims=. tokens=1" %%a in ("%JAVA_VERSION_STRING%") do set DETECTED_JAVA_VERSION=%%a

REM Build JVM arguments based on Java version
set JVM_ARGS=-Xmx8G -Xms4G

REM Add garbage collector flags (available in Java 21+)
if %DETECTED_JAVA_VERSION% GEQ 21 (
    set JVM_ARGS=%JVM_ARGS% -XX:+UseZGC -XX:+ZGenerational
)

REM Add Compact Object Headers flag (only available in Java 25+)
if %DETECTED_JAVA_VERSION% GEQ 25 (
    set JVM_ARGS=%JVM_ARGS% -XX:+UseCompactObjectHeaders
    echo Using Java %DETECTED_JAVA_VERSION% with Compact Object Headers enabled
) else (
    echo Warning: Java %DETECTED_JAVA_VERSION% detected. Compact Object Headers requires Java 25+
    echo          Performance may be suboptimal. Consider using the bundled JDK.
)

REM Launch the game with Fabric Loader
REM Note: Minecraft classes are included in the main JAR, no separate game JAR needed
"%JAVA_CMD%" %JVM_ARGS% ^
    -Dfabric.development=true ^
    -cp "@CLASSPATH_WINDOWS@" ^
    net.fabricmc.loader.impl.launch.knot.KnotClient ^
    --version @VERSION@ ^
    --accessToken 0 ^
    --gameDir run ^
    --assetsDir run\assets ^
    --assetIndex @ASSET_INDEX@
