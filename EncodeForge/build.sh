#!/bin/bash
# EncodeForge Build Script for Linux/macOS
# Usage:
#   ./build.sh              # Auto-detect platform (DEB on Linux, DMG on macOS)
#   ./build.sh linux-deb    # Force Linux DEB
#   ./build.sh linux-rpm    # Force Linux RPM
#   ./build.sh mac-dmg      # Force macOS DMG

echo "====================================="
echo "EncodeForge Build Script"
echo "====================================="
echo

# Navigate to script directory
cd "$(dirname "$0")"

# Determine profile
PROFILE=$1
if [ -z "$PROFILE" ]; then
    # Auto-detect platform
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        PROFILE="linux-deb"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        PROFILE="mac-dmg"
    else
        echo "ERROR: Unable to detect platform. Please specify profile:"
        echo "  ./build.sh linux-deb"
        echo "  ./build.sh linux-rpm"
        echo "  ./build.sh mac-dmg"
        exit 1
    fi
fi

echo "Building with profile: $PROFILE"
echo

# Clean and package
echo "Step 1/2: Building JAR..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "ERROR: Maven build failed!"
    exit 1
fi

echo
echo "Step 2/2: Creating installer with profile $PROFILE..."
mvn jpackage:jpackage@jpackage-$PROFILE

if [ $? -ne 0 ]; then
    echo "ERROR: Installer creation failed!"
    exit 1
fi

echo
echo "====================================="
echo "Build Complete!"
echo "====================================="
echo

# Show output location based on profile
if [[ "$PROFILE" == "linux-"* ]]; then
    echo "Installer location: target/dist/linux/"
elif [[ "$PROFILE" == "mac-"* ]]; then
    echo "Installer location: target/dist/mac/"
fi
echo

