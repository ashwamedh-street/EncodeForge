# Build Instructions for EncodeForge

## Overview

EncodeForge uses Maven and jpackage to create native installers for each platform. The JAR bundles:
- ✅ Python scripts (.py files)
- ✅ FXML files
- ✅ Java classes and resources
- ✅ Icons and assets

Dependencies installed on-demand:
- ❌ Python libraries (downloaded via pip on first launch)
- ❌ FFmpeg (downloaded on first launch)
- ❌ Whisper AI models (optional, downloaded via setup wizard)

## Prerequisites

1. **JDK 17 or higher** with JavaFX
2. **Maven 3.8+**
3. **Platform-specific packaging tools:**
   - Windows: WiX Toolset 3.x for EXE/MSI
   - Linux: dpkg-deb for DEB, rpmbuild for RPM
   - macOS: Xcode Command Line Tools for DMG

## Quick Build

### Windows
```bash
# Create EXE installer (recommended)
.\build.bat

# Or create MSI installer
.\build.bat windows-msi

# Manual Maven command
mvn clean package -DskipTests
mvn package -P windows-exe
```

Output: `target/dist/windows/EncodeForge-0.3.1.exe`

### Linux
```bash
# Create DEB package (Ubuntu/Debian)
./build.sh linux-deb

# Or create RPM package (Fedora/RHEL)
./build.sh linux-rpm

# Manual Maven command
mvn clean package -DskipTests
mvn package -P linux-deb
```

Output: `target/dist/linux/encodeforge_0.3.1-1_amd64.deb`

### macOS
```bash
# Auto-detect architecture (Intel/ARM64)
./build.sh

# Create DMG installer for Intel Macs
./build.sh mac-dmg-x64

# Create DMG installer for Apple Silicon (ARM64)
./build.sh mac-dmg-arm64

# Manual Maven command
mvn clean package -DskipTests
mvn package -P mac-dmg-x64
```

Output: `target/dist/mac/EncodeForge-0.3.1.dmg`

## Development

### Run in Development Mode
```bash
# Without installer creation
mvn clean compile
mvn javafx:run
```

### Create JAR Only
```bash
mvn clean package -DskipTests
```

Output: `target/encodeforge-0.3.1.jar`

## Build Profiles

The pom.xml includes profiles for each platform:
- `windows-exe` - Windows EXE installer (auto-activated on Windows)
- `windows-msi` - Windows MSI installer
- `linux-deb` - Debian/Ubuntu DEB package
- `linux-rpm` - Fedora/RHEL RPM package
- `mac-dmg` - macOS DMG installer (auto-activated on macOS)

## Bundling Python Interpreter (Optional)

The current configuration uses the system Python and downloads libraries on-demand. To bundle a portable Python interpreter:

1. Download embeddable Python for your platform
2. Extract to `EncodeForge/src/main/resources/python-runtime/`
3. Update pom.xml to include it in resources
4. Update `DependencyManager.getPythonExecutable()` to check bundled Python first

## Troubleshooting

### "jpackage not found"
Ensure you're using JDK 14+ which includes jpackage.

### Windows: "WiX Toolset required"
Install WiX Toolset 3.x from https://wixtoolset.org/

### Linux: Missing packaging tools
```bash
# Debian/Ubuntu
sudo apt-get install dpkg-dev

# Fedora/RHEL
sudo dnf install rpm-build
```

### macOS: Code signing issues
The build uses `<macSign>false</macSign>`. For distribution, you'll need an Apple Developer certificate.

## File Size Expectations

- JAR file: ~50-60 MB (includes JavaFX + dependencies)
- Windows EXE: ~70-80 MB (includes JRE)
- Linux DEB/RPM: ~70-80 MB (includes JRE)
- macOS DMG: ~75-85 MB (includes JRE)

Dependencies downloaded on first launch add:
- Core Python libraries: ~50 MB
- FFmpeg: ~100-150 MB
- Whisper AI (optional): ~300 MB - 3 GB depending on model

## Version Updates

To bump the version:
1. Update `<version>` in `pom.xml`
2. Update `VERSION` constant in `MainApp.java`
3. Build and test

## Distribution

Installers are created in:
- Windows: `target/dist/windows/`
- Linux: `target/dist/linux/`
- macOS: `target/dist/mac/`

These can be distributed directly to users. On first launch, the app will:
1. Extract Python scripts to `~/.encodeforge/scripts/`
2. Check for required dependencies
3. Download and install missing components with progress UI

