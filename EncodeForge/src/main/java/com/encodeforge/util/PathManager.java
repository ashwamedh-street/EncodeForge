package com.encodeforge.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Unified path management for EncodeForge application data.
 * All application data is stored in a single location:
 * - Windows: AppData/Local/EncodeForge/
 * - Unix/Linux: ~/.local/share/EncodeForge/
 * - macOS: ~/Library/Application Support/EncodeForge/
 */
public class PathManager {
    
    private static final String APP_NAME = "EncodeForge";
    private static Path baseDir = null;
    
    /**
     * Get the base application data directory
     * @return Path to the base application directory
     */
    public static Path getBaseDir() {
        if (baseDir == null) {
            baseDir = determineBaseDir();
            // Ensure the directory exists
            try {
                Files.createDirectories(baseDir);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create base application directory: " + baseDir, e);
            }
        }
        return baseDir;
    }
    
    /**
     * Get the settings directory
     * @return Path to the settings directory
     */
    public static Path getSettingsDir() {
        return getSubDir("settings");
    }
    
    /**
     * Get the logs directory
     * @return Path to the logs directory
     */
    public static Path getLogsDir() {
        return getSubDir("logs");
    }
    
    /**
     * Get the cache directory
     * @return Path to the cache directory
     */
    public static Path getCacheDir() {
        return getSubDir("cache");
    }
    
    /**
     * Get the temp directory
     * @return Path to the temp directory
     */
    public static Path getTempDir() {
        return getSubDir("temp");
    }
    
    /**
     * Get the backups directory
     * @return Path to the backups directory
     */
    public static Path getBackupsDir() {
        return getSubDir("backups");
    }
    
    /**
     * Get the profiles directory
     * @return Path to the profiles directory
     */
    public static Path getProfilesDir() {
        return getSubDir("profiles");
    }
    
    /**
     * Get the models directory
     * @return Path to the models directory
     */
    public static Path getModelsDir() {
        return getSubDir("models");
    }
    
    /**
     * Get the settings file path
     * @return Path to settings.json
     */
    public static Path getSettingsFile() {
        return getSettingsDir().resolve("settings.json");
    }
    
    /**
     * Get the conversion state file path
     * @return Path to conversion_state.json
     */
    public static Path getConversionStateFile() {
        return getTempDir().resolve("conversion_state.json");
    }
    
    /**
     * Get a subdirectory under the base directory
     * @param subDirName Name of the subdirectory
     * @return Path to the subdirectory
     */
    private static Path getSubDir(String subDirName) {
        Path subDir = getBaseDir().resolve(subDirName);
        try {
            Files.createDirectories(subDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create subdirectory: " + subDir, e);
        }
        return subDir;
    }
    
    /**
     * Determine the base directory based on the operating system
     * @return Path to the base application directory
     */
    private static Path determineBaseDir() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        
        if (osName.contains("win")) {
            // Windows: AppData/Local/EncodeForge/
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                return Paths.get(localAppData, APP_NAME);
            } else {
                // Fallback to user home
                return Paths.get(System.getProperty("user.home"), "AppData", "Local", APP_NAME);
            }
        } else if (osName.contains("mac")) {
            // macOS: ~/Library/Application Support/EncodeForge/
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
        } else {
            // Linux/Unix: ~/.local/share/EncodeForge/
            return Paths.get(System.getProperty("user.home"), ".local", "share", APP_NAME);
        }
    }
    
    /**
     * Get the base directory as a String (for backward compatibility)
     * @return String path to the base directory
     */
    public static String getBaseDirString() {
        return getBaseDir().toString();
    }
    
    /**
     * Get the settings file path as a String (for backward compatibility)
     * @return String path to settings.json
     */
    public static String getSettingsFilePath() {
        return getSettingsFile().toString();
    }
    
    /**
     * Create a temporary file in the application's temp directory
     * @param prefix File prefix
     * @param suffix File suffix
     * @return Path to the temporary file
     */
    public static Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(getTempDir(), prefix, suffix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temporary file", e);
        }
    }
    
    /**
     * Create a temporary directory in the application's temp directory
     * @param prefix Directory prefix
     * @return Path to the temporary directory
     */
    public static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(getTempDir(), prefix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temporary directory", e);
        }
    }
}
