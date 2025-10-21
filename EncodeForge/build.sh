#!/bin/bash
# Main build script for Encode Forge
# Usage: ./build.sh [target]
#   target: jar, windows, linux, mac, all, dev, clean
#   Default: all

set -e

BUILD_TARGET=${1:-all}

echo "========================================"
echo "Encode Forge Build System"
echo "Target: $BUILD_TARGET"
echo "========================================"

# Configuration
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$PROJECT_DIR/target"
DIST_DIR="$OUTPUT_DIR/dist"

echo "Project directory: $PROJECT_DIR"
echo "Output directory: $OUTPUT_DIR"

# Build Functions
setup_build() {
    echo ""
    echo "Setting up build environment..."
    rm -rf "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
}

build_python_bundle() {
    echo ""
    echo "Building Python runtime bundle..."
    bash "$PROJECT_DIR/build-python-bundle.sh"
    if [ $? -ne 0 ]; then
        echo "ERROR: Python bundle creation failed"
        exit 1
    fi
}

build_java() {
    echo ""
    echo "Building Java application..."
    cd "$PROJECT_DIR"
    ./mvnw clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "ERROR: Java application build failed"
        exit 1
    fi
}

create_windows_exe() {
    echo ""
    echo "Creating Windows executable..."
    ./mvnw jpackage:jpackage@jpackage-windows
    if [ $? -eq 0 ]; then
        echo "SUCCESS: Windows executable created"
    else
        echo "INFO: Windows executable creation skipped (requires Windows or cross-compilation tools)"
    fi
}

create_linux_appimage() {
    echo ""
    echo "Creating Linux AppImage..."
    ./mvnw jpackage:jpackage@jpackage-linux
    if [ $? -eq 0 ]; then
        echo "SUCCESS: Linux AppImage created"
    else
        echo "WARNING: Linux AppImage creation failed"
    fi
}

create_mac_dmg() {
    echo ""
    echo "Creating macOS DMG..."
    ./mvnw jpackage:jpackage@jpackage-mac
    if [ $? -eq 0 ]; then
        echo "SUCCESS: macOS DMG created"
    else
        echo "INFO: macOS DMG creation skipped (requires macOS or cross-compilation tools)"
    fi
}

create_portable_launcher() {
    echo ""
    echo "Creating portable launcher..."
    LAUNCHER_DIR="$OUTPUT_DIR/launcher"
    mkdir -p "$LAUNCHER_DIR"
    
    # Create universal launcher script
    cat > "$LAUNCHER_DIR/encode-forge.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
java -jar encodeforge-0.3.0.jar "$@"
EOF
    chmod +x "$LAUNCHER_DIR/encode-forge.sh"
    
    # Copy JAR to launcher directory
    cp "$OUTPUT_DIR/encodeforge-0.3.0.jar" "$LAUNCHER_DIR/"
    echo "SUCCESS: Portable launcher created"
}

# Main build logic
case "$BUILD_TARGET" in
    "clean")
        echo "Cleaning build directory..."
        rm -rf "$OUTPUT_DIR"
        echo "Clean complete."
        ;;
    "dev")
        echo "Starting development mode..."
        cd "$PROJECT_DIR"
        ./mvnw javafx:run
        ;;
    "jar")
        setup_build
        build_python_bundle
        build_java
        ;;
    "windows")
        setup_build
        build_python_bundle
        build_java
        create_windows_exe
        create_portable_launcher
        ;;
    "linux")
        setup_build
        build_python_bundle
        build_java
        create_linux_appimage
        create_portable_launcher
        ;;
    "mac")
        setup_build
        build_python_bundle
        build_java
        create_mac_dmg
        create_portable_launcher
        ;;
    "all")
        setup_build
        build_python_bundle
        build_java
        create_windows_exe
        create_linux_appimage
        create_mac_dmg
        create_portable_launcher
        ;;
    *)
        echo "Invalid target: $BUILD_TARGET"
        echo "Valid targets: jar, windows, linux, mac, all, dev, clean"
        exit 1
        ;;
esac

    echo ""
    echo "========================================"
    echo "Build Complete!"
    echo "========================================"
    echo ""
    echo "Generated files:"

    if [ -f "$OUTPUT_DIR/encodeforge-0.3.0.jar" ]; then
        echo "  - Universal JAR: $OUTPUT_DIR/encodeforge-0.3.0.jar"
    fi

    if [ -d "$DIST_DIR/windows" ]; then
        echo "  - Windows: $DIST_DIR/windows/"
        ls -la "$DIST_DIR/windows/" 2>/dev/null || true
    fi

    if [ -d "$DIST_DIR/linux" ]; then
        echo "  - Linux: $DIST_DIR/linux/"
        ls -la "$DIST_DIR/linux/" 2>/dev/null || true
    fi

    if [ -d "$DIST_DIR/mac" ]; then
        echo "  - macOS: $DIST_DIR/mac/"
        ls -la "$DIST_DIR/mac/" 2>/dev/null || true
    fi

    if [ -d "$OUTPUT_DIR/launcher" ]; then
        echo "  - Portable Launcher: $OUTPUT_DIR/launcher/"
        ls -la "$OUTPUT_DIR/launcher/" 2>/dev/null || true
    fi

    echo ""
    echo "Python runtime location: $OUTPUT_DIR/python-runtime"
    echo ""
    echo "Usage:"
    echo "  Windows: EncodeForge.exe (installer) or encode-forge.sh (portable)"
    echo "  Linux: EncodeForge (AppImage) or encode-forge.sh (portable)"
    echo "  macOS: Encode Forge.app (in DMG) or encode-forge.sh (portable)"
    echo "  Universal: java -jar encodeforge-0.3.0.jar"
    echo ""
