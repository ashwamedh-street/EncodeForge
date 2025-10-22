package com.encodeforge.service;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Pool manager for multiple Python worker processes
 * Enables concurrent task execution with intelligent routing and health monitoring
 */
public class PythonProcessPool {
    private static final Logger logger = LoggerFactory.getLogger(PythonProcessPool.class);
    
    private final int poolSize;
    private final DependencyManager dependencyManager;
    private final List<PythonWorker> workers;
    private final TaskRouter taskRouter;
    private final ScheduledExecutorService healthMonitor;
    private final ExecutorService taskExecutor;
    
    private volatile boolean isRunning = false;
    private volatile boolean isStartupComplete = false;
    
    /**
     * Create a new Python process pool
     * @param dependencyManager Dependency manager for Python paths
     * @param poolSize Number of worker processes (recommended: 4-5)
     */
    public PythonProcessPool(DependencyManager dependencyManager, int poolSize) {
        this.dependencyManager = dependencyManager;
        this.poolSize = poolSize;
        this.workers = new ArrayList<>(poolSize);
        this.taskRouter = new TaskRouter(workers);
        
        // Health monitoring executor
        this.healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PythonProcessPool-HealthMonitor");
            t.setDaemon(true);
            return t;
        });
        
        // Task execution executor
        this.taskExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "PythonProcessPool-TaskExecutor");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("PythonProcessPool created with {} workers", poolSize);
    }
    
    /**
     * Start all workers synchronously (blocks until all ready)
     */
    public void start() throws IOException {
        if (isRunning) {
            logger.warn("Process pool already running");
            return;
        }
        
        logger.info("Starting Python process pool with {} workers", poolSize);
        long startTime = System.currentTimeMillis();
        
        // Create and start all workers
        List<CompletableFuture<Void>> startupFutures = new ArrayList<>();
        
        for (int i = 0; i < poolSize; i++) {
            final int workerIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    PythonWorker worker = new PythonWorker("worker-" + workerIndex, dependencyManager);
                    worker.start();
                    synchronized (workers) {
                        workers.add(worker);
                    }
                    logger.info("Worker {} started successfully", workerIndex);
                } catch (Exception e) {
                    logger.error("Failed to start worker {}", workerIndex, e);
                    throw new CompletionException(e);
                }
            }, taskExecutor);
            startupFutures.add(future);
        }
        
        // Wait for all workers to start
        try {
            CompletableFuture.allOf(startupFutures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to start all workers", e);
            shutdown();
            throw new IOException("Worker startup failed", e);
        }
        
        // Configure worker specialization
        configureWorkerRoles();
        
        // Start health monitoring
        startHealthMonitoring();
        
        isRunning = true;
        isStartupComplete = true;
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Python process pool started successfully in {}ms with {} workers", duration, workers.size());
    }
    
    /**
     * Start workers asynchronously (returns immediately, workers start in background)
     */
    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                start();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, taskExecutor);
    }
    
    /**
     * Configure worker role specialization
     */
    private void configureWorkerRoles() {
        if (workers.size() < 4) {
            // Not enough workers for specialization, all are general purpose
            for (PythonWorker worker : workers) {
                taskRouter.assignWorkerRole(worker, TaskRouter.WorkerRole.GENERAL);
            }
            logger.info("Worker pool too small for specialization, all workers set to GENERAL");
            return;
        }
        
        // Assign specialized roles based on pool size
        // Workers 0-1: Quick operations
        for (int i = 0; i < Math.min(2, workers.size()); i++) {
            taskRouter.assignWorkerRole(workers.get(i), TaskRouter.WorkerRole.QUICK_OPS);
            taskRouter.assignWorkerRole(workers.get(i), TaskRouter.WorkerRole.GENERAL); // Can also do general
        }
        
        // Worker 2: Heavy FFmpeg operations (if enough workers)
        if (workers.size() > 2) {
            taskRouter.assignWorkerRole(workers.get(2), TaskRouter.WorkerRole.HEAVY_FFMPEG);
            taskRouter.assignWorkerRole(workers.get(2), TaskRouter.WorkerRole.GENERAL);
        }
        
        // Worker 3: Whisper operations (if enough workers)
        if (workers.size() > 3) {
            taskRouter.assignWorkerRole(workers.get(3), TaskRouter.WorkerRole.WHISPER);
            taskRouter.assignWorkerRole(workers.get(3), TaskRouter.WorkerRole.GENERAL);
        }
        
        // Worker 4+: Conversion operations (if enough workers)
        if (workers.size() > 4) {
            taskRouter.assignWorkerRole(workers.get(4), TaskRouter.WorkerRole.CONVERSION);
            taskRouter.assignWorkerRole(workers.get(4), TaskRouter.WorkerRole.GENERAL);
        }
        
        // Any additional workers are general purpose
        for (int i = 5; i < workers.size(); i++) {
            taskRouter.assignWorkerRole(workers.get(i), TaskRouter.WorkerRole.GENERAL);
        }
        
        logger.info("Worker role specialization configured for {} workers", workers.size());
    }
    
    /**
     * Start health monitoring for all workers
     */
    private void startHealthMonitoring() {
        healthMonitor.scheduleAtFixedRate(() -> {
            try {
                checkWorkerHealth();
            } catch (Exception e) {
                logger.error("Error in health monitoring", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
        
        logger.info("Health monitoring started");
    }
    
    /**
     * Check health of all workers and restart unhealthy ones
     */
    private void checkWorkerHealth() {
        for (int i = 0; i < workers.size(); i++) {
            PythonWorker worker = workers.get(i);
            
            if (!worker.isHealthy() || !worker.isRunning()) {
                logger.warn("Worker {} is unhealthy, attempting restart", worker.getWorkerId());
                restartWorker(i);
            }
        }
    }
    
    /**
     * Restart a specific worker
     */
    private void restartWorker(int workerIndex) {
        PythonWorker oldWorker = workers.get(workerIndex);
        String workerId = oldWorker.getWorkerId();
        
        try {
            // Shutdown old worker
            oldWorker.shutdown();
            
            // Create and start new worker
            PythonWorker newWorker = new PythonWorker(workerId, dependencyManager);
            newWorker.start();
            
            // Replace in list
            synchronized (workers) {
                workers.set(workerIndex, newWorker);
            }
            
            // Reconfigure roles (worker roles are based on index)
            configureWorkerRoles();
            
            logger.info("Worker {} restarted successfully", workerId);
        } catch (Exception e) {
            logger.error("Failed to restart worker {}", workerId, e);
        }
    }
    
    /**
     * Submit a command and wait for response (blocking)
     */
    public JsonObject sendCommand(JsonObject command) throws IOException, TimeoutException {
        return sendCommand(command, TaskRouter.TaskPriority.NORMAL);
    }
    
    /**
     * Submit a command with priority and wait for response (blocking)
     */
    public JsonObject sendCommand(JsonObject command, TaskRouter.TaskPriority priority) throws IOException, TimeoutException {
        if (!isRunning) {
            throw new IllegalStateException("Process pool not running");
        }
        
        long startTime = System.currentTimeMillis();
        String action = command.has("action") ? command.get("action").getAsString() : "unknown";
        
        // Get appropriate worker
        PythonWorker worker = taskRouter.selectWorker(command, priority);
        
        if (worker == null) {
            // No workers available - wait and retry
            logger.warn("No workers available for task {}, waiting...", action);
            try {
                Thread.sleep(100);
                worker = taskRouter.selectWorker(command, priority);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for worker");
            }
            
            if (worker == null) {
                throw new IOException("No available workers for task: " + action);
            }
        }
        
        try {
            JsonObject response = worker.sendCommand(command);
            long duration = System.currentTimeMillis() - startTime;
            taskRouter.recordTaskCompletion(action, duration);
            return response;
        } catch (Exception e) {
            logger.error("Error executing command {} on worker {}", action, worker.getWorkerId(), e);
            throw e;
        }
    }
    
    /**
     * Submit a command with streaming progress (non-blocking)
     */
    public void sendStreamingCommand(JsonObject command, Consumer<JsonObject> progressCallback) throws IOException {
        sendStreamingCommand(command, progressCallback, TaskRouter.TaskPriority.NORMAL);
    }
    
    /**
     * Submit a command with streaming progress and priority (non-blocking)
     */
    public void sendStreamingCommand(JsonObject command, Consumer<JsonObject> progressCallback, TaskRouter.TaskPriority priority) throws IOException {
        if (!isRunning) {
            throw new IllegalStateException("Process pool not running");
        }
        
        String action = command.has("action") ? command.get("action").getAsString() : "unknown";
        
        // Get appropriate worker
        PythonWorker worker = taskRouter.selectWorker(command, priority);
        
        if (worker == null) {
            throw new IOException("No available workers for streaming task: " + action);
        }
        
        logger.debug("Submitting streaming task {} to worker {}", action, worker.getWorkerId());
        worker.sendStreamingCommand(command, progressCallback);
    }
    
    /**
     * Submit a quick task (guaranteed to use quick-ops worker)
     */
    public CompletableFuture<JsonObject> submitQuickTask(String action) {
        return submitQuickTask(action, new JsonObject());
    }
    
    /**
     * Submit a quick task with custom parameters
     */
    public CompletableFuture<JsonObject> submitQuickTask(String action, JsonObject params) {
        JsonObject command = new JsonObject();
        command.addProperty("action", action);
        
        // Merge params
        for (String key : params.keySet()) {
            command.add(key, params.get(key));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendCommand(command, TaskRouter.TaskPriority.IMMEDIATE);
            } catch (Exception e) {
                logger.error("Error executing quick task {}", action, e);
                throw new CompletionException(e);
            }
        }, taskExecutor);
    }
    
    /**
     * Check if pool is running
     */
    public boolean isRunning() {
        return isRunning && workers.stream().anyMatch(PythonWorker::isRunning);
    }
    
    /**
     * Check if startup is complete
     */
    public boolean isStartupComplete() {
        return isStartupComplete;
    }
    
    /**
     * Get number of available workers
     */
    public int getAvailableWorkerCount() {
        return (int) workers.stream().filter(PythonWorker::isAvailable).count();
    }
    
    /**
     * Get pool statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pool_size", poolSize);
        stats.put("running_workers", workers.stream().filter(PythonWorker::isRunning).count());
        stats.put("worker_utilization", taskRouter.getWorkerUtilization());
        stats.put("task_statistics", taskRouter.getTaskStatistics());
        return stats;
    }
    
    /**
     * Shutdown all workers
     */
    public void shutdown() {
        logger.info("Shutting down Python process pool");
        isRunning = false;
        
        // Stop health monitoring
        healthMonitor.shutdown();
        
        // Shutdown all workers in parallel
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        for (PythonWorker worker : workers) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(worker::shutdown, taskExecutor);
            shutdownFutures.add(future);
        }
        
        // Wait for all workers to shutdown
        try {
            CompletableFuture.allOf(shutdownFutures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Not all workers shut down cleanly", e);
        }
        
        // Shutdown executors
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            if (!healthMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Python process pool shutdown complete");
    }
    
    /**
     * Get the task router (for advanced usage)
     */
    public TaskRouter getTaskRouter() {
        return taskRouter;
    }
}
