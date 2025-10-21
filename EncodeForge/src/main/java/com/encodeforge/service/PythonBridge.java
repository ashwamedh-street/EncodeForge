package com.encodeforge.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Bridge for communicating with Python backend via JSON over stdin/stdout
 */
public class PythonBridge {
    private static final Logger logger = LoggerFactory.getLogger(PythonBridge.class);
    private static final Gson gson = new Gson();
    private static final long TIMEOUT_SECONDS = 300; // 5 minutes for long operations
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() + 2; // CPU cores + 2 for I/O
    
    private Process pythonProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ExecutorService executorService;
    private ExecutorService ioExecutorService; // Separate executor for I/O operations
    private boolean isRunning = false;
    private final Object ioLock = new Object();
    private final Object streamingLock = new Object(); // Serialize streaming commands
    
    public PythonBridge() {
        // Main executor for CPU-bound tasks
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "PythonBridge-Worker");
            t.setDaemon(true);
            return t;
        });
        
        // Separate executor for I/O operations to prevent blocking
        this.ioExecutorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "PythonBridge-IO");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("PythonBridge initialized with {} worker threads", THREAD_POOL_SIZE);
    }
    
    /**
     * Start the Python backend process
     */
    public void start() throws IOException {
        if (isRunning) {
            logger.warn("Python bridge already running");
            return;
        }
        
        Path pythonExecutable = getPythonExecutable();
        Path scriptPath = getPythonScript();
        
        logger.info("Starting Python process");
        logger.info("  Python: {}", pythonExecutable);
        logger.info("  Script: {}", scriptPath.toAbsolutePath());
        
        ProcessBuilder pb = new ProcessBuilder(
            pythonExecutable.toString(),
            "-u",  // Unbuffered mode for real-time progress updates
            scriptPath.toString()
        );
        
        // Set working directory
        pb.directory(scriptPath.getParent().toFile());
        
        // Set environment variable for Python unbuffered mode
        pb.environment().put("PYTHONUNBUFFERED", "1");
        
        // Merge error stream for easier handling
        pb.redirectErrorStream(true);
        
        pythonProcess = pb.start();
        
        reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        
        isRunning = true;
        
        // Wait for the "ready" message from Python (use I/O executor)
        try {
            Future<String> readyFuture = ioExecutorService.submit(() -> reader.readLine());
            String readyLine = readyFuture.get(10, TimeUnit.SECONDS);
            logger.debug("Received startup message: {}", readyLine);
            
            JsonObject readyMessage = gson.fromJson(readyLine, JsonObject.class);
            if (readyMessage.has("status") && "ready".equals(readyMessage.get("status").getAsString())) {
                logger.info("Python bridge started successfully and ready");
            } else {
                logger.warn("Unexpected startup message from Python: {}", readyLine);
            }
        } catch (Exception e) {
            logger.error("Failed to receive ready message from Python", e);
            throw new IOException("Python bridge initialization failed", e);
        }
    }
    
    /**
     * Send a command to Python and get response
     */
    public JsonObject sendCommand(JsonObject command) throws IOException, TimeoutException {
        if (!isRunning) {
            throw new IllegalStateException("Python bridge not running");
        }
        synchronized (ioLock) {
            // Send command
            String commandJson = gson.toJson(command);
            logger.debug("Sending command: {}", commandJson);
            writer.write(commandJson);
            writer.newLine();
            writer.flush();

            // Read response with timeout (use I/O executor for non-blocking I/O)
            Future<String> future = ioExecutorService.submit(() -> reader.readLine());

            try {
                String responseLine = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (responseLine == null) {
                    throw new IOException("Python process closed connection");
                }
                logger.debug("Received response: {} chars", responseLine.length());
                return gson.fromJson(responseLine, JsonObject.class);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error reading response from Python", e);
                throw new IOException("Failed to read response from Python", e);
            } catch (TimeoutException e) {
                logger.error("Python command timed out after {} seconds", TIMEOUT_SECONDS);
                future.cancel(true);
                throw e;
            }
        }
    }
    
    /**
     * Send a command with streaming response (for progress updates)
     * IMPORTANT: Python stdin/stdout is single-threaded, so streaming commands are serialized
     * to prevent response mixing. Multiple files will be searched sequentially.
     */
    public void sendStreamingCommand(JsonObject command, Consumer<JsonObject> progressCallback) throws IOException {
        if (!isRunning) {
            throw new IllegalStateException("Python bridge not running");
        }
        
        ioExecutorService.submit(() -> {
            // Serialize entire streaming command to prevent response mixing
            synchronized (streamingLock) {
                try {
                    // Write command
                    synchronized (ioLock) {
                        String commandJson = gson.toJson(command);
                        logger.debug("Sending streaming command: {}", commandJson);
                        writer.write(commandJson);
                        writer.newLine();
                        writer.flush();
                    }

                    // Read streaming responses
                    String line;
                    boolean receivedFinalResponse = false;
                    while ((line = reader.readLine()) != null && !receivedFinalResponse) {
                    logger.trace("Streaming line: {}", line);
                    JsonObject response = gson.fromJson(line, JsonObject.class);
                    
                    // Pass every response to callback
                    progressCallback.accept(response);
                    
                    // Check if this is the final response
                    // For subtitle search: has "complete": true
                    // For conversion: has "status": "complete" or "error"
                    // For other operations: has "results" field
                    if (response.has("complete") && response.get("complete").getAsBoolean()) {
                        logger.debug("Received final response (complete: true)");
                        receivedFinalResponse = true;
                    } else if (response.has("status")) {
                        String status = response.get("status").getAsString();
                        if ("complete".equals(status) || "error".equals(status)) {
                            logger.debug("Received final response (status: {})", status);
                            receivedFinalResponse = true;
                        }
                    } else if (response.has("results") && !response.has("progress")) {
                        logger.debug("Received final response (has results)");
                        receivedFinalResponse = true;
                    }
                }
                
                    if (!receivedFinalResponse) {
                        logger.warn("Stream ended without receiving final response");
                    }
                } catch (IOException e) {
                    logger.error("Error in streaming command", e);
                    // Send error to callback
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("status", "error");
                    errorResponse.addProperty("message", "Streaming error: " + e.getMessage());
                    errorResponse.addProperty("complete", true);
                    progressCallback.accept(errorResponse);
                }
            } // End streamingLock - ensures one streaming command at a time
        });
    }

    /**
     * Stop current conversion (best-effort cancel).
     */
    public void requestStopConversion() throws IOException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "stop_conversion");
        synchronized (ioLock) {
            String commandJson = gson.toJson(command);
            logger.debug("Sending stop command: {}", commandJson);
            writer.write(commandJson);
            writer.newLine();
            writer.flush();
        }
    }
    
    /**
     * Get all provider/service status information in a single consolidated call.
     * This replaces the need for individual check methods and reduces overhead.
     * 
     * Returns status for:
     * - FFmpeg availability and version
     * - Whisper availability and version
     * - OpenSubtitles configuration and login status
     * - Metadata providers (TMDB, TVDB, OMDB, Trakt, Fanart, AniList, Kitsu, Jikan, TVmaze)
     * - Subtitle providers count
     */
    public JsonObject getAllStatus() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "get_all_status");
        return sendCommand(command);
    }
    
    /**
     * @deprecated Use getAllStatus() instead for better performance
     * Check if FFmpeg is available
     */
    @Deprecated
    public JsonObject checkFFmpeg() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_ffmpeg");
        return sendCommand(command);
    }
    
    /**
     * @deprecated Use getAllStatus() instead for better performance
     * Check Whisper availability
     */
    @Deprecated
    public JsonObject checkWhisper() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_whisper");
        return sendCommand(command);
    }
    
    /**
     * @deprecated Use getAllStatus() instead for better performance
     * Check OpenSubtitles status
     */
    @Deprecated
    public JsonObject checkOpenSubtitles() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_opensubtitles");
        return sendCommand(command);
    }
    
    /**
     * @deprecated Use getAllStatus() instead for better performance
     * Check TMDB status
     */
    @Deprecated
    public JsonObject checkTMDB() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_tmdb");
        return sendCommand(command);
    }
    
    /**
     * @deprecated Use getAllStatus() instead for better performance
     * Check TVDB status
     */
    @Deprecated
    public JsonObject checkTVDB() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_tvdb");
        return sendCommand(command);
    }
    
    /**
     * Scan directory for media files
     */
    public JsonObject scanDirectory(String directory, boolean includeSubdirs) throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "scan_directory");
        command.addProperty("directory", directory);
        command.addProperty("recursive", includeSubdirs);
        return sendCommand(command);
    }
    
    /**
     * Get detailed file information
     */
    public JsonObject getFileInfo(String filePath) throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "get_file_info");
        command.addProperty("file_path", filePath);
        return sendCommand(command);
    }
    
    /**
     * Start conversion with progress callback
     */
    public void convertFiles(JsonObject settings, List<String> filePaths, Consumer<JsonObject> progressCallback) throws IOException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "convert_files");
        command.add("settings", settings);
        command.add("file_paths", gson.toJsonTree(filePaths));
        
        sendStreamingCommand(command, progressCallback);
    }
    
    /**
     * Get hardware capabilities
     */
    public JsonObject getCapabilities() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "get_capabilities");
        return sendCommand(command);
    }
    
    /**
     * Shutdown the Python process
     */
    public void shutdown() {
        logger.info("Shutting down Python bridge");
        isRunning = false;
        
        try {
            if (writer != null) {
                // Send shutdown command
                JsonObject command = new JsonObject();
                command.addProperty("action", "shutdown");
                writer.write(gson.toJson(command));
                writer.newLine();
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            logger.warn("Error sending shutdown command", e);
        }
        
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            try {
                if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("Python process did not terminate, forcing...");
                    pythonProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for Python process", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown both executors
        executorService.shutdown();
        ioExecutorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!ioExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            ioExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Python bridge shutdown complete");
    }
    
    /**
     * Get path to Python executable (bundled, venv, or system)
     */
    private Path getPythonExecutable() throws IOException {
        // First, check if we have an extracted Python runtime
        String runtimeDir = System.getProperty("python.runtime.dir");
        if (runtimeDir != null) {
            Path runtimePath = Paths.get(runtimeDir);
            Path bundledExecutable = com.encodeforge.util.PythonRuntimeExtractor.getPythonExecutablePath(runtimePath);
            
            if (Files.exists(bundledExecutable)) {
                logger.info("Using bundled Python executable: {}", bundledExecutable);
                return bundledExecutable;
            }
        }
        
        // Second, check for venv in project directory (for development/Whisper support)
        Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
        Path venvPython = null;
        
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            venvPython = workspaceRoot.resolve("venv").resolve("Scripts").resolve("python.exe");
        } else {
            venvPython = workspaceRoot.resolve("venv").resolve("bin").resolve("python3");
        }
        
        if (venvPython != null && Files.exists(venvPython)) {
            logger.info("Using project venv Python: {}", venvPython);
            return venvPython;
        }
        
        // Fallback to system Python
        String pythonCommand = System.getProperty("os.name").toLowerCase().contains("win") ? "python.exe" : "python3";
        logger.info("Using system Python: {}", pythonCommand);
        return Paths.get(pythonCommand);
    }
    
    /**
     * Get path to Python API script
     */
    private Path getPythonScript() throws IOException {
        // First try extracted runtime directory
        String runtimeDir = System.getProperty("python.runtime.dir");
        if (runtimeDir != null) {
            Path runtimePath = Paths.get(runtimeDir);
            
            // Try encodeforge_api.py first (new name)
            Path scriptPath = runtimePath.resolve("scripts").resolve("encodeforge_api.py");
            if (Files.exists(scriptPath)) {
                logger.info("Using bundled Python script: {}", scriptPath);
                return scriptPath;
            }
            
            scriptPath = runtimePath.resolve("encodeforge_api.py");
            if (Files.exists(scriptPath)) {
                logger.info("Using bundled Python API script: {}", scriptPath);
                return scriptPath;
            }
            
            // Fallback to old name for backward compatibility
            scriptPath = runtimePath.resolve("scripts").resolve("ffmpeg_api.py");
            if (Files.exists(scriptPath)) {
                logger.info("Using bundled Python script (legacy): {}", scriptPath);
                return scriptPath;
            }
            
            scriptPath = runtimePath.resolve("ffmpeg_api.py");
            if (Files.exists(scriptPath)) {
                logger.info("Using bundled Python API script (legacy): {}", scriptPath);
                return scriptPath;
            }
        }
        
        // Fallback to project directory (for development)
        // Try new name first
        Path devScriptPath = Paths.get("src", "main", "resources", "python", "encodeforge_api.py");
        if (Files.exists(devScriptPath)) {
            logger.info("Using development Python script: {}", devScriptPath);
            return devScriptPath;
        }
        
        // Try old name for backward compatibility
        devScriptPath = Paths.get("src", "main", "resources", "python", "ffmpeg_api.py");
        if (Files.exists(devScriptPath)) {
            logger.info("Using development Python script (legacy): {}", devScriptPath);
            return devScriptPath;
        }
        
        // Try parent directory (old location)
        Path parentScriptPath = Paths.get("encodeforge_api.py");
        if (Files.exists(parentScriptPath)) {
            logger.info("Using parent directory Python script: {}", parentScriptPath);
            return parentScriptPath;
        }
        
        parentScriptPath = Paths.get("ffmpeg_api.py");
        if (Files.exists(parentScriptPath)) {
            logger.info("Using parent directory Python script (legacy): {}", parentScriptPath);
            return parentScriptPath;
        }
        
        throw new IOException("Python API script not found. Checked:\n" +
            "  - " + (runtimeDir != null ? Paths.get(runtimeDir, "scripts", "encodeforge_api.py") : "(runtime dir not set)") + "\n" +
            "  - " + (runtimeDir != null ? Paths.get(runtimeDir, "encodeforge_api.py") : "(runtime dir not set)") + "\n" +
            "  - " + Paths.get("src", "main", "resources", "python", "encodeforge_api.py") + "\n" +
            "  - " + Paths.get("src", "main", "resources", "python", "ffmpeg_api.py") + " (legacy)\n" +
            "  - " + Paths.get("encodeforge_api.py") + "\n" +
            "  - " + Paths.get("ffmpeg_api.py") + " (legacy)");
    }
    
    public boolean isRunning() {
        return isRunning && pythonProcess != null && pythonProcess.isAlive();
    }
}

