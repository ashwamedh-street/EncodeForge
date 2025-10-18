package com.ffmpeg.gui.service;

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
    
    private Process pythonProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ExecutorService executorService;
    private boolean isRunning = false;
    
    public PythonBridge() {
        this.executorService = Executors.newFixedThreadPool(2);
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
            scriptPath.toString()
        );
        
        // Set working directory
        pb.directory(scriptPath.getParent().toFile());
        
        // Merge error stream for easier handling
        pb.redirectErrorStream(true);
        
        pythonProcess = pb.start();
        
        reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        
        isRunning = true;
        
        // Wait for the "ready" message from Python
        try {
            Future<String> readyFuture = executorService.submit(() -> reader.readLine());
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
        
        // Send command
        String commandJson = gson.toJson(command);
        logger.debug("Sending command: {}", commandJson);
        
        writer.write(commandJson);
        writer.newLine();
        writer.flush();
        
        // Read response with timeout
        Future<String> future = executorService.submit(() -> reader.readLine());
        
        try {
            String responseLine = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.debug("Received response: {}", responseLine);
            
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
    
    /**
     * Send a command with streaming response (for progress updates)
     */
    public void sendStreamingCommand(JsonObject command, Consumer<JsonObject> progressCallback) throws IOException {
        if (!isRunning) {
            throw new IllegalStateException("Python bridge not running");
        }
        
        executorService.submit(() -> {
            try {
                String commandJson = gson.toJson(command);
                logger.debug("Sending streaming command: {}", commandJson);
                
                writer.write(commandJson);
                writer.newLine();
                writer.flush();
                
                // Read streaming responses
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonObject response = gson.fromJson(line, JsonObject.class);
                    
                    // Check if this is a progress update or final response
                    if (response.has("progress")) {
                        progressCallback.accept(response);
                    } else if (response.has("status")) {
                        // Final response
                        progressCallback.accept(response);
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Error in streaming command", e);
            }
        });
    }
    
    /**
     * Check if FFmpeg is available
     */
    public JsonObject checkFFmpeg() throws IOException, TimeoutException {
        JsonObject command = new JsonObject();
        command.addProperty("action", "check_ffmpeg");
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
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Python bridge shutdown complete");
    }
    
    /**
     * Get path to Python executable (bundled or system)
     */
    private Path getPythonExecutable() throws IOException {
        // First, check if we have a bundled Python executable
        Path bundledPython = Paths.get(System.getProperty("user.dir"), "python", "ffmpeg_backend.exe");
        
        if (Files.exists(bundledPython)) {
            logger.info("Using bundled Python: {}", bundledPython);
            return bundledPython;
        }
        
        // On Windows, look for .exe
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            bundledPython = Paths.get(System.getProperty("user.dir"), "python", "ffmpeg_backend.exe");
            if (Files.exists(bundledPython)) {
                return bundledPython;
            }
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
            Path scriptPath = Paths.get(runtimeDir, "ffmpeg_api.py");
            if (Files.exists(scriptPath)) {
                return scriptPath;
            }
        }
        
        // Fallback to project directory (for development)
        Path devScriptPath = Paths.get(System.getProperty("user.dir"), "..", "ffmpeg_api.py");
        if (Files.exists(devScriptPath)) {
            return devScriptPath;
        }
        
        // Try parent directory
        Path parentScriptPath = Paths.get("ffmpeg_api.py");
        if (Files.exists(parentScriptPath)) {
            return parentScriptPath;
        }
        
        // Fallback to resources directory
        Path resourceScriptPath = Paths.get("src", "main", "resources", "python", "ffmpeg_api.py");
        if (Files.exists(resourceScriptPath)) {
            return resourceScriptPath;
        }
        
        throw new IOException("Python API script not found. Checked:\n" +
            "  - " + (runtimeDir != null ? Paths.get(runtimeDir, "ffmpeg_api.py") : "(runtime dir not set)") + "\n" +
            "  - " + devScriptPath + "\n" +
            "  - " + parentScriptPath + "\n" +
            "  - " + resourceScriptPath);
    }
    
    public boolean isRunning() {
        return isRunning && pythonProcess != null && pythonProcess.isAlive();
    }
}

