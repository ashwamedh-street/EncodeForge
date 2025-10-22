package com.encodeforge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * Utility to extract bundled Python scripts from JAR
 */
public class ResourceExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ResourceExtractor.class);
    private static Path extractedScriptsDir;
    
    /**
     * Extract Python scripts from JAR resources to app directory
     * This extracts only .py files, not the full Python runtime or libraries
     */
    public static void extractPythonScripts() throws IOException {
        // Extract to ~/.encodeforge/scripts/
        extractedScriptsDir = PathManager.getBaseDir().resolve("scripts");
        
        // Create directories
        Files.createDirectories(extractedScriptsDir);
        
        logger.info("Extracting Python scripts to: {}", extractedScriptsDir);
        
        // Extract all .py files from /python/ resource directory
        extractResourceDirectory("/python/", extractedScriptsDir);
        
        // Set system property for easy access
        System.setProperty("python.scripts.dir", extractedScriptsDir.toString());
        
        logger.info("Python scripts extracted successfully");
    }
    
    /**
     * Extract a directory from JAR resources
     */
    private static void extractResourceDirectory(String resourcePath, Path targetDir) throws IOException {
        logger.info("Extracting resource directory: {} -> {}", resourcePath, targetDir);
        
        try {
            // Get resource URI
            URI uri = ResourceExtractor.class.getResource(resourcePath).toURI();
            
            // Handle both JAR and file system paths
            Path sourcePath;
            FileSystem fs = null;
            
            if (uri.getScheme().equals("jar")) {
                // Running from JAR
                fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                sourcePath = fs.getPath(resourcePath);
            } else {
                // Running from IDE
                sourcePath = Paths.get(uri);
            }
            
            // Track extraction stats
            final int[] stats = new int[2]; // [copied, skipped]
            
            // Walk through all files in the resource directory
            try (Stream<Path> walk = Files.walk(sourcePath)) {
                walk.forEach(source -> {
                    try {
                        // Get relative path
                        Path relative = sourcePath.relativize(source);
                        Path target = targetDir.resolve(relative.toString());
                        
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            // Check if file already exists with same size
                            boolean shouldCopy = true;
                            if (Files.exists(target)) {
                                try {
                                    long sourceSize = Files.size(source);
                                    long targetSize = Files.size(target);
                                    if (sourceSize == targetSize) {
                                        shouldCopy = false;
                                    }
                                } catch (IOException e) {
                                    // If we can't compare sizes, copy anyway
                                }
                            }
                            
                            if (shouldCopy) {
                                Files.createDirectories(target.getParent());
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                                logger.debug("Extracted: {}", relative);
                                stats[0]++;
                            } else {
                                logger.debug("Skipped (already exists): {}", relative);
                                stats[1]++;
                            }
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to extract: {}", source, e);
                    }
                });
            }
            
            // Close file system if we opened one
            if (fs != null) {
                fs.close();
            }
            
            logger.info("Successfully processed {} to {} (copied: {}, skipped: {})", 
                resourcePath, targetDir, stats[0], stats[1]);
            
        } catch (URISyntaxException e) {
            throw new IOException("Invalid resource path: " + resourcePath, e);
        }
    }
    
    /**
     * Clean up extracted resources (optional, called on app exit)
     */
    public static void cleanup() {
        // Keep extracted scripts for next run
        logger.info("Resource cleanup skipped (files kept for next startup)");
    }
    
    public static Path getExtractedScriptsDir() {
        return extractedScriptsDir;
    }
}


