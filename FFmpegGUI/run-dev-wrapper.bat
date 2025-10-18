@echo off
REM Quick development run script using Maven Wrapper
echo Starting FFmpeg Batch Transcoder in development mode...
echo.
echo Note: First run will download Maven automatically...
echo.
call mvnw.cmd clean javafx:run
pause

