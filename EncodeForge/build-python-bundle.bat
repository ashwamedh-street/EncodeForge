@echo off
REM Build script to bundle Python runtime and dependencies for EncodeForge
REM This script creates a portable Python environment with all required packages

setlocal enabledelayedexpansion

echo ========================================
echo Encode Forge Python Bundle Builder
echo ========================================

REM Configuration
set PYTHON_VERSION=3.12
set BUNDLE_DIR=python-bundle
set REQUIREMENTS_FILE=requirements.txt
set PYTHON_SCRIPTS_DIR=src\main\resources\python
set OUTPUT_DIR=target\python-runtime
set FFMPEG_OUTPUT_DIR=target\ffmpeg-runtime

REM Check if Python 3.12 is available first
py -3.12 --version >nul 2>&1
if not errorlevel 1 (
    echo Found Python 3.12, using it for compatibility
    set PYTHON_CMD=py -3.12
    goto :python_found
)

REM Check if Python 3.13 is available
py -3.13 --version >nul 2>&1
if not errorlevel 1 (
    echo Found Python 3.13, using it for compatibility
    set PYTHON_CMD=py -3.13
    goto :python_found
)

REM Fallback to default python
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.12 or 3.13 and try again
    pause
    exit /b 1
)

REM Check Python version compatibility
for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYTHON_VER=%%i
echo Detected Python version: %PYTHON_VER%

REM Check if Python version is compatible (3.10 to 3.13)
python -c "import sys; ver = sys.version_info; exit(0 if (3,10) <= ver < (3,14) else 1)" >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python %PYTHON_VER% is not compatible
    echo Please install Python 3.12 or 3.13
    pause
    exit /b 1
)
set PYTHON_CMD=python

:python_found
%PYTHON_CMD% --version
echo Using Python command: %PYTHON_CMD%

echo Creating Python bundle directory...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

echo Installing Python packages...
%PYTHON_CMD% -m venv "%OUTPUT_DIR%\venv"
call "%OUTPUT_DIR%\venv\Scripts\activate.bat"

REM Install packages from requirements
if exist "%REQUIREMENTS_FILE%" (
    echo Installing packages from %REQUIREMENTS_FILE%...
    pip install -r "%REQUIREMENTS_FILE%" --no-cache-dir
) else (
    echo Installing default packages...
    pip install streamlit pandas requests --no-cache-dir
    echo Installing whisper with compatible numba...
    pip install "numba>=0.58.0,<0.63.0" --no-cache-dir
    pip install openai-whisper --no-cache-dir
)

REM Copy Python scripts
echo Copying Python scripts...
if exist "%PYTHON_SCRIPTS_DIR%" (
    xcopy "%PYTHON_SCRIPTS_DIR%\*" "%OUTPUT_DIR%\scripts\" /Y /I
) else (
    echo WARNING: Python scripts directory not found: %PYTHON_SCRIPTS_DIR%
)

REM Create launcher script
echo Creating Python launcher...
(
echo @echo off
echo cd /d "%%~dp0"
echo call venv\Scripts\activate.bat
echo python scripts\ffmpeg_manager.py %%*
) > "%OUTPUT_DIR%\run-python.bat"

REM Create requirements.txt for the bundle
echo Creating bundle requirements...
pip freeze > "%OUTPUT_DIR%\requirements.txt"

REM Create platform-specific Python executable wrapper
echo Creating Python executable wrapper...
(
echo @echo off
echo set PYTHONPATH=%%~dp0scripts
echo call venv\Scripts\activate.bat
echo python scripts\ffmpeg_manager.py %%*
) > "%OUTPUT_DIR%\python_backend.bat"

REM Create a proper executable wrapper (batch file with .exe extension for compatibility)
copy "%OUTPUT_DIR%\python_backend.bat" "%OUTPUT_DIR%\python_backend.exe" >nul

REM Download and package FFmpeg
echo.
echo ========================================
echo Downloading and packaging FFmpeg...
echo ========================================

if exist "%FFMPEG_OUTPUT_DIR%" rmdir /s /q "%FFMPEG_OUTPUT_DIR%"
mkdir "%FFMPEG_OUTPUT_DIR%"

REM Download FFmpeg for Windows
echo Downloading FFmpeg for Windows...
set FFMPEG_URL=https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
set FFMPEG_ZIP=%FFMPEG_OUTPUT_DIR%\ffmpeg.zip

REM Use PowerShell to download FFmpeg
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%FFMPEG_URL%' -OutFile '%FFMPEG_ZIP%'}"

if exist "%FFMPEG_ZIP%" (
    echo Extracting FFmpeg...
    powershell -Command "Expand-Archive -Path '%FFMPEG_ZIP%' -DestinationPath '%FFMPEG_OUTPUT_DIR%' -Force"
    
    REM Find the extracted FFmpeg directory and move contents to root
    for /d %%i in ("%FFMPEG_OUTPUT_DIR%\ffmpeg-*") do (
        echo Moving FFmpeg files from %%i...
        move "%%i\bin\*" "%FFMPEG_OUTPUT_DIR%\" >nul 2>&1
        rmdir /s /q "%%i" >nul 2>&1
    )
    
    REM Clean up zip file
    del "%FFMPEG_ZIP%" >nul 2>&1
    
    echo FFmpeg packaged successfully!
    echo FFmpeg files:
    dir "%FFMPEG_OUTPUT_DIR%\*.exe" /b
) else (
    echo WARNING: Failed to download FFmpeg. You may need to install it manually.
)

echo.
echo ========================================
echo Python bundle created successfully!
echo Location: %OUTPUT_DIR%
echo FFmpeg location: %FFMPEG_OUTPUT_DIR%
echo ========================================
echo.
echo Bundle contents:
dir "%OUTPUT_DIR%" /b

echo.
echo Next steps:
echo 1. Run 'mvn clean package' to build the Java application
echo 2. The Python bundle and FFmpeg will be included in the JAR
echo.
pause
