package com.encodeforge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * FFmpeg runtime extractor that extracts FFmpeg binaries from JAR resources
 * and makes them available for use by the application.
 */
public class FFmpegRuntimeExtractor {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegRuntimeExtractor.class);
    
    /**
     * Extract FFmpeg binaries from JAR resources
     */
    public static void extractFFmpegRuntime(Path targetDir) throws IOException {
        logger.info("Extracting FFmpeg runtime to: {}", targetDir);
        
        // Get the JAR file path
        String jarPath = FFmpegRuntimeExtractor.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();
        
        if (jarPath.endsWith(".jar")) {
            extractFromJar(jarPath, targetDir);
        } else {
            // Running from IDE - extract from classpath resources
            extractFromClasspath(targetDir);
        }
        
        logger.info("FFmpeg runtime extraction completed");
    }
    
    /**
     * Extract from JAR file
     */
    private static void extractFromJar(String jarPath, Path targetDir) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Extract FFmpeg runtime resources
                if (entryName.startsWith("ffmpeg-runtime/") && !entry.isDirectory()) {
                    String relativePath = entryName.substring("ffmpeg-runtime/".length());
                    Path targetFile = targetDir.resolve(relativePath);
                    
                    // Create parent directories
                    Files.createDirectories(targetFile.getParent());
                    
                    // Extract file
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Extracted: {} -> {}", entryName, targetFile.getFileName());
                        
                        // Make executable if it's an FFmpeg binary
                        if (isFFmpegBinary(relativePath)) {
                            targetFile.toFile().setExecutable(true);
                            logger.debug("Made executable: {}", targetFile.getFileName());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract from classpath (when running from IDE)
     */
    private static void extractFromClasspath(Path targetDir) throws IOException {
        // Extract known FFmpeg binaries from classpath resources
        String[] ffmpegBinaries = {
            "ffmpeg.exe",
            "ffprobe.exe",
            "ffmpeg",
            "ffprobe"
        };
        
        for (String fileName : ffmpegBinaries) {
            try (InputStream is = FFmpegRuntimeExtractor.class.getResourceAsStream("/ffmpeg-runtime/" + fileName)) {
                if (is != null) {
                    Path targetFile = targetDir.resolve(fileName);
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    
                    if (isFFmpegBinary(fileName)) {
                        targetFile.toFile().setExecutable(true);
                    }
                    
                    logger.debug("Extracted from classpath: {}", fileName);
                }
            }
        }
    }
    
    /**
     * Check if a file is an FFmpeg binary that should be made executable
     */
    private static boolean isFFmpegBinary(String fileName) {
        return fileName.equals("ffmpeg") || 
               fileName.equals("ffprobe") ||
               fileName.equals("ffmpeg.exe") || 
               fileName.equals("ffprobe.exe");
    }
    
    /**
     * Get the FFmpeg executable path for the current platform
     */
    public static Path getFFmpegExecutablePath(Path ffmpegRuntimeDir) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return ffmpegRuntimeDir.resolve("ffmpeg.exe");
        } else {
            return ffmpegRuntimeDir.resolve("ffmpeg");
        }
    }
    
    /**
     * Get the FFprobe executable path for the current platform
     */
    public static Path getFFprobeExecutablePath(Path ffmpegRuntimeDir) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return ffmpegRuntimeDir.resolve("ffprobe.exe");
        } else {
            return ffmpegRuntimeDir.resolve("ffprobe");
        }
    }
    
    /**
     * Check if embedded FFmpeg is available
     */
    public static boolean isEmbeddedFFmpegAvailable() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String ffmpegResource = osName.contains("win") ? "/ffmpeg-runtime/ffmpeg.exe" : "/ffmpeg-runtime/ffmpeg";
            return FFmpegRuntimeExtractor.class.getResource(ffmpegResource) != null;
        } catch (Exception e) {
            logger.debug("Error checking for embedded FFmpeg: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get embedded FFmpeg version info
     */
    public static String getEmbeddedFFmpegVersion(Path ffmpegRuntimeDir) {
        try {
            Path ffmpegPath = getFFmpegExecutablePath(ffmpegRuntimeDir);
            if (Files.exists(ffmpegPath)) {
                ProcessBuilder pb = new ProcessBuilder(ffmpegPath.toString(), "-version");
                Process process = pb.start();
                process.waitFor();
                
                // Read first line of output for version info
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String firstLine = reader.readLine();
                    if (firstLine != null && firstLine.contains("version")) {
                        return firstLine.trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error getting embedded FFmpeg version: {}", e.getMessage());
        }
        return "Unknown";
    }
}
