@echo off
echo FFmpeg Batch Transcoder - CLI Mode
echo.
echo Usage:
echo   start_cli.bat encoder [files/folders] [options]
echo   start_cli.bat subtitle [files/folders] [options]
echo   start_cli.bat renamer [files/folders] [options]
echo.
echo Examples:
echo   start_cli.bat encoder "C:\Videos" --use-nvenc
echo   start_cli.bat subtitle "C:\Videos" --enable-subtitle-generation
echo   start_cli.bat renamer "C:\Videos" --preview-only --tmdb-api-key YOUR_KEY
echo.
echo For more options, run: python ffmpeg_cli.py --help
echo.

python ffmpeg_cli.py %*

