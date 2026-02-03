@echo off
REM Script to compile GLSL shaders to SPIR-V
REM This requires glslangValidator to be installed (part of Vulkan SDK)

setlocal

set "SHADER_SRC_DIR=%~dp0shaders\src"
set "SHADER_OUT_DIR=%~dp0shaders\compiled"

echo Compiling shaders...
echo Source directory: %SHADER_SRC_DIR%
echo Output directory: %SHADER_OUT_DIR%

REM Create output directory if it doesn't exist
if not exist "%SHADER_OUT_DIR%" mkdir "%SHADER_OUT_DIR%"

REM Check if glslangValidator is available
where glslangValidator >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: glslangValidator not found!
    echo Please install the Vulkan SDK from https://vulkan.lunarg.com/sdk/home
    echo Make sure the Vulkan SDK bin directory is in your PATH
    exit /b 1
)

REM Compile vertex shader
echo Compiling vertex shader...
glslangValidator -V "%SHADER_SRC_DIR%\vertex.vert" -o "%SHADER_OUT_DIR%\vertex.spv"
if %ERRORLEVEL% NEQ 0 exit /b 1

REM Compile fragment shader
echo Compiling fragment shader...
glslangValidator -V "%SHADER_SRC_DIR%\fragment.frag" -o "%SHADER_OUT_DIR%\fragment.spv"
if %ERRORLEVEL% NEQ 0 exit /b 1

echo Shader compilation complete!
dir "%SHADER_OUT_DIR%"

endlocal
