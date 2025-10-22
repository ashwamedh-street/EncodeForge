package com.encodeforge.service;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Routes tasks to appropriate workers based on task type, priority, and worker availability
 * Implements intelligent task distribution with worker specialization
 */
public class TaskRouter {
    private static final Logger logger = LoggerFactory.getLogger(TaskRouter.class);
    
    private final List<PythonWorker> allWorkers;
    private final Map<WorkerRole, List<PythonWorker>> workersByRole;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Map<String, TaskMetrics> taskMetrics = new ConcurrentHashMap<>();
    
    /**
     * Worker roles for specialization
     */
    public enum WorkerRole {
        QUICK_OPS,      // Fast status checks, file info
        HEAVY_FFMPEG,   // FFmpeg operations, encoder detection
        WHISPER,        // Whisper operations (downloads, transcription)
        CONVERSION,     // Video conversion operations
        GENERAL         // General purpose worker
    }
    
    /**
     * Task priority levels
     */
    public enum TaskPriority {
        IMMEDIATE,      // Status checks, heartbeats
        HIGH,           // User-initiated actions
        NORMAL,         // Background processing
        LOW             // Cleanup, maintenance
    }
    
    /**
     * Task type classification
     */
    public enum TaskType {
        // Quick operations
        STATUS_CHECK("check_ffmpeg", "check_whisper", "check_opensubtitles", "check_tmdb", "check_tvdb", "get_all_status"),
        FILE_INFO("get_file_info", "get_media_info", "scan_directory"),
        HEARTBEAT("heartbeat"),
        
        // Heavy FFmpeg operations
        FFMPEG_OPS("get_available_encoders", "get_capabilities"),
        
        // Whisper operations
        WHISPER_OPS("download_whisper_model", "install_whisper", "generate_subtitles"),
        
        // Conversion operations
        CONVERSION("convert_files", "convert_file"),
        
        // Subtitle operations (can use general workers)
        SUBTITLE_OPS("search_subtitles", "download_subtitle", "download_subtitles", "apply_subtitles", "advanced_search_subtitles", "batch_search_subtitles"),
        
        // General operations
        GENERAL("preview_rename", "rename_files", "update_settings", "get_settings", "list_profiles", "load_profile", "save_profile", "delete_profile");
        
        private final Set<String> actions;
        
        TaskType(String... actions) {
            this.actions = new HashSet<>(Arrays.asList(actions));
        }
        
        public boolean matches(String action) {
            return actions.contains(action);
        }
        
        public static TaskType fromAction(String action) {
            for (TaskType type : values()) {
                if (type.matches(action)) {
                    return type;
                }
            }
            return GENERAL; // Default to general
        }
    }
    
    /**
     * Metrics for task routing decisions
     */
    private static class TaskMetrics {
        long totalExecutions = 0;
        long totalDuration = 0;
        
        double getAverageDuration() {
            return totalExecutions > 0 ? (double) totalDuration / totalExecutions : 0;
        }
    }
    
    public TaskRouter(List<PythonWorker> workers) {
        this.allWorkers = new ArrayList<>(workers);
        this.workersByRole = new EnumMap<>(WorkerRole.class);
        
        // Initialize role mappings (will be configured properly during pool setup)
        for (WorkerRole role : WorkerRole.values()) {
            workersByRole.put(role, new ArrayList<>());
        }
        
        logger.info("TaskRouter initialized with {} workers", workers.size());
    }
    
    /**
     * Assign a worker to a specific role
     */
    public void assignWorkerRole(PythonWorker worker, WorkerRole role) {
        if (!workersByRole.get(role).contains(worker)) {
            workersByRole.get(role).add(worker);
            logger.info("Worker {} assigned to role: {}", worker.getWorkerId(), role);
        }
    }
    
    /**
     * Get the best available worker for a task
     */
    public PythonWorker selectWorker(JsonObject command, TaskPriority priority) {
        String action = command.has("action") ? command.get("action").getAsString() : "unknown";
        TaskType taskType = TaskType.fromAction(action);
        
        logger.debug("Selecting worker for task: {} (type: {}, priority: {})", action, taskType, priority);
        
        // Try to get specialized worker first
        PythonWorker worker = selectSpecializedWorker(taskType);
        
        // Fallback to general worker if specialized not available
        if (worker == null) {
            worker = selectGeneralWorker();
        }
        
        if (worker != null) {
            logger.debug("Selected worker {} for task {}", worker.getWorkerId(), action);
            recordTaskStart(action);
        } else {
            logger.warn("No available worker for task: {}", action);
        }
        
        return worker;
    }
    
    /**
     * Select a specialized worker based on task type
     */
    private PythonWorker selectSpecializedWorker(TaskType taskType) {
        WorkerRole preferredRole = getPreferredRole(taskType);
        
        // Try preferred role first
        List<PythonWorker> roleWorkers = workersByRole.get(preferredRole);
        for (PythonWorker worker : roleWorkers) {
            if (worker.isAvailable()) {
                return worker;
            }
        }
        
        // If preferred role workers are busy but task is not critical,
        // check if any quick-ops workers are available for non-blocking tasks
        if (taskType == TaskType.STATUS_CHECK || taskType == TaskType.FILE_INFO || taskType == TaskType.HEARTBEAT) {
            roleWorkers = workersByRole.get(WorkerRole.QUICK_OPS);
            for (PythonWorker worker : roleWorkers) {
                if (worker.isAvailable()) {
                    return worker;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Select a general-purpose worker using round-robin
     */
    private PythonWorker selectGeneralWorker() {
        // Get all general workers
        List<PythonWorker> generalWorkers = workersByRole.get(WorkerRole.GENERAL);
        if (generalWorkers.isEmpty()) {
            // Fallback to all workers
            generalWorkers = allWorkers;
        }
        
        // Try round-robin through available workers
        int attempts = 0;
        int maxAttempts = generalWorkers.size();
        
        while (attempts < maxAttempts) {
            int index = roundRobinIndex.getAndIncrement() % generalWorkers.size();
            PythonWorker worker = generalWorkers.get(index);
            
            if (worker.isAvailable()) {
                return worker;
            }
            attempts++;
        }
        
        // No available workers
        return null;
    }
    
    /**
     * Get list of all available workers
     */
    public List<PythonWorker> getAvailableWorkers() {
        return allWorkers.stream()
            .filter(PythonWorker::isAvailable)
            .collect(Collectors.toList());
    }
    
    /**
     * Get list of workers by role
     */
    public List<PythonWorker> getWorkersByRole(WorkerRole role) {
        return new ArrayList<>(workersByRole.get(role));
    }
    
    /**
     * Get preferred worker role for a task type
     */
    private WorkerRole getPreferredRole(TaskType taskType) {
        switch (taskType) {
            case STATUS_CHECK:
            case FILE_INFO:
            case HEARTBEAT:
                return WorkerRole.QUICK_OPS;
                
            case FFMPEG_OPS:
                return WorkerRole.HEAVY_FFMPEG;
                
            case WHISPER_OPS:
                return WorkerRole.WHISPER;
                
            case CONVERSION:
                return WorkerRole.CONVERSION;
                
            case SUBTITLE_OPS:
            case GENERAL:
            default:
                return WorkerRole.GENERAL;
        }
    }
    
    /**
     * Record task start for metrics
     */
    private void recordTaskStart(String action) {
        taskMetrics.computeIfAbsent(action, k -> new TaskMetrics());
    }
    
    /**
     * Record task completion for metrics
     */
    public void recordTaskCompletion(String action, long durationMs) {
        TaskMetrics metrics = taskMetrics.get(action);
        if (metrics != null) {
            metrics.totalExecutions++;
            metrics.totalDuration += durationMs;
            logger.debug("Task {} completed in {}ms (avg: {}ms over {} executions)",
                action, durationMs, (long)metrics.getAverageDuration(), metrics.totalExecutions);
        }
    }
    
    /**
     * Get task statistics
     */
    public Map<String, String> getTaskStatistics() {
        Map<String, String> stats = new HashMap<>();
        
        for (Map.Entry<String, TaskMetrics> entry : taskMetrics.entrySet()) {
            TaskMetrics metrics = entry.getValue();
            stats.put(entry.getKey(), String.format(
                "executions: %d, avg duration: %.2fms",
                metrics.totalExecutions,
                metrics.getAverageDuration()
            ));
        }
        
        return stats;
    }
    
    /**
     * Get worker utilization statistics
     */
    public Map<String, Object> getWorkerUtilization() {
        Map<String, Object> utilization = new HashMap<>();
        
        int totalWorkers = allWorkers.size();
        int availableWorkers = (int) allWorkers.stream().filter(PythonWorker::isAvailable).count();
        int busyWorkers = (int) allWorkers.stream().filter(PythonWorker::isBusy).count();
        int unhealthyWorkers = (int) allWorkers.stream().filter(w -> !w.isHealthy()).count();
        
        utilization.put("total", totalWorkers);
        utilization.put("available", availableWorkers);
        utilization.put("busy", busyWorkers);
        utilization.put("unhealthy", unhealthyWorkers);
        utilization.put("utilization_percent", totalWorkers > 0 ? (busyWorkers * 100.0 / totalWorkers) : 0);
        
        return utilization;
    }
    
    /**
     * Check if any workers are available
     */
    public boolean hasAvailableWorkers() {
        return allWorkers.stream().anyMatch(PythonWorker::isAvailable);
    }
    
    /**
     * Get count of available workers by role
     */
    public int getAvailableWorkerCount(WorkerRole role) {
        return (int) workersByRole.get(role).stream()
            .filter(PythonWorker::isAvailable)
            .count();
    }
}
