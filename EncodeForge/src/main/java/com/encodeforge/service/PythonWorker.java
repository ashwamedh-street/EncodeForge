package com.encodeforge.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Individual Python worker process for handling tasks
 * Each worker maintains its own Python process and stdin/stdout communication channel
 */
public class PythonWorker {
    private static final Logger logger = LoggerFactory.getLogger(PythonWorker.class);
    private static final Gson gson = new Gson();
    private static final long TIMEOUT_SECONDS = 300; // 5 minutes for long operations
    
    private final String workerId;
    private final DependencyManager dependencyManager;
    private final ExecutorService ioExecutor;
    
    private Process pythonProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean isBusy = new AtomicBoolean(false);
    private AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    
    private final Object ioLock = new Object();
    
    /**
     * Create a new Python worker
     * @param workerId Unique identifier for this worker (e.g., "worker-0")
     * @param dependencyManager Dependency manager for Python paths
     */
    public PythonWorker(String workerId, DependencyManager dependencyManager) {
        this.workerId = workerId;
        this.dependencyManager = dependencyManager;
        
        // Dedicated I/O executor for this worker
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PythonWorker-" + workerId + "-IO");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("PythonWorker {} created", workerId);
    }
    
    /**
     * Start the Python worker process
     */
    public void start() throws IOException {
        if (isRunning.get()) {
            logger.warn("Worker {} already running", workerId);
            return;
        }
        
        Path pythonExecutable = getPythonExecutable();
        Path scriptPath = getPythonScript();
        
        logger.info("Starting Python worker {}", workerId);
        logger.info("  Python: {}", pythonExecutable);
        logger.info("  Script: {}", scriptPath.toAbsolutePath());
        
        ProcessBuilder pb = new ProcessBuilder(
            pythonExecutable.toString(),
            "-u",  // Unbuffered mode for real-time progress updates
            scriptPath.toString()
        );
        
        // Set working directory
        pb.directory(scriptPath.getParent().toFile());
        
        // Set environment variables
        pb.environment().put("PYTHONUNBUFFERED", "1");
        pb.environment().put("WORKER_ID", workerId); // Worker identification
        
        // Set PYTHONPATH to include our custom libraries directory
        String pythonPath = dependencyManager.getPythonPath();
        pb.environment().put("PYTHONPATH", pythonPath);
        logger.debug("Worker {} PYTHONPATH: {}", workerId, pythonPath);
        
        // Pass FFmpeg directory to Python if available
        Path ffmpegPath = dependencyManager.getInstalledFFmpegPath();
        if (ffmpegPath != null) {
            pb.environment().put("FFMPEG_PATH", ffmpegPath.toString());
        }
        
        // Merge error stream for easier handling
        pb.redirectErrorStream(true);
        
        pythonProcess = pb.start();
        
        reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        
        isRunning.set(true);
        updateActivity();
        
        // Wait for the "ready" message from Python
        try {
            Future<String> readyFuture = ioExecutor.submit(() -> reader.readLine());
            String readyLine = readyFuture.get(10, TimeUnit.SECONDS);
            logger.debug("Worker {} received startup message: {}", workerId, readyLine);
            
            JsonObject readyMessage = gson.fromJson(readyLine, JsonObject.class);
            if (readyMessage.has("status") && "ready".equals(readyMessage.get("status").getAsString())) {
                logger.info("Worker {} started successfully and ready", workerId);
            } else {
                logger.warn("Worker {} unexpected startup message: {}", workerId, readyLine);
            }
        } catch (Exception e) {
            logger.error("Worker {} failed to receive ready message", workerId, e);
            throw new IOException("Worker " + workerId + " initialization failed", e);
        }
    }
    
    /**
     * Send a command and get response (blocking)
     */
    public JsonObject sendCommand(JsonObject command) throws IOException, TimeoutException {
        if (!isRunning.get()) {
            throw new IllegalStateException("Worker " + workerId + " not running");
        }
        
        isBusy.set(true);
        try {
            synchronized (ioLock) {
                // Send command
                String commandJson = gson.toJson(command);
                logger.debug("Worker {} sending command: {}", workerId, command.get("action"));
                writer.write(commandJson);
                writer.newLine();
                writer.flush();
                updateActivity();

                // Read response with timeout
                Future<String> future = ioExecutor.submit(() -> reader.readLine());

                try {
                    String responseLine = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (responseLine == null) {
                        throw new IOException("Worker " + workerId + " closed connection");
                    }
                    updateActivity();
                    logger.debug("Worker {} received response: {} chars", workerId, responseLine.length());
                    
                    // Debug: Log actual response content if it seems problematic
                    if (responseLine.length() < 500) {
                        logger.debug("Worker {} response content: {}", workerId, responseLine);
                    }
                    
                    return gson.fromJson(responseLine, JsonObject.class);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Worker {} error reading response", workerId, e);
                    throw new IOException("Failed to read response from worker " + workerId, e);
                } catch (TimeoutException e) {
                    logger.error("Worker {} command timed out after {} seconds", workerId, TIMEOUT_SECONDS);
                    future.cancel(true);
                    throw e;
                }
            }
        } finally {
            isBusy.set(false);
        }
    }
    
    /**
     * Send a command with streaming response (for progress updates)
     */
    public void sendStreamingCommand(JsonObject command, Consumer<JsonObject> progressCallback) throws IOException {
        if (!isRunning.get()) {
            throw new IllegalStateException("Worker " + workerId + " not running");
        }
        
        isBusy.set(true);
        ioExecutor.submit(() -> {
            try {
                // Write command
                synchronized (ioLock) {
                    String commandJson = gson.toJson(command);
                    logger.debug("Worker {} sending streaming command: {}", workerId, command.get("action"));
                    writer.write(commandJson);
                    writer.newLine();
                    writer.flush();
                    updateActivity();
                }

                // Read streaming responses
                String line;
                boolean receivedFinalResponse = false;
                while ((line = reader.readLine()) != null && !receivedFinalResponse) {
                    logger.trace("Worker {} streaming line: {}", workerId, line);
                    
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    JsonObject response = null;
                    try {
                        response = gson.fromJson(line, JsonObject.class);
                    } catch (Exception e) {
                        logger.warn("Worker {} failed to parse streaming line: {}", workerId, line, e);
                        continue;
                    }
                    
                    if (response == null) {
                        logger.warn("Worker {} received null response from line: {}", workerId, line);
                        continue;
                    }
                    
                    updateActivity();
                    
                    // Pass every response to callback
                    progressCallback.accept(response);
                    
                    // DEBUG: Log what we received
                    logger.debug("Worker {} received response with keys: {}", workerId, response.keySet());
                    if (response.has("status")) {
                        logger.debug("Worker {} response status: {}", workerId, response.get("status").getAsString());
                    }
                    if (response.has("file_complete")) {
                        logger.debug("Worker {} response has file_complete: {}", workerId, response.get("file_complete").getAsBoolean());
                    }
                    
                    // Check if this is the final response
                    if (response.has("complete") && response.get("complete").getAsBoolean()) {
                        logger.debug("Worker {} received final response (complete: true)", workerId);
                        receivedFinalResponse = true;
                    } else if (response.has("status")) {
                        String status = response.get("status").getAsString();
                        // Note: "file_complete" is NOT a final response - it indicates one file in a batch is done
                        // Only "complete", "error", and "success" (for non-batch operations) close the stream
                        if ("complete".equals(status) || "error".equals(status) || 
                            ("success".equals(status) && !response.has("file_complete"))) {
                            logger.debug("Worker {} received final response (status: {})", workerId, status);
                            receivedFinalResponse = true;
                        } else {
                            logger.debug("Worker {} status '{}' is NOT final (continuing stream)", workerId, status);
                        }
                    } else if (response.has("results") && !response.has("progress")) {
                        logger.debug("Worker {} received final response (has results)", workerId);
                        receivedFinalResponse = true;
                    }
                    
                    if (receivedFinalResponse) {
                        logger.info("Worker {} STOPPING stream read loop - receivedFinalResponse=true", workerId);
                    }
                }
                
                if (!receivedFinalResponse) {
                    logger.warn("Worker {} stream ended without receiving final response", workerId);
                }
            } catch (IOException e) {
                logger.error("Worker {} error in streaming command", workerId, e);
                // Send error to callback
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "error");
                errorResponse.addProperty("message", "Streaming error: " + e.getMessage());
                errorResponse.addProperty("complete", true);
                progressCallback.accept(errorResponse);
            } finally {
                isBusy.set(false);
            }
        });
    }
    
    /**
     * Send a heartbeat to check if worker is responsive
     */
    public CompletableFuture<Boolean> sendHeartbeat() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject command = new JsonObject();
                command.addProperty("action", "heartbeat");
                
                JsonObject response = sendCommand(command);
                updateActivity();
                return response.has("status") && "success".equals(response.get("status").getAsString());
            } catch (Exception e) {
                logger.warn("Worker {} heartbeat failed", workerId, e);
                return false;
            }
        }, ioExecutor);
    }
    
    /**
     * Check if worker is available for new tasks
     */
    public boolean isAvailable() {
        return isRunning.get() && !isBusy.get() && isHealthy();
    }
    
    /**
     * Check if worker is busy with a task
     */
    public boolean isBusy() {
        return isBusy.get();
    }
    
    /**
     * Check if worker process is running
     */
    public boolean isRunning() {
        return isRunning.get() && pythonProcess != null && pythonProcess.isAlive();
    }
    
    /**
     * Check if worker is healthy (responsive and not hung)
     */
    public boolean isHealthy() {
        if (!isRunning()) {
            return false;
        }
        
        // Check if worker has been inactive for too long (10 minutes)
        long timeSinceActivity = System.currentTimeMillis() - lastActivityTime.get();
        if (timeSinceActivity > 600000) { // 10 minutes
            logger.warn("Worker {} appears inactive ({}ms since last activity)", workerId, timeSinceActivity);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get worker ID
     */
    public String getWorkerId() {
        return workerId;
    }
    
    /**
     * Get last activity timestamp
     */
    public long getLastActivityTime() {
        return lastActivityTime.get();
    }
    
    /**
     * Update last activity time
     */
    private void updateActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }
    
    /**
     * Shutdown the worker process
     */
    public void shutdown() {
        logger.info("Shutting down worker {}", workerId);
        isRunning.set(false);
        
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
            logger.warn("Worker {} error sending shutdown command", workerId, e);
        }
        
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            try {
                if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("Worker {} process did not terminate, forcing...", workerId);
                    pythonProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.error("Worker {} interrupted while waiting for process", workerId, e);
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown executor
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Worker {} shutdown complete", workerId);
    }
    
    /**
     * Get path to Python executable (delegates to DependencyManager)
     */
    private Path getPythonExecutable() throws IOException {
        return dependencyManager.getPythonExecutable();
    }
    
    /**
     * Get path to Python API script
     */
    private Path getPythonScript() throws IOException {
        // First try extracted scripts directory
        String scriptsDir = System.getProperty("python.scripts.dir");
        if (scriptsDir != null) {
            Path scriptPath = Paths.get(scriptsDir, "encodeforge_api.py");
            if (Files.exists(scriptPath)) {
                logger.debug("Worker {} using extracted script: {}", workerId, scriptPath);
                return scriptPath;
            }
        }
        
        // Fallback to development directory (running from IDE)
        Path devScriptPath = Paths.get("src", "main", "resources", "python", "encodeforge_api.py");
        if (Files.exists(devScriptPath)) {
            logger.debug("Worker {} using development script: {}", workerId, devScriptPath);
            return devScriptPath;
        }
        
        // Try EncodeForge subdirectory (Maven project structure)
        devScriptPath = Paths.get("EncodeForge", "src", "main", "resources", "python", "encodeforge_api.py");
        if (Files.exists(devScriptPath)) {
            logger.debug("Worker {} using development script (EncodeForge/): {}", workerId, devScriptPath);
            return devScriptPath;
        }
        
        throw new IOException("Python API script (encodeforge_api.py) not found. Checked:\n" +
            "  - " + (scriptsDir != null ? Paths.get(scriptsDir, "encodeforge_api.py") : "(scripts dir not set)") + "\n" +
            "  - " + Paths.get("src", "main", "resources", "python", "encodeforge_api.py") + "\n" +
            "  - " + Paths.get("EncodeForge", "src", "main", "resources", "python", "encodeforge_api.py"));
    }
}
