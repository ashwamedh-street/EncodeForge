@echo off
REM ===================================================================
REM Build and Package Script for Windows
REM Creates a distributable Java application with bundled Python
REM ===================================================================

echo.
echo ===============================================
echo  FFmpeg Batch Transcoder - Build & Package
echo ===============================================
echo.

REM Step 1: Build Java application with Maven
echo [1/4] Building Java application...
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

REM Step 2: Package Python with PyInstaller
echo.
echo [2/4] Packaging Python backend...
cd src\main\resources\python
if exist "dist" rmdir /s /q dist
if exist "build" rmdir /s /q build

python -m PyInstaller --onefile --name ffmpeg_backend ^
    --add-data "*.py;." ^
    --hidden-import json ^
    --hidden-import pathlib ^
    ffmpeg_batch_transcoder.py

if %ERRORLEVEL% NEQ 0 (
    echo WARNING: PyInstaller failed. Python script will be used directly.
    cd ..\..\..\..
) else (
    REM Copy packaged Python to target/python
    cd ..\..\..\..
    if not exist "target\python" mkdir target\python
    copy src\main\resources\python\dist\ffmpeg_backend.exe target\python\
    copy src\main\resources\python\ffmpeg_batch_transcoder.py target\python\
)

REM Step 3: Create distribution directory
echo.
echo [3/4] Preparing distribution...
if not exist "target\distribution" mkdir target\distribution
copy target\*.jar target\distribution\ffmpeg-transcoder.jar

REM Copy Python runtime
if not exist "target\distribution\python" mkdir target\distribution\python
xcopy /E /I /Y target\python target\distribution\python

REM Step 4: Create launch script
echo.
echo [4/4] Creating launcher...

(
echo @echo off
echo echo Starting FFmpeg Batch Transcoder...
echo java -jar ffmpeg-transcoder.jar
echo pause
) > target\distribution\FFmpeg-Transcoder.bat

echo.
echo ===============================================
echo  Build Complete!
echo ===============================================
echo.
echo Distribution created in: target\distribution
echo.
echo To run: Double-click FFmpeg-Transcoder.bat
echo.
pause

