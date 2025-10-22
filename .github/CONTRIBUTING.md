## Contributing to Encode Forge

Thank you for your interest in contributing! This document provides guidelines and instructions for contributing to the project.

## Getting Started

1. **Fork the repository** and clone your fork locally
2. **Set up your development environment**:
   - Install Java 17 or later with JavaFX
   - Install Python 3.9 or later
   - Install Maven 3.8+ (or use the included Maven Wrapper)
   - Install Git
   - FFmpeg will be auto-downloaded on first run

3. **Create a new branch** for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Java/JavaFX Development
```bash
cd EncodeForge
./mvnw clean install
./mvnw javafx:run
```

### Python Script Development
```bash
cd EncodeForge
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r ../requirements-core.txt  # Required libraries
pip install -r ../requirements-ai.txt    # Optional AI libraries
python src/main/resources/python/encodeforge_cli.py --help
```

### Building Installers
```bash
# Windows EXE installer
.\build.bat

# Windows MSI installer
.\build.bat windows-msi

# Linux DEB package
./build.sh linux-deb

# Linux RPM package
./build.sh linux-rpm

# macOS DMG
./build.sh mac-dmg
```

See `BUILD.md` for detailed build instructions.

## Code Style

### Java
- Follow standard Java naming conventions
- Use 4 spaces for indentation
- Add JavaDoc comments for public methods
- Keep lines under 120 characters when possible

### Python
- Follow PEP 8 style guidelines
- Use 4 spaces for indentation
- Add docstrings for functions and classes
- Use type hints where appropriate

## Making Changes

1. **Write clean, readable code** with appropriate comments
2. **Test your changes thoroughly** on your platform
3. **Update documentation** if you're changing functionality
4. **Follow the existing code structure** and patterns
5. **Keep commits focused** - one logical change per commit
6. **For Java changes**: Update JavaDoc comments for public methods
7. **For Python changes**: Add docstrings for functions and classes
8. **For UI changes**: Test on different screen resolutions and themes
9. **For backend changes**: Test with various file formats and codecs

## Commit Messages

Write clear, descriptive commit messages:
```
Add feature: Brief description of what was added

More detailed explanation if needed. Explain why the change
was made, not just what was changed.

Fixes #123
```

## Submitting Changes

1. **Push your changes** to your fork
2. **Create a Pull Request** with a clear title and description
3. **Reference any related issues** in the PR description
4. **Be responsive** to feedback and questions

## Reporting Bugs

Use the bug report template when creating issues. Include:
- Clear description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, versions, etc.)
- Screenshots or logs if applicable

## Feature Requests

Use the feature request template. Explain:
- What problem the feature solves
- How you envision it working
- Why it would be valuable

## Code Review Process

- All submissions require review before merging
- Reviewers may request changes or ask questions
- Be patient and respectful during the review process
- Address all feedback before the PR can be merged

## Testing

- Test your changes with various file formats
- Test on your platform (Windows/macOS/Linux if possible)
- Ensure existing functionality still works
- Add unit tests for new features when applicable
- Test JavaFX UI changes in the desktop application
- Test Python backend changes with CLI interface
- Test hardware acceleration (NVENC, AMF, Quick Sync, VideoToolbox)
- Test first-time setup with automatic dependency installation
- Test FFmpeg detection and download (DependencyManager)
- Test Python library installation via pip
- Test optional Whisper AI setup wizard
- Test subtitle generation with Whisper (if installed)
- Test subtitle downloads with OpenSubtitles
- Test file renaming with metadata providers
- Verify log files are generated correctly in `~/.encodeforge/logs/` or `%APPDATA%\.encodeforge\logs\`

## Questions?

Feel free to open an issue for questions or reach out to the maintainers.

Thank you for contributing!

