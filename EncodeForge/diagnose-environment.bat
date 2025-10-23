@echo off
REM EncodeForge Environment Diagnostic Tool
REM Checks all requirements before launching

setlocal enabledelayedexpansion

echo ================================================
echo EncodeForge Environment Diagnostic Tool
echo ================================================
echo.

REM Create a log file
set LOGFILE=%TEMP%\encodeforge-diagnostics.txt
echo EncodeForge Diagnostics - %DATE% %TIME% > "%LOGFILE%"
echo ================================================ >> "%LOGFILE%"
echo. >> "%LOGFILE%"

echo [1/8] Checking Java...
echo. >> "%LOGFILE%"
echo [Java] >> "%LOGFILE%"
java -version 2>> "%LOGFILE%"
if %ERRORLEVEL% NEQ 0 (
    echo   [ERROR] Java not found!
    echo   ERROR: Java not found >> "%LOGFILE%"
    set JAVA_OK=0
) else (
    echo   [OK] Java found
    set JAVA_OK=1
)
echo. >> "%LOGFILE%"

echo [2/8] Checking JavaFX availability...
echo [JavaFX Test] >> "%LOGFILE%"
java -cp target\encodeforge-0.4.0.jar --list-modules 2>> "%LOGFILE%" | findstr "javafx" >> "%LOGFILE%"
if %ERRORLEVEL% NEQ 0 (
    echo   [WARNING] JavaFX modules may not be available
    echo   WARNING: JavaFX not detected >> "%LOGFILE%"
) else (
    echo   [OK] JavaFX detected
)
echo. >> "%LOGFILE%"

echo [3/8] Checking Python...
echo [Python] >> "%LOGFILE%"
python --version 2>> "%LOGFILE%"
if %ERRORLEVEL% NEQ 0 (
    python3 --version 2>> "%LOGFILE%"
    if !ERRORLEVEL! NEQ 0 (
        echo   [WARNING] Python not found in PATH
        echo   WARNING: Python not in PATH >> "%LOGFILE%"
        set PYTHON_OK=0
    ) else (
        echo   [OK] Python3 found
        set PYTHON_OK=1
    )
) else (
    echo   [OK] Python found
    set PYTHON_OK=1
)
echo. >> "%LOGFILE%"

echo [4/8] Checking EncodeForge JAR...
echo [JAR File] >> "%LOGFILE%"
if exist "target\encodeforge-0.4.0.jar" (
    echo   [OK] JAR file found
    dir "target\encodeforge-0.4.0.jar" >> "%LOGFILE%"
    set JAR_OK=1
) else (
    echo   [ERROR] JAR file not found at target\encodeforge-0.4.0.jar
    echo   ERROR: JAR file not found >> "%LOGFILE%"
    set JAR_OK=0
)
echo. >> "%LOGFILE%"

echo [5/8] Checking EncodeForge directory permissions...
echo [Permissions] >> "%LOGFILE%"
set ENCODEFORGE_DIR=%USERPROFILE%\.encodeforge
if not exist "%ENCODEFORGE_DIR%" (
    mkdir "%ENCODEFORGE_DIR%" 2>> "%LOGFILE%"
    if %ERRORLEVEL% NEQ 0 (
        echo   [ERROR] Cannot create %ENCODEFORGE_DIR%
        echo   ERROR: Cannot create directory >> "%LOGFILE%"
        set PERM_OK=0
    ) else (
        echo   [OK] Created %ENCODEFORGE_DIR%
        set PERM_OK=1
    )
) else (
    echo   [OK] Directory exists: %ENCODEFORGE_DIR%
    echo   Directory exists >> "%LOGFILE%"
    set PERM_OK=1
)
echo. >> "%LOGFILE%"

echo [6/8] Checking for previous errors...
echo [Previous Errors] >> "%LOGFILE%"
if exist "%USERPROFILE%\encodeforge_error.txt" (
    echo   [INFO] Found previous error log
    echo   Previous error log: >> "%LOGFILE%"
    type "%USERPROFILE%\encodeforge_error.txt" >> "%LOGFILE%"
    echo.
    echo   Content of previous error:
    type "%USERPROFILE%\encodeforge_error.txt"
    echo.
) else (
    echo   [OK] No previous error logs
    echo   No previous errors >> "%LOGFILE%"
)
echo. >> "%LOGFILE%"

echo [7/8] Checking system info...
echo [System] >> "%LOGFILE%"
echo   OS: %OS% >> "%LOGFILE%"
systeminfo | findstr /B /C:"OS Name" /C:"OS Version" /C:"System Type" >> "%LOGFILE%"
echo   User: %USERNAME% >> "%LOGFILE%"
echo   Home: %USERPROFILE% >> "%LOGFILE%"
echo   Temp: %TEMP% >> "%LOGFILE%"
echo   [OK] System info collected
echo. >> "%LOGFILE%"

echo [8/8] Testing JAR manifest...
echo [JAR Manifest] >> "%LOGFILE%"
if %JAR_OK% EQU 1 (
    java -jar target\encodeforge-0.4.0.jar --version 2>> "%LOGFILE%"
    if %ERRORLEVEL% NEQ 0 (
        echo   [WARNING] JAR test failed (this is expected if no --version arg)
        echo   Testing JAR readability... >> "%LOGFILE%"
        jar tf target\encodeforge-0.4.0.jar | findstr "MainApp.class" >> "%LOGFILE%"
        if !ERRORLEVEL! EQU 0 (
            echo   [OK] JAR is readable
        ) else (
            echo   [ERROR] JAR may be corrupted
            echo   ERROR: Cannot read JAR contents >> "%LOGFILE%"
        )
    )
)
echo. >> "%LOGFILE%"

echo.
echo ================================================
echo Summary
echo ================================================
if %JAVA_OK% EQU 0 (
    echo [CRITICAL] Java is not installed or not in PATH
    echo            Download from: https://adoptium.net/
)
if %JAR_OK% EQU 0 (
    echo [CRITICAL] EncodeForge JAR not found
    echo            Run: mvnw.cmd clean package
)
if %PERM_OK% EQU 0 (
    echo [CRITICAL] Cannot write to user directory
    echo            Check folder permissions
)
if %PYTHON_OK% EQU 0 (
    echo [WARNING]  Python not found in PATH
    echo            EncodeForge will download dependencies
    echo            Download from: https://python.org/downloads/
)

echo.
echo Full diagnostics saved to: %LOGFILE%
echo.

if %JAVA_OK% EQU 1 if %JAR_OK% EQU 1 (
    echo All critical requirements met!
    echo.
    choice /C YN /M "Do you want to try launching EncodeForge now"
    if !ERRORLEVEL! EQU 1 (
        echo.
        echo Launching EncodeForge...
        java -jar target\encodeforge-0.4.0.jar
    )
) else (
    echo Please fix the critical issues above before launching.
)

echo.
pause
