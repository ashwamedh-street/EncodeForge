#!/bin/bash
# EncodeForge Diagnostic Launcher for Linux/Mac
# Shows console output to diagnose launch failures

echo "============================================"
echo "EncodeForge Diagnostic Launcher"
echo "============================================"
echo ""
echo "This terminal will stay open to show any errors."
echo ""
echo "System Information:"
echo "-------------------"
echo "Java Version:"
java -version
echo ""
echo "Python Version:"
python3 --version 2>/dev/null || python --version 2>/dev/null || echo "Python not found in PATH"
echo ""
echo "============================================"
echo "Starting EncodeForge..."
echo "============================================"
echo ""

# Run the JAR with console output visible
java -jar target/encodeforge-0.4.0.jar

EXIT_CODE=$?

echo ""
echo "============================================"
echo "Application exited with code: $EXIT_CODE"
echo "============================================"
echo ""
echo "If you saw errors above, please report them."
echo ""
read -p "Press Enter to close..."
