package com.ffmpeg.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Detects available hardware encoders directly in Java for instant UI updates.
 * Caches results for 5 minutes to avoid repeated detection.
 */
public class HardwareDetector {
    private static final Logger logger = LoggerFactory.getLogger(HardwareDetector.class);
    
    private static Map<String, Boolean> encoderCache = null;
    private static long cacheTimestamp = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    
    /**
     * Detect available AND FUNCTIONAL hardware encoders.
     * Tests each encoder by actually running FFmpeg with it.
     * Results are cached for 5 minutes for instant subsequent calls.
     * 
     * @return Map of encoder names to availability (e.g., "h264_nvenc" -> true)
     */
    public static Map<String, Boolean> detectEncoders() {
        // Return cached results if available and fresh
        if (encoderCache != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS) {
            logger.debug("Using cached encoder detection (age: {}ms)", System.currentTimeMillis() - cacheTimestamp);
            return new HashMap<>(encoderCache);
        }
        
        logger.info("Detecting available encoders and testing functionality...");
        Map<String, Boolean> encoders = new HashMap<>();
        
        // Initialize all to false
        encoders.put("h264_nvenc", false);
        encoders.put("hevc_nvenc", false);
        encoders.put("h264_amf", false);
        encoders.put("hevc_amf", false);
        encoders.put("h264_qsv", false);
        encoders.put("hevc_qsv", false);
        encoders.put("h264_videotoolbox", false);
        encoders.put("hevc_videotoolbox", false);
        
        // Test each encoder to see if it actually works (not just compiled in)
        List<String> encodersToTest = new ArrayList<>();
        encodersToTest.add("h264_nvenc");
        encodersToTest.add("hevc_nvenc");
        encodersToTest.add("h264_amf");
        encodersToTest.add("hevc_amf");
        encodersToTest.add("h264_qsv");
        encodersToTest.add("hevc_qsv");
        
        // Only test VideoToolbox on macOS
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            encodersToTest.add("h264_videotoolbox");
            encodersToTest.add("hevc_videotoolbox");
        }
        
        // Test each encoder
        for (String encoderName : encodersToTest) {
            boolean works = testEncoder(encoderName);
            encoders.put(encoderName, works);
            if (works) {
                logger.info("Encoder {} is functional", encoderName);
            } else {
                logger.debug("Encoder {} is not functional or not available", encoderName);
            }
        }
        
        // Cache the results
        encoderCache = new HashMap<>(encoders);
        cacheTimestamp = System.currentTimeMillis();
        
        logger.info("Encoder detection complete: NVENC={}, AMF={}, QSV={}", 
                encoders.get("h264_nvenc"), 
                encoders.get("h264_amf"), 
                encoders.get("h264_qsv"));
        
        return encoders;
    }
    
    /**
     * Test if a specific encoder actually works by running a quick FFmpeg test.
     * 
     * @param encoderName The encoder to test (e.g., "h264_nvenc")
     * @return true if the encoder works, false otherwise
     */
    private static boolean testEncoder(String encoderName) {
        try {
            // Create a minimal test: 0.1 second black video to null output
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=black:size=320x240:duration=0.1",
                "-c:v", encoderName,
                "-f", "null", "-"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Wait for completion with timeout (5 seconds max)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            
            // Encoder works if exit code is 0
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            logger.debug("Encoder test failed for {}: {}", encoderName, e.getMessage());
            return false;
        }
    }
    
    // Removed detectGPUHardware method - now we test encoders directly
    
    /**
     * Detect NVIDIA GPU via nvidia-smi command.
     */
    private static boolean detectNvidiaGPU() {
        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=name", "--format=csv,noheader");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            boolean hasNvidia = line != null && !line.trim().isEmpty();
            
            process.waitFor(2, TimeUnit.SECONDS);
            reader.close();
            
            if (hasNvidia) {
                logger.info("Detected NVIDIA GPU: {}", line.trim());
            }
            
            return hasNvidia;
            
        } catch (Exception e) {
            logger.debug("nvidia-smi not found or failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Detect AMD GPU via Windows Management Instrumentation (WMI) on Windows.
     */
    private static boolean detectAMDGPU() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            try {
                // Use WMIC to query video controllers
                ProcessBuilder pb = new ProcessBuilder("wmic", "path", "win32_videocontroller", "get", "name");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean hasAMD = false;
                
                while ((line = reader.readLine()) != null) {
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("amd") || lowerLine.contains("radeon") || lowerLine.contains("ryzen")) {
                        hasAMD = true;
                        logger.info("Detected AMD GPU: {}", line.trim());
                        break;
                    }
                }
                
                process.waitFor(2, TimeUnit.SECONDS);
                reader.close();
                
                return hasAMD;
                
            } catch (Exception e) {
                logger.debug("AMD GPU detection failed: {}", e.getMessage());
                return false;
            }
        }
        
        // TODO: Add Linux/macOS detection methods
        return false;
    }
    
    /**
     * Build a user-friendly list of available encoders for the UI.
     * Note: "Auto (Best Available)" should be added by the caller if desired.
     */
    public static List<String> getAvailableEncoderList() {
        Map<String, Boolean> encoders = detectEncoders();
        List<String> list = new ArrayList<>();
        
        // Always add software encoders
        list.add("Software H.264");
        list.add("Software H.265");
        
        // Add hardware encoders if available
        if (encoders.get("h264_nvenc")) list.add("H.264 NVENC (GPU)");
        if (encoders.get("hevc_nvenc")) list.add("H.265 NVENC (GPU)");
        if (encoders.get("h264_amf")) list.add("H.264 AMF (GPU)");
        if (encoders.get("hevc_amf")) list.add("H.265 AMF (GPU)");
        if (encoders.get("h264_qsv")) list.add("H.264 Intel QSV (CPU)");
        if (encoders.get("hevc_qsv")) list.add("H.265 Intel QSV (CPU)");
        if (encoders.get("h264_videotoolbox")) list.add("H.264 VideoToolbox (GPU)");
        if (encoders.get("hevc_videotoolbox")) list.add("H.265 VideoToolbox (GPU)");
        
        list.add("Copy");
        
        return list;
    }
    
    /**
     * Get a recommended encoder based on detected hardware.
     */
    public static String getRecommendedEncoder() {
        Map<String, Boolean> encoders = detectEncoders();
        
        // Prefer NVENC for NVIDIA GPUs
        if (encoders.get("h264_nvenc")) return "H.264 NVENC (GPU)";
        
        // Then AMD AMF
        if (encoders.get("h264_amf")) return "H.264 AMF (GPU)";
        
        // Then Intel QSV
        if (encoders.get("h264_qsv")) return "H.264 Intel QSV (CPU)";
        
        // Then Apple VideoToolbox
        if (encoders.get("h264_videotoolbox")) return "H.264 VideoToolbox (GPU)";
        
        // Default to software
        return "Software H.264";
    }
    
    /**
     * Clear the cache to force re-detection.
     */
    public static void clearCache() {
        encoderCache = null;
        cacheTimestamp = 0;
        logger.info("Hardware detection cache cleared");
    }
}

