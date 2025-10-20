
#!/bin/bash
echo "FFmpeg Batch Transcoder - CLI Mode"
echo ""
echo "Usage:"
echo "  ./start_cli.sh encoder [files/folders] [options]"
echo "  ./start_cli.sh subtitle [files/folders] [options]"
echo "  ./start_cli.sh renamer [files/folders] [options]"
echo ""
echo "Examples:"
echo "  ./start_cli.sh encoder ~/Videos --use-nvenc"
echo "  ./start_cli.sh subtitle ~/Videos --enable-subtitle-generation"
echo "  ./start_cli.sh renamer ~/Videos --preview-only --tmdb-api-key YOUR_KEY"
echo ""
echo "For more options, run: python3 EncodeForge/src/main/resources/python/encodeforge_cli.py --help"
echo ""

python3 EncodeForge/src/main/resources/python/encodeforge_cli.py "$@"

