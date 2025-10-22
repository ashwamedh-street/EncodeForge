#!/bin/bash
# EncodeForge Build Script for Linux/macOS
# Usage:
#   ./build.sh              # Auto-detect platform (DEB on Linux, DMG on macOS)
#   ./build.sh linux-deb    # Force Linux DEB
#   ./build.sh linux-rpm    # Force Linux RPM
#   ./build.sh mac-dmg      # Force macOS DMG (Intel)
#   ./build.sh mac-dmg-arm64 # Force macOS DMG (ARM64)

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
        # Auto-detect ARM64 vs Intel on macOS
        if [[ $(uname -m) == "arm64" ]]; then
            PROFILE="mac-dmg-arm64"
        else
            PROFILE="mac-dmg-x64"
        fi
    else
        echo "ERROR: Unable to detect platform. Please specify profile:"
        echo "  ./build.sh linux-deb"
        echo "  ./build.sh linux-rpm"
        echo "  ./build.sh mac-dmg"
        echo "  ./build.sh mac-dmg-arm64"
        exit 1
    fi
fi

echo "Building with profile: $PROFILE"
echo

# Validate profile for current platform
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if [[ "$PROFILE" == "mac-"* ]]; then
        echo "ERROR: Cannot build macOS packages on Linux!"
        echo "Use: ./build.sh linux-deb or ./build.sh linux-rpm"
        exit 1
    fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
    if [[ "$PROFILE" == "linux-"* ]]; then
        echo "ERROR: Cannot build Linux packages on macOS!"
        echo "Use: ./build.sh mac-dmg or ./build.sh mac-dmg-arm64"
        exit 1
    fi
fi

# Clean and package
echo "Step 1/2: Building JAR..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "ERROR: Maven build failed!"
    exit 1
fi

echo
echo "Step 2/2: Creating installer with profile $PROFILE..."
mvn package -P $PROFILE

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

