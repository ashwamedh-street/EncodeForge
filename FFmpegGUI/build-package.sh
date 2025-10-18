#!/bin/bash
# ===================================================================
# Build and Package Script for Linux/Mac
# Creates a distributable Java application with bundled Python
# ===================================================================

set -e  # Exit on error

echo ""
echo "==============================================="
echo " FFmpeg Batch Transcoder - Build & Package"
echo "==============================================="
echo ""

# Step 1: Build Java application with Maven
echo "[1/4] Building Java application..."
mvn clean package

# Step 2: Package Python with PyInstaller
echo ""
echo "[2/4] Packaging Python backend..."
cd src/main/resources/python

if [ -d "dist" ]; then rm -rf dist; fi
if [ -d "build" ]; then rm -rf build; fi

python3 -m PyInstaller --onefile --name ffmpeg_backend \
    --add-data "*.py:." \
    --hidden-import json \
    --hidden-import pathlib \
    ffmpeg_batch_transcoder.py || {
        echo "WARNING: PyInstaller failed. Python script will be used directly."
        cd ../../../..
    }

if [ $? -eq 0 ]; then
    # Copy packaged Python to target/python
    cd ../../../..
    mkdir -p target/python
    cp src/main/resources/python/dist/ffmpeg_backend target/python/
    cp src/main/resources/python/ffmpeg_batch_transcoder.py target/python/
    chmod +x target/python/ffmpeg_backend
fi

# Step 3: Create distribution directory
echo ""
echo "[3/4] Preparing distribution..."
mkdir -p target/distribution
cp target/*.jar target/distribution/ffmpeg-transcoder.jar

# Copy Python runtime
mkdir -p target/distribution/python
cp -r target/python/* target/distribution/python/

# Step 4: Create launch script
echo ""
echo "[4/4] Creating launcher..."

cat > target/distribution/ffmpeg-transcoder.sh << 'EOF'
#!/bin/bash
echo "Starting FFmpeg Batch Transcoder..."
java -jar ffmpeg-transcoder.jar
EOF

chmod +x target/distribution/ffmpeg-transcoder.sh

echo ""
echo "==============================================="
echo " Build Complete!"
echo "==============================================="
echo ""
echo "Distribution created in: target/distribution"
echo ""
echo "To run: ./ffmpeg-transcoder.sh"
echo ""

