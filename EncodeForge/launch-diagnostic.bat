@echo off
REM Diagnostic launcher for EncodeForge
REM Shows console output to diagnose launch failures

echo ============================================
echo EncodeForge Diagnostic Launcher
echo ============================================
echo.
echo This window will stay open to show any errors.
echo.
echo System Information:
echo -------------------
echo Java Version:
java -version
echo.
echo Python Version:
python --version 2>NUL || python3 --version 2>NUL || echo Python not found in PATH
echo.
echo ============================================
echo Starting EncodeForge...
echo ============================================
echo.

REM Run the JAR with console output visible
java -jar target\encodeforge-0.4.0.jar

echo.
echo ============================================
echo Application exited with code: %ERRORLEVEL%
echo ============================================
echo.
echo If you saw errors above, please report them.
echo.
pause
