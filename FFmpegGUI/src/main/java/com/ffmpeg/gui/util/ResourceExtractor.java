package com.ffmpeg.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Utility to extract bundled Python runtime and scripts
 */
public class ResourceExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ResourceExtractor.class);
    private static Path extractedPythonDir;
    
    /**
     * Extract Python runtime from JAR resources to temporary directory
     */
    public static void extractPythonRuntime() throws IOException {
        // Create extraction directory in user's home or temp
        Path baseDir = Paths.get(System.getProperty("user.home"), ".ffmpeg-transcoder");
        extractedPythonDir = baseDir.resolve("python");
        
        // Create directories
        Files.createDirectories(extractedPythonDir);
        
        logger.info("Extracting Python runtime to: {}", extractedPythonDir);
        
        // Extract Python scripts (new modular structure)
        String[] pythonScripts = {
            "ffmpeg_api.py",
            "ffmpeg_core.py",
            "ffmpeg_manager.py",
            "whisper_manager.py",
            "media_renamer.py",
            "opensubtitles_manager.py",
            "profile_manager.py",
            "subtitle_providers.py"
        };
        
        for (String script : pythonScripts) {
            try {
                extractResource("/python/" + script, extractedPythonDir.resolve(script));
            } catch (IOException e) {
                logger.warn("Could not extract {}: {}", script, e.getMessage());
            }
        }
        
        // Extract Python executable (if bundled)
        String osName = System.getProperty("os.name").toLowerCase();
        String exeName = osName.contains("win") ? "ffmpeg_backend.exe" : "ffmpeg_backend";
        
        try {
            extractResource("/python/" + exeName, extractedPythonDir.resolve(exeName));
            
            // Make executable on Unix systems
            if (!osName.contains("win")) {
                Path execFile = extractedPythonDir.resolve(exeName);
                execFile.toFile().setExecutable(true);
            }
        } catch (IOException e) {
            logger.warn("Bundled Python executable not found, will use system Python");
        }
        
        // Set system property for easy access
        System.setProperty("python.runtime.dir", extractedPythonDir.toString());
        
        logger.info("Python runtime extracted successfully");
    }
    
    /**
     * Extract a single resource from JAR to file system
     */
    private static void extractResource(String resourcePath, Path targetPath) throws IOException {
        // Check if resource exists
        InputStream is = ResourceExtractor.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        
        try (is) {
            // If file already exists and is same size, skip extraction
            if (Files.exists(targetPath)) {
                long existingSize = Files.size(targetPath);
                long resourceSize = is.available();
                
                if (existingSize == resourceSize) {
                    logger.debug("Skipping extraction, file already exists: {}", targetPath.getFileName());
                    return;
                }
            }
            
            // Copy resource to file
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Extracted: {} -> {}", resourcePath, targetPath.getFileName());
        }
    }
    
    /**
     * Clean up extracted resources (optional, called on app exit)
     */
    public static void cleanup() {
        // Optionally delete extracted files
        // For now, we keep them for faster startup on subsequent runs
        logger.info("Resource cleanup skipped (files kept for next startup)");
    }
    
    public static Path getExtractedPythonDir() {
        return extractedPythonDir;
    }
}

