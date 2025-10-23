package com.encodeforge.util;

import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * SystemResourceManager - Detects system resources and provides optimal worker counts
 * Replaces Python's psutil dependency with native Java system monitoring
 */
public class SystemResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(SystemResourceManager.class);
    
    private final int logicalCpuCount;
    private final int physicalCpuCount;
    private final OperatingSystemMXBean osBean;
    
    private static SystemResourceManager instance;
    
    private SystemResourceManager() {
        this.logicalCpuCount = Runtime.getRuntime().availableProcessors();
        
        // Get physical core count (estimate: usually half of logical on hyperthreaded systems)
        // This is a rough estimate since Java doesn't have a direct API for physical cores
        this.physicalCpuCount = estimatePhysicalCores();
        
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        
        logger.info("=== System Resources (Java) ===");
        logger.info("CPU Cores (Logical): {}", logicalCpuCount);
        logger.info("CPU Cores (Physical): {} (estimated)", physicalCpuCount);
        logger.info("Total RAM: {:.2f} GB", getTotalRamGB());
        logger.info("Available RAM: {:.2f} GB", getAvailableRamGB());
    }
    
    public static synchronized SystemResourceManager getInstance() {
        if (instance == null) {
            instance = new SystemResourceManager();
        }
        return instance;
    }
    
    /**
     * Estimate physical CPU core count
     * On hyperthreaded systems, physical cores are typically half of logical cores
     */
    private int estimatePhysicalCores() {
        // For simple estimation: physical = logical / 2 if > 2 cores
        // This works well for most modern systems with hyperthreading
        // Most modern CPUs have 2 threads per physical core
        if (logicalCpuCount > 2) {
            return Math.max(1, logicalCpuCount / 2);
        }
        return logicalCpuCount;
    }
    
    /**
     * Get logical CPU core count (includes hyperthreading)
     */
    public int getLogicalCpuCount() {
        return logicalCpuCount;
    }
    
    /**
     * Get estimated physical CPU core count
     */
    public int getPhysicalCpuCount() {
        return physicalCpuCount;
    }
    
    /**
     * Get total system RAM in GB
     */
    public double getTotalRamGB() {
        long totalMemoryBytes = osBean.getTotalMemorySize();
        return totalMemoryBytes / (1024.0 * 1024.0 * 1024.0);
    }
    
    /**
     * Get available system RAM in GB
     */
    public double getAvailableRamGB() {
        long freeMemoryBytes = osBean.getFreeMemorySize();
        return freeMemoryBytes / (1024.0 * 1024.0 * 1024.0);
    }
    
    /**
     * Get optimal worker count for a specific task type
     * 
     * @param taskType Type of task: "whisper", "encoding", "subtitle_search", "download", "metadata"
     * @return Optimal number of workers
     */
    public int getOptimalWorkerCount(String taskType) {
        switch (taskType.toLowerCase()) {
            case "whisper":
                return calculateWhisperWorkers();
            
            case "encoding":
                // Video encoding: CPU-intensive, use physical cores
                // Leave 1 core free for system
                return Math.max(1, physicalCpuCount - 1);
            
            case "subtitle_search":
                // Network I/O bound, can parallelize heavily
                return Math.min(8, logicalCpuCount * 2);
            
            case "download":
                // Network I/O bound, moderate parallelization
                return Math.min(6, logicalCpuCount);
            
            case "metadata":
                // Light CPU, moderate I/O
                return Math.min(4, logicalCpuCount);
            
            default:
                // Default: use half of logical cores
                return Math.max(2, logicalCpuCount / 2);
        }
    }
    
    /**
     * Calculate optimal Whisper workers based on available RAM
     * Whisper memory requirements:
     * - Tiny: ~1GB, Base: ~2GB, Small: ~5GB, Medium: ~10GB, Large: ~15GB
     */
    private int calculateWhisperWorkers() {
        double availableRam = getAvailableRamGB();
        
        // Conservative estimate: 5GB per instance (Small model is most common)
        double gbPerInstance = 5.0;
        
        // Reserve 4GB for system and other processes
        double availableForWhisper = Math.max(0, availableRam - 4);
        
        // Calculate how many instances we can run
        int maxInstances = (int) (availableForWhisper / gbPerInstance);
        
        // Limit to physical cores (Whisper is CPU-intensive too)
        maxInstances = Math.min(maxInstances, physicalCpuCount);
        
        // At least 1, at most 4 (diminishing returns beyond 4 parallel transcriptions)
        int optimal = Math.max(1, Math.min(4, maxInstances));
        
        logger.debug("Whisper workers: {} (based on {:.2f}GB available RAM, {}GB per instance)",
                optimal, availableRam, gbPerInstance);
        
        return optimal;
    }
    
    /**
     * Get system information as a map (for passing to Python)
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("cpu_count_logical", logicalCpuCount);
        info.put("cpu_count_physical", physicalCpuCount);
        info.put("total_ram_gb", Math.round(getTotalRamGB() * 100.0) / 100.0);
        info.put("available_ram_gb", Math.round(getAvailableRamGB() * 100.0) / 100.0);
        info.put("os_name", System.getProperty("os.name"));
        info.put("os_version", System.getProperty("os.version"));
        info.put("java_version", System.getProperty("java.version"));
        return info;
    }
    
    /**
     * Check if system can run multiple parallel Whisper instances
     * 
     * @param numInstances Number of instances to check
     * @return true if system can handle it
     */
    public boolean canRunParallelWhisper(int numInstances) {
        double gbPerInstance = 4.0;
        double requiredRam = numInstances * gbPerInstance + 2; // +2GB for system
        
        double availableRam = getAvailableRamGB();
        
        if (availableRam < requiredRam) {
            logger.warn("Insufficient RAM for {} Whisper instances: need {:.1f}GB, have {:.1f}GB",
                    numInstances, requiredRam, availableRam);
            return false;
        }
        
        if (physicalCpuCount < numInstances) {
            logger.warn("Insufficient CPU cores for {} Whisper instances: need {}, have {}",
                    numInstances, numInstances, physicalCpuCount);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get encoding worker count (same as getOptimalWorkerCount("encoding"))
     */
    public int getEncodingWorkers() {
        int workers = Math.max(1, physicalCpuCount - 1);
        logger.debug("Encoding workers: {} (based on {} physical cores)", workers, physicalCpuCount);
        return workers;
    }
    
    /**
     * Format system info as JSON string for Python API
     */
    public String getSystemInfoJson() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"cpu_count_logical\":").append(logicalCpuCount).append(",");
        json.append("\"cpu_count_physical\":").append(physicalCpuCount).append(",");
        json.append("\"total_ram_gb\":").append(String.format("%.2f", getTotalRamGB())).append(",");
        json.append("\"available_ram_gb\":").append(String.format("%.2f", getAvailableRamGB()));
        json.append("}");
        return json.toString();
    }
}
