# Diagnostic Tools for EncodeForge Launch Issues

If EncodeForge fails to launch with no error message, use these diagnostic tools to identify the problem.

## Windows

### Quick Diagnostic
Run this to check your environment:
```
diagnose-environment.bat
```

This will check:
- Java installation and version
- JavaFX availability
- Python installation
- JAR file integrity
- Directory permissions
- Previous error logs
- System information

### Launch with Console Output
Run this to see any error messages:
```
launch-diagnostic.bat
```

The console will stay open showing any errors that occur.

## Linux/Mac

### Launch with Console Output
```bash
chmod +x launch-diagnostic.sh
./launch-diagnostic.sh
```

## Common Issues and Solutions

### Issue 1: Silent Failure (No Error, No Window)
**Symptoms:** Double-clicking the app does nothing, no window appears, no error message

**Causes:**
1. **Python scripts failed to extract from JAR**
   - Solution: Check permissions on `~/.encodeforge/` directory
   - Solution: Run diagnostic tool to verify JAR integrity

2. **JavaFX runtime missing**
   - Solution: Reinstall Java with JavaFX support
   - Solution: Download JavaFX SDK separately

3. **Antivirus blocking JAR file system access**
   - Solution: Add EncodeForge to antivirus exclusions
   - Solution: Check antivirus logs for blocked operations

### Issue 2: "Python Not Found" Error
**Symptoms:** Error dialog says Python is required

**Solution:** Install Python 3.8-3.13 from https://python.org/downloads/

### Issue 3: Missing Dependencies
**Symptoms:** App launches but subtitle features don't work

**Solution:** 
1. Delete `~/.encodeforge/initialization_complete.flag`
2. Restart EncodeForge
3. Let the initialization dialog install dependencies

### Issue 4: Different Behavior on Different PCs
**Symptoms:** Works on one PC but not another (same OS)

**Check:**
- Java version differences (`java -version`)
- Python version differences (`python --version`)
- Antivirus software differences
- User account permissions differences

Run `diagnose-environment.bat` on both machines and compare the output.

## Error Log Locations

EncodeForge writes error logs to multiple locations:

1. **Early initialization errors:**
   - Windows: `%USERPROFILE%\encodeforge_error.txt`
   - Linux/Mac: `~/encodeforge_error.txt`

2. **Launch errors:**
   - Windows: `%USERPROFILE%\encodeforge_launch_error.txt`
   - Linux/Mac: `~/encodeforge_launch_error.txt`

3. **Runtime logs:**
   - Windows: `%USERPROFILE%\.encodeforge\logs\`
   - Linux/Mac: `~/.encodeforge/logs/`

## Reporting Issues

If you still can't get EncodeForge to launch, please report the issue with:

1. Output from `diagnose-environment.bat` (or `launch-diagnostic.sh`)
2. Contents of any `encodeforge_error.txt` files
3. Java version (`java -version`)
4. Python version (`python --version`)
5. Operating system and version
6. Antivirus software being used

## Clean Reinstall Procedure

To completely reset EncodeForge:

### Windows
```batch
REM 1. Uninstall via Control Panel

REM 2. Delete app data
rmdir /s /q "%USERPROFILE%\.encodeforge"
rmdir /s /q "%LOCALAPPDATA%\EncodeForge"

REM 3. Delete error logs
del "%USERPROFILE%\encodeforge_*.txt"

REM 4. Reinstall EncodeForge
```

### Linux/Mac
```bash
# 1. Delete app data
rm -rf ~/.encodeforge

# 2. Delete error logs  
rm ~/encodeforge_*.txt

# 3. Reinstall EncodeForge
```

## Building from Source with Diagnostics

To build EncodeForge with enhanced diagnostics enabled:

```batch
REM Windows
mvnw.cmd clean package -DskipTests

REM Then run
diagnose-environment.bat
```

```bash
# Linux/Mac
./mvnw clean package -DskipTests

# Then run
./launch-diagnostic.sh
```
