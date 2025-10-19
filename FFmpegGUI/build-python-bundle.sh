#!/bin/bash
# Build script to bundle Python runtime and dependencies for EncodeForge
# This script creates a portable Python environment with all required packages

set -e

echo "========================================"
echo "Encode Forge Python Bundle Builder"
echo "========================================"

# Configuration
PYTHON_VERSION="3.12"
BUNDLE_DIR="python-bundle"
REQUIREMENTS_FILE="requirements.txt"
PYTHON_SCRIPTS_DIR="src/main/resources/python"
OUTPUT_DIR="target/python-runtime"

# Check if Python 3.12 is available first
if command -v python3.12 &> /dev/null; then
    echo "Found Python 3.12, using it for compatibility"
    PYTHON_CMD="python3.12"
elif command -v python3.13 &> /dev/null; then
    echo "Found Python 3.13, using it for compatibility"
    PYTHON_CMD="python3.13"
elif command -v python3 &> /dev/null; then
    # Check Python version compatibility
    PYTHON_VER=$(python3 --version 2>&1 | cut -d' ' -f2)
    echo "Detected Python version: $PYTHON_VER"
    
    # Check if Python version is compatible (3.10 to 3.13)
    python3 -c "import sys; ver = sys.version_info; exit(0 if (3,10) <= ver < (3,14) else 1)" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "ERROR: Python $PYTHON_VER is not compatible"
        echo "Please install Python 3.12 or 3.13"
        exit 1
    fi
    PYTHON_CMD="python3"
else
    echo "ERROR: Python3 is not installed or not in PATH"
    echo "Please install Python 3.12 or 3.13 and try again"
    exit 1
fi

$PYTHON_CMD --version
echo "Using Python command: $PYTHON_CMD"

echo "Creating Python bundle directory..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

echo "Installing Python packages..."
$PYTHON_CMD -m venv "$OUTPUT_DIR/venv"
source "$OUTPUT_DIR/venv/bin/activate"

# Install packages from requirements
if [ -f "$REQUIREMENTS_FILE" ]; then
    echo "Installing packages from $REQUIREMENTS_FILE..."
    pip install -r "$REQUIREMENTS_FILE" --no-cache-dir
else
    echo "Installing default packages..."
    pip install streamlit pandas requests --no-cache-dir
    echo "Installing whisper with compatible numba..."
    pip install "numba>=0.58.0,<0.63.0" --no-cache-dir
    pip install openai-whisper --no-cache-dir
fi

# Copy Python scripts
echo "Copying Python scripts..."
if [ -d "$PYTHON_SCRIPTS_DIR" ]; then
    mkdir -p "$OUTPUT_DIR/scripts"
    cp "$PYTHON_SCRIPTS_DIR"/*.py "$OUTPUT_DIR/scripts/"
else
    echo "WARNING: Python scripts directory not found: $PYTHON_SCRIPTS_DIR"
fi

# Create launcher script
echo "Creating Python launcher..."
cat > "$OUTPUT_DIR/run-python.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
source venv/bin/activate
python scripts/ffmpeg_manager.py "$@"
EOF
chmod +x "$OUTPUT_DIR/run-python.sh"

# Create requirements.txt for the bundle
echo "Creating bundle requirements..."
pip freeze > "$OUTPUT_DIR/requirements.txt"

# Create platform-specific Python executable wrapper
echo "Creating Python executable wrapper..."
cat > "$OUTPUT_DIR/python_backend" << 'EOF'
#!/bin/bash
export PYTHONPATH="$(dirname "$0")/scripts"
source "$(dirname "$0")/venv/bin/activate"
python "$(dirname "$0")/scripts/ffmpeg_manager.py" "$@"
EOF
chmod +x "$OUTPUT_DIR/python_backend"

echo ""
echo "========================================"
echo "Python bundle created successfully!"
echo "Location: $OUTPUT_DIR"
echo "========================================"
echo ""
echo "Bundle contents:"
ls -la "$OUTPUT_DIR"

echo ""
echo "Next steps:"
echo "1. Run 'mvn clean package' to build the Java application"
echo "2. The Python bundle will be included in the JAR"
echo ""
