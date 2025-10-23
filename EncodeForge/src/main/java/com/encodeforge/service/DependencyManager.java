package com.encodeforge.service;

import com.encodeforge.model.ProgressUpdate;
import com.encodeforge.util.DownloadManager;
import com.encodeforge.util.PathManager;
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
            
            // Only subtitle provider dependencies - core libraries like requests/pandas/streamlit
            // are handled separately as optional/AI dependencies
            List<String> required = Arrays.asList("bs4", "lxml");
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
     * Install PyTorch with GPU support (ROCm for AMD, CUDA for NVIDIA)
     * Platform-aware: respects official PyTorch support matrix
     */
    public CompletableFuture<Void> installTorchWithGPUSupport(Consumer<ProgressUpdate> callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path pythonExe = getPythonExecutable();
                String osName = System.getProperty("os.name").toLowerCase();
                boolean isWindows = osName.contains("win");
                boolean isLinux = osName.contains("linux");
                
                callback.accept(new ProgressUpdate("installing", 0, 
                    "Detecting GPU and installing PyTorch...", ""));
                
                // Use existing HardwareDetector to check what encoders are available
                Map<String, Boolean> encoders = com.encodeforge.util.HardwareDetector.detectEncoders();
                
                // Determine GPU type and installation strategy based on detected hardware and OS
                String gpuType = "cpu";
                String installReason = "";
                
                if (encoders.get("h264_amf") || encoders.get("hevc_amf")) {
                    // AMD GPU detected
                    if (isLinux) {
                        gpuType = "rocm";
                        installReason = "AMD GPU detected on Linux - installing PyTorch with ROCm support";
                        logger.info(installReason);
                    } else if (isWindows) {
                        gpuType = "cpu";
                        installReason = "AMD GPU detected on Windows - GPU acceleration requires preview drivers. Installing CPU-only PyTorch.";
                        logger.info("AMD GPU detected on Windows");
                        logger.info("Note: PyTorch GPU support for AMD on Windows is currently in preview and requires:");
                        logger.info("  - Specific preview drivers (25.20.01.14+)");
                        logger.info("  - Supported GPU series only");
                        logger.info("  - Manual installation from AMD's ROCm repository");
                        logger.info("Installing CPU-only PyTorch for stability. Whisper will still work, just slower.");
                        logger.info("For GPU acceleration info: https://www.amd.com/en/resources/support-articles/release-notes/RN-AMDGPU-WINDOWS-PYTORCH-PREVIEW.html");
                    } else {
                        gpuType = "cpu";
                        installReason = "AMD GPU detected but unsupported OS - installing CPU-only PyTorch";
                        logger.info(installReason);
                    }
                } else if (encoders.get("h264_nvenc") || encoders.get("hevc_nvenc")) {
                    // NVIDIA GPU detected
                    if (isWindows || isLinux) {
                        gpuType = "cuda";
                        installReason = "NVIDIA GPU detected - installing PyTorch with CUDA support";
                        logger.info(installReason);
                    } else {
                        gpuType = "cpu";
                        installReason = "NVIDIA GPU detected but unsupported OS - installing CPU-only PyTorch";
                        logger.info(installReason);
                    }
                } else {
                    installReason = "No compatible GPU encoders detected - installing CPU-only PyTorch";
                    logger.info(installReason);
                }
                
                callback.accept(new ProgressUpdate("installing", 20, 
                    installReason, ""));
                
                // Build pip install command based on GPU type
                List<String> command = new ArrayList<>();
                command.add(pythonExe.toString());
                command.add("-m");
                command.add("pip");
                command.add("install");
                command.add("--target");
                command.add(pythonLibsDir.toString());
                command.add("--upgrade");
                
                if ("rocm".equalsIgnoreCase(gpuType)) {
                    // AMD ROCm support (Linux only)
                    logger.info("Installing PyTorch with ROCm support (Linux)");
                    callback.accept(new ProgressUpdate("installing", 30, 
                        "Installing PyTorch with ROCm support for AMD GPU...", ""));
                    
                    command.add("torch");
                    command.add("torchvision");
                    command.add("torchaudio");
                    command.add("--index-url");
                    command.add("https://download.pytorch.org/whl/rocm6.2");
                    
                } else if ("cuda".equalsIgnoreCase(gpuType)) {
                    // NVIDIA CUDA support (Windows/Linux)
                    logger.info("Installing PyTorch with CUDA support");
                    callback.accept(new ProgressUpdate("installing", 30, 
                        "Installing PyTorch with CUDA support for NVIDIA GPU...", ""));
                    
                    command.add("torch");
                    command.add("torchvision");
                    command.add("torchaudio");
                    command.add("--index-url");
                    command.add("https://download.pytorch.org/whl/cu126");  // CUDA 12.6
                    
                } else {
                    // CPU-only (all platforms)
                    logger.info("Installing PyTorch CPU-only version");
                    callback.accept(new ProgressUpdate("installing", 30, 
                        "Installing CPU-only PyTorch (stable, cross-platform)...", ""));
                    
                    command.add("torch");
                    command.add("torchvision");
                    command.add("torchaudio");
                    // No --index-url means use default PyPI (CPU version)
                }
                
                // Run pip install
                logger.debug("Running pip command: {}", String.join(" ", command));
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                // Capture output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("pip: {}", line);
                        output.append(line).append("\n");
                        
                        // Update progress based on pip output
                        if (line.contains("Downloading") || line.contains("downloading")) {
                            callback.accept(new ProgressUpdate("installing", 50, 
                                "Downloading PyTorch packages...", line));
                        } else if (line.contains("Installing") || line.contains("installing")) {
                            callback.accept(new ProgressUpdate("installing", 80, 
                                "Installing PyTorch...", line));
                        }
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("PyTorch installation failed with exit code: " + exitCode + "\n" + output.toString());
                }
                
                String successMsg = gpuType.equalsIgnoreCase("cpu") 
                    ? "PyTorch installed successfully (CPU mode - stable and reliable)"
                    : "PyTorch installed successfully with " + gpuType.toUpperCase() + " GPU support";
                    
                callback.accept(new ProgressUpdate("complete", 100, successMsg, ""));
                
                logger.info("PyTorch installed successfully (mode: {})", gpuType.toUpperCase());
                
            } catch (Exception e) {
                logger.error("Failed to install PyTorch", e);
                callback.accept(new ProgressUpdate("error", 0, 
                    "Failed to install PyTorch: " + e.getMessage(), ""));
                throw new RuntimeException("PyTorch installation failed", e);
            }
        });
    }
    
    /**
     * Uninstall Whisper AI and related packages
     */
    public CompletableFuture<Void> uninstallWhisper(Consumer<ProgressUpdate> callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path pythonExe = getPythonExecutable();
                
                callback.accept(new ProgressUpdate("uninstalling", 0, 
                    "Uninstalling Whisper AI...", ""));
                
                // List of Whisper-related packages to uninstall
                List<String> whisperPackages = Arrays.asList(
                    "openai-whisper",
                    "whisper"
                );
                
                int total = whisperPackages.size();
                int current = 0;
                
                for (String packageName : whisperPackages) {
                    current++;
                    int progress = (current * 100) / total;
                    
                    callback.accept(new ProgressUpdate("uninstalling", progress,
                        String.format("Uninstalling %s (%d/%d)...", packageName, current, total),
                        ""));
                    
                    uninstallPythonPackage(pythonExe, packageName);
                    logger.info("Uninstalled: {}", packageName);
                }
                
                // Delete Whisper models directory
                try {
                    Path modelsDir = PathManager.getModelsDir().resolve("whisper");
                    if (Files.exists(modelsDir)) {
                        logger.info("Deleting Whisper models directory: {}", modelsDir);
                        deleteDirectory(modelsDir);
                        callback.accept(new ProgressUpdate("uninstalling", 90, 
                            "Deleted Whisper models", ""));
                    }
                } catch (Exception e) {
                    logger.warn("Could not delete Whisper models directory", e);
                }
                
                callback.accept(new ProgressUpdate("complete", 100, 
                    "Whisper AI uninstalled successfully", ""));
                
            } catch (Exception e) {
                logger.error("Failed to uninstall Whisper", e);
                callback.accept(new ProgressUpdate("error", 0, 
                    "Failed to uninstall Whisper: " + e.getMessage(), ""));
                throw new RuntimeException("Whisper uninstallation failed", e);
            }
        });
    }
    
    /**
     * Uninstall a single Python package
     */
    private void uninstallPythonPackage(Path pythonExe, String packageName) throws IOException, InterruptedException {
        logger.info("Uninstalling Python package: {}", packageName);
        
        // Check if we're running in a venv
        boolean isVenv = isVenvEnvironment(pythonExe);
        
        List<String> command;
        if (isVenv) {
            // Development mode: uninstall from venv
            command = Arrays.asList(
                pythonExe.toString(),
                "-m", "pip", "uninstall",
                "-y",  // Yes to all prompts
                packageName
            );
        } else {
            // Production mode: uninstall from custom directory
            // Note: --target installs don't support uninstall, so we delete manually
            logger.info("Skipping pip uninstall (using --target install), will delete manually");
            return;
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Discard output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("pip uninstall: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.warn("pip uninstall returned non-zero exit code: {}", exitCode);
        }
    }
    
    /**
     * Delete a directory and all its contents
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        try (java.util.stream.Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Could not delete: {}", path, e);
                    }
                });
        }
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
                        // Keep version constraints for proper dependency resolution
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
        
        // ALWAYS install to custom directory for production usage
        // Even in dev/venv environments, optional packages like Whisper should go to python-libs
        // so workers can find them via PYTHONPATH
        logger.info("Installing to custom directory: {}", pythonLibsDir);
        List<String> command = Arrays.asList(
            pythonExe.toString(),
            "-m", "pip", "install",
            "--target", pythonLibsDir.toString(),
            "--upgrade",
            packageSpec
        );
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        // Log the full command for debugging
        logger.debug("Running pip command: {}", String.join(" ", command));
        
        Process process = pb.start();
        
        // Capture output for error analysis
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("pip: {}", line);
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorOutput = output.toString();
            
            // Check for Python version incompatibility
            if (errorOutput.contains("only versions") && errorOutput.contains("are supported")) {
                String pythonVersion = getPythonVersion(pythonExe);
                throw new IOException(String.format(
                    "Python version incompatibility detected!\n\n" +
                    "Python version: %s\n" +
                    "Package %s requires Python versions that don't include %s.\n\n" +
                    "Please install Python 3.8-3.13 or wait for updated packages.\n\n" +
                    "Error details:\n%s",
                    pythonVersion, packageSpec, pythonVersion, errorOutput
                ));
            }
            
            throw new IOException("pip install failed with exit code: " + exitCode + "\n" + errorOutput);
        }
    }
    
    /**
     * Get Python version string
     */
    private String getPythonVersion(Path pythonExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                pythonExe.toString(),
                "--version"
            );
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String version = reader.readLine();
                process.waitFor();
                return version != null ? version : "Unknown";
            }
        } catch (Exception e) {
            logger.debug("Could not get Python version", e);
            return "Unknown";
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
        String os = System.getProperty("os.name").toLowerCase();
        
        // Try bundled Python first (extracted by jpackage to app directory)
        // Check multiple possible locations where bundled Python might be
        List<Path> bundledPaths = new ArrayList<>();
        
        // Path where jpackage extracts bundled resources
        Path appDir = PathManager.getBaseDir();
        bundledPaths.add(appDir.resolve("python").resolve("python_backend.exe"));
        bundledPaths.add(appDir.resolve("python").resolve("python_backend"));
        bundledPaths.add(appDir.resolve("python-runtime").resolve("python_backend.exe"));
        bundledPaths.add(appDir.resolve("python-runtime").resolve("python_backend"));
        
        // Check if running from jpackage bundle
        if (os.contains("win")) {
            // On Windows, jpackage may bundle resources differently
            bundledPaths.add(appDir.resolve("app").resolve("python").resolve("python_backend.exe"));
        }
        
        for (Path bundledPath : bundledPaths) {
            if (Files.exists(bundledPath)) {
                String version = getPythonVersion(bundledPath);
                logger.info("Using bundled Python: {} (version: {})", bundledPath, version);
                return bundledPath;
            }
        }
        
        logger.debug("Bundled Python not found, trying system Python");
        
        // Try to find Python in multiple ways
        List<String> pythonCommands = os.contains("win") 
            ? Arrays.asList("python", "python3", "py")
            : Arrays.asList("python3", "python");
        
        // First try: standard commands
        for (String cmd : pythonCommands) {
            if (isCommandAvailable(cmd)) {
                Path pythonPath = Paths.get(cmd);
                String version = getPythonVersion(pythonPath);
                
                // Check if version is compatible (3.8 to 3.13)
                if (isPythonVersionCompatible(version)) {
                    logger.info("Using system Python: {} (version: {})", cmd, version);
                    return pythonPath;
                } else {
                    // Allow non-compatible versions with a warning (useful for development)
                    logger.warn("Python version {} detected. Whisper may not work properly. Recommended: Python 3.8-3.13", version);
                    logger.warn("Using Python {} anyway (development mode). For production, install Python 3.8-3.13", version);
                    return pythonPath;
                }
            }
        }
        
        // Second try: Check PATH for python3.x executables
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] pathDirs = pathEnv.split(os.contains("win") ? ";" : ":");
            for (String dir : pathDirs) {
                try {
                    Path dirPath = Paths.get(dir);
                    if (Files.isDirectory(dirPath)) {
                        // Look for python3.x executables
                        try (var stream = Files.list(dirPath)) {
                            for (Path file : stream.toList()) {
                                String fileName = file.getFileName().toString().toLowerCase();
                                if (fileName.startsWith("python3.") && !fileName.endsWith(".exe-config")) {
                                    String version = getPythonVersion(file);
                                    if (version != null && !version.isEmpty() && !version.equals("Unknown")) {
                                        logger.info("Found Python in PATH: {} (version: {})", file, version);
                                        if (isPythonVersionCompatible(version)) {
                                            return file;
                                        } else {
                                            logger.warn("Python version {} detected. Using anyway for development", version);
                                            return file;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error checking PATH directory: {}", dir, e);
                }
            }
        }
        
        throw new IOException("Python not found. Please install Python 3.8 or higher.");
    }
    
    /**
     * Check if Python version is compatible with numba/Whisper
     * Requires Python 3.8-3.13 (Python 3.14 has compatibility issues)
     */
    private boolean isPythonVersionCompatible(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        
        // Parse major.minor version (e.g., "Python 3.14.0" -> 3.14)
        try {
            String[] parts = version.trim().split("\\s+");
            for (String part : parts) {
                if (part.startsWith("3.")) {
                    String[] versionParts = part.split("\\.");
                    if (versionParts.length >= 2) {
                        int major = Integer.parseInt(versionParts[0]);
                        int minor = Integer.parseInt(versionParts[1]);
                        
                        // Check if version is 3.8 to 3.13
                        if (major == 3 && minor >= 8 && minor <= 13) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse Python version: {}", version);
        }
        
        return false;
    }
    
    /**
     * Get PYTHONPATH environment variable value
     */
    public String getPythonPath() {
        return pythonLibsDir.toString();
    }
    
    /**
     * Check if the Python executable is from a virtual environment
     */
    private boolean isVenvEnvironment(Path pythonExe) {
        try {
            // Run python -c "import sys; print(sys.prefix)"
            ProcessBuilder pb = new ProcessBuilder(
                pythonExe.toString(),
                "-c", "import sys; print(sys.prefix)"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String prefix = output.toString().trim();
                // Check if prefix contains 'venv' or is different from system Python
                boolean isVenv = prefix.toLowerCase().contains("venv") || 
                                !prefix.equals(System.getProperty("user.home"));
                logger.debug("Python prefix: {}, isVenv: {}", prefix, isVenv);
                return isVenv;
            }
        } catch (Exception e) {
            logger.debug("Could not check venv status", e);
        }
        return false;
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

