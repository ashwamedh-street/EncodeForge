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
 * Advanced Python runtime extractor that can extract complete directory structures
 * from JAR resources, including Python virtual environments and dependencies.
 */
public class PythonRuntimeExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PythonRuntimeExtractor.class);
    
    /**
     * Extract complete Python runtime bundle from JAR resources
     */
    public static void extractPythonRuntimeBundle(Path targetDir) throws IOException {
        logger.info("Extracting Python runtime bundle to: {}", targetDir);
        
        // Get the JAR file path
        String jarPath = PythonRuntimeExtractor.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();
        
        if (jarPath.endsWith(".jar")) {
            extractFromJar(jarPath, targetDir);
        } else {
            // Running from IDE - extract from classpath resources
            extractFromClasspath(targetDir);
        }
        
        logger.info("Python runtime bundle extraction completed");
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
                
                // Extract Python runtime resources
                if (entryName.startsWith("python-runtime/") && !entry.isDirectory()) {
                    String relativePath = entryName.substring("python-runtime/".length());
                    Path targetFile = targetDir.resolve(relativePath);
                    
                    // Create parent directories
                    Files.createDirectories(targetFile.getParent());
                    
                    // Extract file
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Extracted: {} -> {}", entryName, targetFile.getFileName());
                        
                        // Make executable if it's a script
                        if (isExecutableScript(relativePath)) {
                            targetFile.toFile().setExecutable(true);
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
        // Extract known files from classpath resources
        String[] knownFiles = {
            "python_backend.exe",
            "python_backend.bat",
            "python_backend", 
            "run-python.bat",
            "run-python.sh",
            "requirements.txt"
        };
        
        for (String fileName : knownFiles) {
            try (InputStream is = PythonRuntimeExtractor.class.getResourceAsStream("/python-runtime/" + fileName)) {
                if (is != null) {
                    Path targetFile = targetDir.resolve(fileName);
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    
                    if (isExecutableScript(fileName)) {
                        targetFile.toFile().setExecutable(true);
                    }
                    
                    logger.debug("Extracted from classpath: {}", fileName);
                }
            }
        }
        
        // Extract Python scripts
        String[] pythonScripts = {
            "encodeforge_api.py",
            "encodeforge_core.py",
            "encodeforge_cli.py",
            "encodeforge_webui.py",
            "ffmpeg_core.py",  // Backward compatibility
            "ffmpeg_manager.py",
            "profile_manager.py",
            "subtitle_manager.py",
            "metadata_grabber.py"
        };
        
        Path scriptsDir = targetDir.resolve("scripts");
        Files.createDirectories(scriptsDir);
        
        for (String script : pythonScripts) {
            try (InputStream is = PythonRuntimeExtractor.class.getResourceAsStream("/python/" + script)) {
                if (is != null) {
                    Path targetFile = scriptsDir.resolve(script);
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Extracted Python script: {}", script);
                }
            }
        }
        
        // Extract subtitle_providers package
        String[] subtitleProviders = {
            "__init__.py",
            "base_provider.py",
            "yify_provider.py",
            "addic7ed_provider.py",
            "subdl_provider.py",
            "subf2m_provider.py",
            "kitsunekko_provider.py",
            "jimaku_provider.py",
            "podnapisi_provider.py",
            "subdivx_provider.py",
            "opensubtitles_manager.py",
            "whisper_manager.py"
        };
        
        Path subtitleProvidersDir = scriptsDir.resolve("subtitle_providers");
        Files.createDirectories(subtitleProvidersDir);
        
        for (String provider : subtitleProviders) {
            try (InputStream is = PythonRuntimeExtractor.class.getResourceAsStream("/python/subtitle_providers/" + provider)) {
                if (is != null) {
                    Path targetFile = subtitleProvidersDir.resolve(provider);
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Extracted subtitle provider: {}", provider);
                }
            }
        }
        
        // Extract metadata_providers package
        String[] metadataProviders = {
            "__init__.py",
            "base_provider.py",
            "tmdb_provider.py",
            "tvdb_provider.py",
            "anilist_provider.py",
            "tvmaze_provider.py",
            "kitsu_provider.py",
            "jikan_provider.py",
            "trakt_provider.py",
            "omdb_provider.py"
        };
        
        Path metadataProvidersDir = scriptsDir.resolve("metadata_providers");
        Files.createDirectories(metadataProvidersDir);
        
        for (String provider : metadataProviders) {
            try (InputStream is = PythonRuntimeExtractor.class.getResourceAsStream("/python/metadata_providers/" + provider)) {
                if (is != null) {
                    Path targetFile = metadataProvidersDir.resolve(provider);
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Extracted metadata provider: {}", provider);
                }
            }
        }
        
        // Extract encodeforge_modules package
        String[] encodeforgeModules = {
            "__init__.py",
            "models.py",
            "file_handler.py",
            "subtitle_handler.py",
            "renaming_handler.py",
            "conversion_handler.py"
        };
        
        Path encodeforgeModulesDir = scriptsDir.resolve("encodeforge_modules");
        Files.createDirectories(encodeforgeModulesDir);
        
        for (String module : encodeforgeModules) {
            try (InputStream is = PythonRuntimeExtractor.class.getResourceAsStream("/python/encodeforge_modules/" + module)) {
                if (is != null) {
                    Path targetFile = encodeforgeModulesDir.resolve(module);
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Extracted encodeforge module: {}", module);
                }
            }
        }
    }
    
    /**
     * Check if a file should be made executable
     */
    private static boolean isExecutableScript(String fileName) {
        return fileName.endsWith(".sh") || 
               fileName.endsWith(".bat") || 
               fileName.equals("python_backend") ||
               fileName.equals("python_backend.exe");
    }
    
    /**
     * Get the Python executable path for the current platform
     */
    public static Path getPythonExecutablePath(Path pythonRuntimeDir) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return pythonRuntimeDir.resolve("python_backend.exe");
        } else {
            return pythonRuntimeDir.resolve("python_backend");
        }
    }
    
    /**
     * Get the Python launcher script path for the current platform
     */
    public static Path getPythonLauncherPath(Path pythonRuntimeDir) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return pythonRuntimeDir.resolve("run-python.bat");
        } else {
            return pythonRuntimeDir.resolve("run-python.sh");
        }
    }
}
