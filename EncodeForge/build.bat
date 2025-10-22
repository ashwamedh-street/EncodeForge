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

REM Validate profile for Windows platform
if "%PROFILE%"=="linux-deb" (
    echo ERROR: Cannot build Linux DEB packages on Windows!
    echo Use: build.bat windows-exe or build.bat windows-msi
    pause
    exit /b 1
)
if "%PROFILE%"=="linux-rpm" (
    echo ERROR: Cannot build Linux RPM packages on Windows!
    echo Use: build.bat windows-exe or build.bat windows-msi
    pause
    exit /b 1
)
if "%PROFILE%"=="mac-dmg" (
    echo ERROR: Cannot build macOS DMG packages on Windows!
    echo Use: build.bat windows-exe or build.bat windows-msi
    pause
    exit /b 1
)
if "%PROFILE%"=="mac-dmg-x64" (
    echo ERROR: Cannot build macOS DMG packages on Windows!
    echo Use: build.bat windows-exe or build.bat windows-msi
    pause
    exit /b 1
)
if "%PROFILE%"=="mac-dmg-arm64" (
    echo ERROR: Cannot build macOS DMG packages on Windows!
    echo Use: build.bat windows-exe or build.bat windows-msi
    pause
    exit /b 1
)

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
call .\mvnw.cmd package -P %PROFILE%

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
if "%PROFILE%"=="windows-exe" (
    echo Installer location: target\dist\windows\
) else if "%PROFILE%"=="windows-msi" (
    echo Installer location: target\dist\windows\
) else (
    echo Installer location: target\dist\%PROFILE%\
)
echo.

pause

