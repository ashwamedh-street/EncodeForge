@echo off
REM Main build script for Encode Forge
REM Usage: build.bat [target]
REM   target: jar, windows, linux, mac, all, dev
REM   Default: all

setlocal enabledelayedexpansion

set BUILD_TARGET=%1
if "%BUILD_TARGET%"=="" set BUILD_TARGET=all

echo ========================================
echo Encode Forge Build System
echo Target: %BUILD_TARGET%
echo ========================================

set PROJECT_DIR=%~dp0
set OUTPUT_DIR=%PROJECT_DIR%target
set DIST_DIR=%OUTPUT_DIR%\dist

echo Project directory: %PROJECT_DIR%
echo Output directory: %OUTPUT_DIR%

REM Check build target and execute accordingly
if "%BUILD_TARGET%"=="dev" goto :dev_mode
if "%BUILD_TARGET%"=="jar" goto :build_jar
if "%BUILD_TARGET%"=="windows" goto :build_windows
if "%BUILD_TARGET%"=="linux" goto :build_linux
if "%BUILD_TARGET%"=="mac" goto :build_mac
if "%BUILD_TARGET%"=="all" goto :build_all
if "%BUILD_TARGET%"=="clean" goto :clean_only

echo Invalid target: %BUILD_TARGET%
echo Valid targets: jar, windows, linux, mac, all, dev, clean
pause
exit /b 1

:clean_only
echo Cleaning build directory...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
echo Clean complete.
goto :end

:dev_mode
echo Starting development mode...
cd "%PROJECT_DIR%"
call mvnw.cmd javafx:run
goto :end

:build_jar
call :setup_build
call :build_python_bundle
call :build_java
goto :show_results

:build_windows
call :setup_build
call :build_python_bundle
call :build_java
call :create_windows_exe
call :create_portable_launcher
goto :show_results

:build_linux
call :setup_build
call :build_python_bundle
call :build_java
call :create_linux_appimage
goto :show_results

:build_mac
call :setup_build
call :build_python_bundle
call :build_java
call :create_mac_dmg
goto :show_results

:build_all
call :setup_build
call :build_python_bundle
call :build_java
call :create_windows_exe
call :create_linux_appimage
call :create_mac_dmg
call :create_portable_launcher
goto :show_results

REM ========================================
REM Build Functions
REM ========================================

:setup_build
echo.
echo Setting up build environment...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"
exit /b 0

:build_python_bundle
echo.
echo Building Python runtime bundle...
call "%PROJECT_DIR%build-python-bundle.bat"
if errorlevel 1 (
    echo ERROR: Python bundle creation failed
    pause
    exit /b 1
)
exit /b 0

:build_java
echo.
echo Building Java application...
cd "%PROJECT_DIR%"
call mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo ERROR: Java application build failed
    pause
    exit /b 1
)
exit /b 0

:create_windows_exe
echo.
echo Creating Windows executable...
call mvnw.cmd jpackage:jpackage@jpackage-windows
if errorlevel 1 (
    echo WARNING: Windows executable creation failed
) else (
    echo SUCCESS: Windows executable created
)
exit /b 0

:create_linux_appimage
echo.
echo Creating Linux AppImage...
call mvnw.cmd jpackage:jpackage@jpackage-linux
if errorlevel 1 (
    echo WARNING: Linux AppImage creation failed (may require Linux environment)
) else (
    echo SUCCESS: Linux AppImage created
)
exit /b 0

:create_mac_dmg
echo.
echo Creating macOS DMG...
call mvnw.cmd jpackage:jpackage@jpackage-mac
if errorlevel 1 (
    echo WARNING: macOS DMG creation failed (may require macOS environment)
) else (
    echo SUCCESS: macOS DMG created
)
exit /b 0

:create_portable_launcher
echo.
echo Creating portable launcher...
set LAUNCHER_DIR=%OUTPUT_DIR%\launcher
if exist "%LAUNCHER_DIR%" rmdir /s /q "%LAUNCHER_DIR%"
mkdir "%LAUNCHER_DIR%"

REM Create batch launcher
(
echo @echo off
echo cd /d "%%~dp0"
echo java -jar encodeforge-1.0.0.jar %%*
) > "%LAUNCHER_DIR%\Encode Forge.bat"

REM Copy JAR to launcher directory
copy "%OUTPUT_DIR%\encodeforge-1.0.0.jar" "%LAUNCHER_DIR%\" >nul

echo SUCCESS: Portable launcher created
exit /b 0

:show_results
echo.
echo ========================================
echo Build Complete!
echo ========================================
echo.
echo Generated files:

if exist "%OUTPUT_DIR%\encodeforge-1.0.0.jar" (
    echo   - Universal JAR: %OUTPUT_DIR%\encodeforge-1.0.0.jar
)

if exist "%DIST_DIR%\windows\" (
    echo   - Windows: %DIST_DIR%\windows\
    dir "%DIST_DIR%\windows\" /b 2>nul
)

if exist "%DIST_DIR%\linux\" (
    echo   - Linux: %DIST_DIR%\linux\
    dir "%DIST_DIR%\linux\" /b 2>nul
)

if exist "%DIST_DIR%\mac\" (
    echo   - macOS: %DIST_DIR%\mac\
    dir "%DIST_DIR%\mac\" /b 2>nul
)

if exist "%OUTPUT_DIR%\launcher\" (
    echo   - Portable Launcher: %OUTPUT_DIR%\launcher\
    dir "%OUTPUT_DIR%\launcher\" /b 2>nul
)

echo.
echo Python runtime location: %OUTPUT_DIR%\python-runtime
echo.
echo Usage:
echo   Windows: EncodeForge.exe (installer) or "Encode Forge.bat" (portable)
echo   Linux: EncodeForge (AppImage)
echo   macOS: Encode Forge.app (in DMG)
echo   Universal: java -jar encodeforge-1.0.0.jar
echo.

:end
pause
