package com.encodeforge.service;

import com.encodeforge.model.ProgressUpdate;
import com.encodeforge.util.DownloadManager;
import com.encodeforge.util.PathManager;
import com.encodeforge.util.PythonRuntimeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages all external dependencies: FFmpeg, Python libraries, etc.
 */
public class DependencyManager {
    private static final Logger logger = LoggerFactory.getLogger(DependencyManager.class);
    
    private final DownloadManager downloadManager;
    private final Path dependenciesDir;
    private final Path ffmpegDir;
    private final Path pythonLibsDir;
    
    // Platform-specific FFmpeg download URLs
    private static final Map<String, String> FFMPEG_URLS = Map.of(
        "windows", "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
        "linux", "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz",
        "mac", "https://evermeet.cx/ffmpeg/getrelease/zip"
    );
    
    public DependencyManager() throws IOException {
        this.downloadManager = new DownloadManager();
        this.dependenciesDir = PathManager.getBaseDir().resolve("dependencies");
        this.ffmpegDir = dependenciesDir.resolve("ffmpeg");
        this.pythonLibsDir = dependenciesDir.resolve("python-libs");
        
        // Create directories
        Files.createDirectories(dependenciesDir);
        Files.createDirectories(ffmpegDir);
        Files.createDirectories(pythonLibsDir);
        
        logger.info("DependencyManager initialized");
        logger.info("  Dependencies dir: {}", dependenciesDir);
        logger.info("  FFmpeg dir: {}", ffmpegDir);
        logger.info("  Python libs dir: {}", pythonLibsDir);
    }
    
    // ===========================
    // FFmpeg Management
    // ===========================
    
    /**
     * Check if FFmpeg is available (system PATH or installed by app)
     */
    public CompletableFuture<Boolean> checkFFmpeg() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Checking for FFmpeg...");
            
            // First check if we've installed it
            Path installedFfmpeg = getInstalledFFmpegPath();
            if (installedFfmpeg != null && Files.exists(installedFfmpeg)) {
                logger.info("FFmpeg found (installed): {}", installedFfmpeg);
                return true;
            }
            
            // Check system PATH
            if (isCommandAvailable("ffmpeg")) {
                logger.info("FFmpeg found in system PATH");
                return true;
            }
            
            logger.info("FFmpeg not found");
            return false;
        });
    }
    
    /**
     * Install FFmpeg to app directory
     */
    public CompletableFuture<Void> installFFmpeg(Consumer<ProgressUpdate> callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                callback.accept(new ProgressUpdate("detecting", 0, "Detecting platform...", ""));
                
                String platform = getPlatform();
                String downloadUrl = FFMPEG_URLS.get(platform);
                
                if (downloadUrl == null) {
                    throw new IOException("FFmpeg download not supported for platform: " + platform);
                }
                
                callback.accept(new ProgressUpdate("downloading", 5, "Downloading FFmpeg...", downloadUrl));
                
                // Download FFmpeg
                Path downloadPath = dependenciesDir.resolve("ffmpeg-download.zip");
                downloadManager.downloadFile(downloadUrl, downloadPath, 
                    progress -> callback.accept(new ProgressUpdate("downloading", 
                        5 + (int)(progress.getProgress() * 0.7), // 5-75%
                        "Downloading FFmpeg: " + progress.getMessage(), 
                        "")))
                    .get();
                
                callback.accept(new ProgressUpdate("installing", 75, "Extracting FFmpeg...", ""));
                
                // Extract
                downloadManager.extractArchive(downloadPath, ffmpegDir);
                
                // Make binaries executable on Unix
                makeExecutable(getInstalledFFmpegPath());
                makeExecutable(getInstalledFFprobePath());
                
                callback.accept(new ProgressUpdate("verifying", 95, "Verifying installation...", ""));
                
                // Verify it works
                if (!testFFmpeg()) {
                    throw new IOException("FFmpeg installation verification failed");
                }
                
                // Clean up download
                Files.deleteIfExists(downloadPath);
                
                callback.accept(new ProgressUpdate("complete", 100, "FFmpeg installed successfully", ""));
                logger.info("FFmpeg installation complete");
                
            } catch (Exception e) {
                logger.error("Failed to install FFmpeg", e);
                callback.accept(new ProgressUpdate("error", 0, "Failed to install FFmpeg: " + e.getMessage(), ""));
                throw new RuntimeException("FFmpeg installation failed", e);
            }
        });
    }
    
    /**
     * Get path to installed FFmpeg executable
     */
    public Path getInstalledFFmpegPath() {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Check common locations in our install directory
        List<Path> possiblePaths = new ArrayList<>();
        
        if (os.contains("win")) {
            possiblePaths.add(ffmpegDir.resolve("bin/ffmpeg.exe"));
            possiblePaths.add(ffmpegDir.resolve("ffmpeg.exe"));
        } else {
            possiblePaths.add(ffmpegDir.resolve("bin/ffmpeg"));
            possiblePaths.add(ffmpegDir.resolve("ffmpeg"));
        }
        
        // Also check subdirectories (some archives extract to versioned folders)
        try {
            Files.list(ffmpegDir)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    if (os.contains("win")) {
                        possiblePaths.add(dir.resolve("bin/ffmpeg.exe"));
                        possiblePaths.add(dir.resolve("ffmpeg.exe"));
                    } else {
                        possiblePaths.add(dir.resolve("bin/ffmpeg"));
                        possiblePaths.add(dir.resolve("ffmpeg"));
                    }
                });
        } catch (IOException e) {
            logger.debug("Error listing ffmpeg directory", e);
        }
        
        return possiblePaths.stream()
            .filter(Files::exists)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get path to installed FFprobe executable
     */
    public Path getInstalledFFprobePath() {
        Path ffmpegPath = getInstalledFFmpegPath();
        if (ffmpegPath == null) return null;
        
        String fileName = ffmpegPath.getFileName().toString();
        String ffprobeName = fileName.replace("ffmpeg", "ffprobe");
        
        return ffmpegPath.getParent().resolve(ffprobeName);
    }
    
    private boolean testFFmpeg() {
        try {
            Path ffmpegPath = getInstalledFFmpegPath();
            if (ffmpegPath == null) return false;
            
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath.toString(), "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("FFmpeg test failed", e);
            return false;
        }
    }
    
    // ===========================
    // Python Library Management
    // ===========================
    
    /**
     * Check which required libraries are installed
     */
    public CompletableFuture<Map<String, Boolean>> checkRequiredLibraries() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Checking required Python libraries...");
            
            List<String> required = Arrays.asList("requests", "pandas", "streamlit");
            Map<String, Boolean> status = new HashMap<>();
            
            for (String lib : required) {
                boolean installed = checkPythonPackage(lib);
                status.put(lib, installed);
                logger.info("  {} : {}", lib, installed ? "✓" : "✗");
            }
            
            return status;
        });
    }
    
    /**
     * Check which optional libraries are installed
     */
    public CompletableFuture<Map<String, Boolean>> checkOptionalLibraries() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Checking optional Python libraries...");
            
            List<String> optional = Arrays.asList("whisper", "torch", "numba");
            Map<String, Boolean> status = new HashMap<>();
            
            for (String lib : optional) {
                boolean installed = checkPythonPackage(lib);
                status.put(lib, installed);
                logger.info("  {} : {}", lib, installed ? "✓" : "✗");
            }
            
            return status;
        });
    }
    
    /**
     * Install required Python libraries
     */
    public CompletableFuture<Void> installRequiredLibraries(Consumer<ProgressUpdate> callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Read requirements from requirements-core.txt
                List<String> packages = readRequirementsFile("requirements-core.txt");
                
                if (packages.isEmpty()) {
                    logger.info("No Python packages required - requirements-core.txt is empty");
                    callback.accept(new ProgressUpdate("complete", 100, 
                        "No Python packages required", ""));
                    return;
                }
                
                logger.info("Installing {} required Python packages", packages.size());
                installPythonPackages(packages, callback).get();
                
            } catch (Exception e) {
                logger.error("Failed to install required Python libraries", e);
                callback.accept(new ProgressUpdate("error", 0, 
                    "Failed to install required libraries: " + e.getMessage(), ""));
                throw new RuntimeException("Required Python library installation failed", e);
            }
        });
    }
    
    /**
     * Install optional Python libraries
     */
    public CompletableFuture<Void> installOptionalLibraries(List<String> packageSpecs, Consumer<ProgressUpdate> callback) {
        return installPythonPackages(packageSpecs, callback);
    }
    
    /**
     * Read requirements file and return list of package specifications
     */
    private List<String> readRequirementsFile(String filename) throws IOException {
        List<String> packages = new ArrayList<>();
        
        try (InputStream is = getClass().getResourceAsStream("/python/" + filename)) {
            if (is == null) {
                logger.warn("Requirements file not found: {}", filename);
                return packages;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Skip empty lines and comments
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        packages.add(line);
                    }
                }
            }
        }
        
        logger.info("Read {} packages from {}", packages.size(), filename);
        return packages;
    }
    
    /**
     * Install Python packages
     */
    private CompletableFuture<Void> installPythonPackages(List<String> packageSpecs, Consumer<ProgressUpdate> callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path pythonExe = getPythonExecutable();
                
                callback.accept(new ProgressUpdate("installing", 0, 
                    "Preparing to install Python packages...", ""));
                
                int totalPackages = packageSpecs.size();
                int current = 0;
                
                for (String packageSpec : packageSpecs) {
                    current++;
                    int progressPercent = (current * 100) / totalPackages;
                    
                    callback.accept(new ProgressUpdate("installing", progressPercent,
                        String.format("Installing %s (%d/%d)...", packageSpec, current, totalPackages),
                        ""));
                    
                    installPythonPackage(pythonExe, packageSpec);
                    
                    logger.info("Installed: {}", packageSpec);
                }
                
                callback.accept(new ProgressUpdate("complete", 100, 
                    "All packages installed successfully", ""));
                
            } catch (Exception e) {
                logger.error("Failed to install Python packages", e);
                callback.accept(new ProgressUpdate("error", 0, 
                    "Failed to install packages: " + e.getMessage(), ""));
                throw new RuntimeException("Python package installation failed", e);
            }
        });
    }
    
    /**
     * Install a single Python package
     */
    private void installPythonPackage(Path pythonExe, String packageSpec) throws IOException, InterruptedException {
        logger.info("Installing Python package: {}", packageSpec);
        
        // Install to our custom directory
        List<String> command = Arrays.asList(
            pythonExe.toString(),
            "-m", "pip", "install",
            "--target", pythonLibsDir.toString(),
            "--upgrade",
            packageSpec
        );
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("pip: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("pip install failed with exit code: " + exitCode);
        }
    }
    
    /**
     * Check if a Python package is installed/importable
     */
    private boolean checkPythonPackage(String packageName) {
        try {
            Path pythonExe = getPythonExecutable();
            
            // Try importing the package
            ProcessBuilder pb = new ProcessBuilder(
                pythonExe.toString(),
                "-c",
                String.format("import %s", packageName)
            );
            
            // Set PYTHONPATH to include our custom libs directory
            Map<String, String> env = pb.environment();
            env.put("PYTHONPATH", pythonLibsDir.toString());
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            return exitCode == 0;
            
        } catch (Exception e) {
            logger.debug("Package check failed for {}: {}", packageName, e.getMessage());
            return false;
        }
    }
    
    // ===========================
    // Python Executable Detection
    // ===========================
    
    /**
     * Get Python executable path (bundled or system)
     */
    public Path getPythonExecutable() throws IOException {
        // First try bundled Python from resources
        try {
            Path bundledPython = PythonRuntimeExtractor.getPythonExecutablePath(
                PathManager.getBaseDir().resolve("python")
            );
            if (bundledPython != null && Files.exists(bundledPython)) {
                logger.info("Using bundled Python: {}", bundledPython);
                return bundledPython;
            }
        } catch (Exception e) {
            logger.debug("Bundled Python not available", e);
        }
        
        // Fall back to system Python
        String os = System.getProperty("os.name").toLowerCase();
        
        // On Windows, try python first (python3 doesn't exist)
        // On Unix, try python3 first (python might be Python 2)
        List<String> pythonCommands = os.contains("win") 
            ? Arrays.asList("python", "python3")
            : Arrays.asList("python3", "python");
        
        for (String cmd : pythonCommands) {
            if (isCommandAvailable(cmd)) {
                logger.info("Using system Python: {}", cmd);
                return Paths.get(cmd);
            }
        }
        
        throw new IOException("Python not found. Please install Python 3.8 or higher.");
    }
    
    /**
     * Get PYTHONPATH environment variable value
     */
    public String getPythonPath() {
        return pythonLibsDir.toString();
    }
    
    // ===========================
    // Utility Methods
    // ===========================
    
    /**
     * Get platform identifier
     */
    private String getPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        if (os.contains("linux")) return "linux";
        return "unknown";
    }
    
    /**
     * Check if a command is available in PATH
     */
    private boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("win")) {
                pb = new ProcessBuilder("where", command);
            } else {
                pb = new ProcessBuilder("which", command);
            }
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            logger.debug("Command availability check failed for {}: {}", command, e.getMessage());
            return false;
        }
    }
    
    /**
     * Make file executable (Unix)
     */
    private void makeExecutable(Path path) {
        if (path != null && Files.exists(path)) {
            try {
                path.toFile().setExecutable(true);
            } catch (Exception e) {
                logger.warn("Failed to make executable: {}", path, e);
            }
        }
    }
    
    public Path getPythonLibsDir() {
        return pythonLibsDir;
    }
    
    public Path getFfmpegDir() {
        return ffmpegDir;
    }
}

