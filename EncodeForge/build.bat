@echo off
REM EncodeForge Build Script for Windows
REM Creates Windows EXE installer by default

echo =====================================
echo EncodeForge Build Script
echo =====================================
echo.

REM Check for profile argument
set PROFILE=windows-exe
if not "%1"=="" set PROFILE=%1

echo Building with profile: %PROFILE%
echo.

REM Navigate to script directory
cd /d "%~dp0"

REM Clean and package
echo Step 1/2: Building JAR...
call .\mvnw.cmd clean package -DskipTests

if errorlevel 1 (
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

echo.
echo Step 2/2: Creating installer with profile %PROFILE%...
call .\mvnw.cmd jpackage:jpackage@jpackage-%PROFILE%

if errorlevel 1 (
    echo ERROR: Installer creation failed!
    pause
    exit /b 1
)

echo.
echo =====================================
echo Build Complete!
echo =====================================
echo.
echo Installer location: target\dist\windows\
echo.

pause

