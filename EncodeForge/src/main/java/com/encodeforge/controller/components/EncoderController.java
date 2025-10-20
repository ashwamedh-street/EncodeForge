package com.encodeforge.controller.components;

import com.encodeforge.model.ConversionJob;
import com.encodeforge.util.HardwareDetector;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Tooltip;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;


/**
 * EncoderController - Handle video encoding operations
 */
public class EncoderController implements ISubController {
       
    public EncoderController() {
        // TODO: Initialize with FXML fields, queues, settings, pythonBridge
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== handleStartEncoding() ==========

    private void handleStartEncoding() {
        isProcessing = true;
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        statusLabel.setText("Encoding...");
        
        // Get files from queued list
        List<String> filePaths = new ArrayList<>();
        List<ConversionJob> jobsToProcess = new ArrayList<>();
        
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
            job.setStatus("⚡ Processing");
            job.setProgress(0.0);
            job.setStartTime(System.currentTimeMillis());
            job.setFps("0");
            job.setSpeed("0x");
            job.setEta("Calculating...");
            jobsToProcess.add(job);
        }
        
        if (filePaths.isEmpty()) {
            isProcessing = false;
            startButton.setDisable(false);
            pauseButton.setDisable(true);
            stopButton.setDisable(true);
            log("No files to encode.");
            showWarning("No Files", "No files available to encode.");
            return;
        }
        
        // Move files from queued to processing
        processingFiles.addAll(jobsToProcess);
        queuedFiles.clear();
        updateQueueCounts();
        
        log("Starting encoding of " + filePaths.size() + " file(s)");
        
        // Update settings from quick settings UI
        updateSettingsFromQuickUI();
        
        // Start conversion in background
        new Thread(() -> {
            try {
                pythonBridge.convertFiles(settings.toJson(), filePaths, this::handleProgressUpdate);
            } catch (IOException e) {
                logger.error("Error starting conversion", e);
                Platform.runLater(() -> {
                    showError("Encoding Error", "Failed to start encoding: " + e.getMessage());
                    resetProcessingState();
                });
            }
        }).start();
    }


    // ========== handleProgressUpdate() ==========

    private void handleProgressUpdate(JsonObject update) {
        Platform.runLater(() -> {
            if (update.has("progress")) {
                double progress = update.get("progress").getAsDouble();
                String fileName = update.get("file").getAsString();
                String status = update.has("status") ? update.get("status").getAsString() : "processing";
                
                // Progress is now displayed in the table, not separate UI elements
                
                // Build detailed status text with all metrics
                StringBuilder statusText = new StringBuilder();
                
                // Status with emoji
                switch (status.toLowerCase()) {
                    case "starting":
                        statusText.append("🔄 Starting");
                        break;
                    case "encoding":
                        statusText.append("⚡ Encoding");
                        break;
                    case "finalizing":
                        statusText.append("🔧 Finalizing");
                        break;
                    case "completed":
                        statusText.append("✅ Completed");
                        break;
                    case "cancelled":
                        statusText.append("❌ Cancelled");
                        break;
                    case "failed":
                    case "error":
                        statusText.append("⚠️ Failed");
                        break;
                    case "paused":
                        statusText.append("⏸️ Paused");
                        break;
                    default:
                        statusText.append("🔄 ").append(status.substring(0, 1).toUpperCase()).append(status.substring(1));
                }
                
                // Add frame count if available
                if (update.has("frame")) {
                    int frame = update.get("frame").getAsInt();
                    if (frame > 0) {
                        statusText.append(" • Frame ").append(frame);
                    }
                }
                
                // Add FPS if available
                if (update.has("fps")) {
                    double fps = update.get("fps").getAsDouble();
                    if (fps > 0) {
                        statusText.append(" • ").append(String.format("%.1f fps", fps));
                    }
                }
                
                // Add speed if available
                if (update.has("speed") && !update.get("speed").getAsString().equals("0x")) {
                    statusText.append(" • ").append(update.get("speed").getAsString());
                }
                
                // Add bitrate if available
                if (update.has("bitrate") && !update.get("bitrate").getAsString().equals("0kbits/s")) {
                    statusText.append(" • ").append(update.get("bitrate").getAsString());
                }
                
                // Add ETA if available
                if (update.has("eta") && !update.get("eta").getAsString().equals("Unknown") && 
                    !update.get("eta").getAsString().equals("Calculating...")) {
                    statusText.append(" • ETA: ").append(update.get("eta").getAsString());
                }
                
                statusLabel.setText(statusText.toString());
                
                // Update job in processing list
                ConversionJob job = processingFiles.stream()
                    .filter(j -> j.getInputPath().equals(fileName))
                    .findFirst()
                    .orElse(null);
                
                if (job != null) {
                    if (progress >= 0) {
                        job.setProgress(progress);
                    }
                    
                    // Update real-time metrics
                    if (update.has("fps")) {
                        job.setFps(String.format("%.1f", update.get("fps").getAsDouble()));
                    }
                    if (update.has("speed")) {
                        job.setSpeed(update.get("speed").getAsString());
                    }
                    if (update.has("eta")) {
                        job.setEta(update.get("eta").getAsString());
                    }
                    
                    // Handle completion - move to completed list
                    if (status.equals("completed")) {
                        job.setStatus("✅ Completed");
                        job.setProgress(100.0);
                        
                        // Calculate time taken
                        long elapsedMs = System.currentTimeMillis() - job.getStartTime();
                        long minutes = elapsedMs / 60000;
                        long seconds = (elapsedMs % 60000) / 1000;
                        job.setTimeTaken(String.format("%dm %ds", minutes, seconds));
                        
                        // Get output file size
                        try {
                            // Construct output path (same directory, new extension)
                            Path inputPath = java.nio.file.Paths.get(job.getInputPath());
                            String baseName = inputPath.getFileName().toString();
                            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
                            Path outputPath = inputPath.getParent().resolve(baseName + "." + settings.getOutputFormat());
                            
                            if (java.nio.file.Files.exists(outputPath)) {
                                long outputSize = java.nio.file.Files.size(outputPath);
                                job.setOutputSizeString(formatFileSize(outputSize));
                                job.setOutputPath(outputPath.toString());
                            }
                        } catch (Exception e) {
                            job.setOutputSizeString("N/A");
                        }
                        
                        // Move from processing to completed
                        processingFiles.remove(job);
                        completedFiles.add(job);
                        updateQueueCounts();
                    }
                    
                    processingTable.refresh();
                }
                
            } else if (update.has("status")) {
                String status = update.get("status").getAsString();
                
                if (status.equals("complete")) {
                    log("All conversions completed successfully");
                    
                    // Move any remaining processing files to completed
                    for (ConversionJob job : new ArrayList<>(processingFiles)) {
                        job.setStatus("✅ Completed");
                        job.setProgress(100.0);
                        
                        // Calculate time taken if not already set
                        if (job.getTimeTaken() == null || job.getTimeTaken().equals("N/A")) {
                            long elapsedMs = System.currentTimeMillis() - job.getStartTime();
                            long minutes = elapsedMs / 60000;
                            long seconds = (elapsedMs % 60000) / 1000;
                            job.setTimeTaken(String.format("%dm %ds", minutes, seconds));
                        }
                        
                        processingFiles.remove(job);
                        completedFiles.add(job);
                    }
                    
                    updateQueueCounts();
                    resetProcessingState();
                    statusLabel.setText("✅ All files completed!");
                    
                } else if (status.equals("cancelled")) {
                    String message = update.has("message") ? update.get("message").getAsString() : "Conversion cancelled";
                    log("CANCELLED: " + message);
                    
                    // Move processing jobs back to queued with cancelled status
                    for (ConversionJob job : new ArrayList<>(processingFiles)) {
                        job.setStatus("❌ Cancelled");
                        job.setProgress(0.0);
                        processingFiles.remove(job);
                        queuedFiles.add(job);
                    }
                    
                    updateQueueCounts();
                    resetProcessingState();
                    statusLabel.setText("❌ Cancelled");
                    
                } else if (status.equals("error") || status.equals("failed")) {
                    String message = update.has("message") ? update.get("message").getAsString() : "Unknown error";
                    log("ERROR: " + message);
                    
                    // Move processing jobs back to queued with failed status
                    for (ConversionJob job : new ArrayList<>(processingFiles)) {
                        job.setStatus("⚠️ Failed");
                        job.setProgress(0.0);
                        processingFiles.remove(job);
                        queuedFiles.add(job);
                    }
                    
                    updateQueueCounts();
                    showError("Conversion Error", message);
                    resetProcessingState();
                }
            }
        });
    }


    // ========== resetProcessingState() ==========

    private void resetProcessingState() {
        isProcessing = false;
        updateQueueCounts();
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        statusLabel.setText("Ready");
    }


    // ========== checkFFmpegAvailability() ==========

    private void checkFFmpegAvailability() {
        new Thread(() -> {
            try {
                JsonObject response = pythonBridge.checkFFmpeg();
                Platform.runLater(() -> {
                    if (response.has("status") && response.get("status").getAsString().equals("error")) {
                        log("ERROR: " + response.get("message").getAsString());
                        updateFFmpegStatus(false, "Not Found");
                        return;
                    }
                    
                    if (response.has("ffmpeg_available") && response.get("ffmpeg_available").getAsBoolean()) {
                        String version = response.get("ffmpeg_version").getAsString();
                        log("FFmpeg detected: " + version);
                        
                        // Update sidebar FFmpeg status
                        updateFFmpegStatus(true, version);
                        
                        // Log hardware capabilities
                        if (response.has("hardware_encoders")) {
                            JsonArray encoders = response.getAsJsonArray("hardware_encoders");
                            if (encoders.size() > 0) {
                                log("Hardware encoders available: " + encoders.size());
                            }
                        }
                        
                        // Note: Encoder detection is now handled by Java HardwareDetector
                        // No need to update encoder list from Python backend
                    } else {
                        log("WARNING: FFmpeg not detected");
                        updateFFmpegStatus(false, "Not Found");
                        showWarning("FFmpeg Not Found", 
                            "FFmpeg could not be detected. Please install FFmpeg or set the path in Settings.");
                    }
                });
            } catch (IOException | TimeoutException e) {
                logger.error("Error checking FFmpeg", e);
                Platform.runLater(() -> {
                    log("ERROR checking FFmpeg: " + e.getMessage());
                    updateFFmpegStatus(false, "Error");
                });
            } catch (Exception e) {
                logger.error("Unexpected error checking FFmpeg", e);
                Platform.runLater(() -> {
                    log("ERROR: " + e.getMessage());
                    updateFFmpegStatus(false, "Error");
                });
            }
        }).start();
    }


    // ========== updateAvailableEncoders() ==========

    private void updateAvailableEncoders() {
        new Thread(() -> {
            try {
                // Create request for available encoders
                JsonObject request = new JsonObject();
                request.addProperty("action", "get_available_encoders");
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                Platform.runLater(() -> {
                    if (response.has("status") && response.get("status").getAsString().equals("success")) {
                        JsonObject encoderSupport = response.getAsJsonObject("encoder_support");
                        
                        // Store current selection to preserve it
                        String currentSelection = quickCodecCombo != null ? quickCodecCombo.getValue() : null;
                        
                        // Build list of available encoders
                        List<String> availableEncoders = new ArrayList<>();
                        
                        // Always add software encoders
                        availableEncoders.add("Software H.264");
                        availableEncoders.add("Software H.265");
                        
                        // Add hardware encoders if available
                        boolean hasNvidiaH264 = false;
                        boolean hasNvidiaH265 = false;
                        
                        if (encoderSupport.has("nvidia_h264") && encoderSupport.get("nvidia_h264").getAsBoolean()) {
                            availableEncoders.add("H.264 NVENC (GPU)");
                            hasNvidiaH264 = true;
                        }
                        if (encoderSupport.has("nvidia_h265") && encoderSupport.get("nvidia_h265").getAsBoolean()) {
                            availableEncoders.add("H.265 NVENC (GPU)");
                            hasNvidiaH265 = true;
                        }
                        if (encoderSupport.has("amd_h264") && encoderSupport.get("amd_h264").getAsBoolean()) {
                            availableEncoders.add("H.264 AMF (GPU)");
                        }
                        if (encoderSupport.has("amd_h265") && encoderSupport.get("amd_h265").getAsBoolean()) {
                            availableEncoders.add("H.265 AMF (GPU)");
                        }
                        if (encoderSupport.has("intel_h264") && encoderSupport.get("intel_h264").getAsBoolean()) {
                            availableEncoders.add("H.264 Intel QSV (CPU)");
                        }
                        if (encoderSupport.has("intel_h265") && encoderSupport.get("intel_h265").getAsBoolean()) {
                            availableEncoders.add("H.265 Intel QSV (CPU)");
                        }
                        if (encoderSupport.has("apple_h264") && encoderSupport.get("apple_h264").getAsBoolean()) {
                            availableEncoders.add("H.264 VideoToolbox (GPU)");
                        }
                        if (encoderSupport.has("apple_h265") && encoderSupport.get("apple_h265").getAsBoolean()) {
                            availableEncoders.add("H.265 VideoToolbox (GPU)");
                        }
                        
                        // Always add copy option
                        availableEncoders.add("Copy");
                        
                        // Update the ComboBox only if hardware options are different
                        if (quickCodecCombo != null) {
                            quickCodecCombo.setItems(FXCollections.observableArrayList(availableEncoders));
                            
                            // If current selection is still valid, keep it
                            if (currentSelection != null && availableEncoders.contains(currentSelection)) {
                                quickCodecCombo.setValue(currentSelection);
                            } else if (!hasNvidiaH264 && !hasNvidiaH265 && currentSelection != null && 
                                       (currentSelection.contains("NVENC") || currentSelection.contains("AMF"))) {
                                // GPU was not detected but user had GPU selected - fallback to software
                                quickCodecCombo.setValue("Software H.264");
                                log("GPU encoder not available - using software encoding");
                            } else if (currentSelection == null) {
                                // First time setup - use recommended
                                if (response.has("recommended_encoder")) {
                                    String recommended = response.get("recommended_encoder").getAsString();
                                    if (recommended.equals("h264_nvenc") && hasNvidiaH264) {
                                        quickCodecCombo.setValue("H.264 NVENC (GPU)");
                                    } else if (recommended.equals("h264_amf") && availableEncoders.contains("H.264 AMF (GPU)")) {
                                        quickCodecCombo.setValue("H.264 AMF (GPU)");
                                    } else if (recommended.equals("h264_qsv") && availableEncoders.contains("H.264 Intel QSV (CPU)")) {
                                        quickCodecCombo.setValue("H.264 Intel QSV (CPU)");
                                    } else {
                                        quickCodecCombo.setValue("Software H.264");
                                    }
                                } else if (hasNvidiaH264) {
                                    quickCodecCombo.setValue("H.264 NVENC (GPU)");
                                } else {
                                    quickCodecCombo.setValue("Software H.264");
                                }
                            }
                        }
                        
                        // Log the available encoders
                        log("Available encoders updated: " + availableEncoders.size() + " options");
                        
                        // Show hardware acceleration status
                        boolean hasHardwareAccel = availableEncoders.stream()
                            .anyMatch(encoder -> encoder.contains("NVENC") || encoder.contains("AMF") || 
                                     encoder.contains("QSV") || encoder.contains("VideoToolbox"));
                        
                        if (hasHardwareAccel) {
                            log("Hardware acceleration available");
                        } else {
                            log("No hardware acceleration available - using software encoding");
                        }
                        
                    } else {
                        logger.warn("Failed to get available encoders: " + response.get("message").getAsString());
                        log("WARNING: Could not detect available encoders, using software fallback");
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error updating available encoders", e);
                Platform.runLater(() -> {
                    log("ERROR: Could not update encoder options - " + e.getMessage());
                });
            }
        }).start();
    }


    // ========== updateSettingsFromQuickUI() ==========

    private void updateSettingsFromQuickUI() {
        // Update video settings from quick settings UI
        if (quickFormatCombo != null && quickFormatCombo.getValue() != null) {
            settings.setOutputFormat(quickFormatCombo.getValue());
        }
        
        if (quickCodecCombo != null && quickCodecCombo.getValue() != null) {
            String selectedCodec = quickCodecCombo.getValue();
            settings.setVideoCodec(selectedCodec);
            
            // Update hardware acceleration flags based on codec selection
            if (selectedCodec.contains("NVENC")) {
                settings.setUseNvenc(true);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("AMF")) {
                settings.setUseNvenc(false);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("QSV")) {
                settings.setUseNvenc(false);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("VideoToolbox")) {
                settings.setUseNvenc(false);
                settings.setHardwareDecoding(true);
            } else {
                // Software encoding
                settings.setUseNvenc(false);
                settings.setHardwareDecoding(false);
            }
        }
        
        if (quickQualityCombo != null && quickQualityCombo.getValue() != null) {
            String quality = quickQualityCombo.getValue();
            // Extract CRF value from quality string like "Medium (CQ 23)"
            if (quality.contains("CQ ")) {
                try {
                    String crfStr = quality.substring(quality.indexOf("CQ ") + 3, quality.indexOf(")"));
                    int crf = Integer.parseInt(crfStr);
                    settings.setCrfValue(crf);
                    settings.setNvencCq(crf); // Also set NVENC CQ
                } catch (Exception e) {
                    logger.warn("Could not parse CRF from quality setting: " + quality);
                }
            }
        }
        
        if (quickPresetCombo != null && quickPresetCombo.getValue() != null) {
            settings.setQualityPreset(quickPresetCombo.getValue());
        }
        
        if (quickHwAccelCheck != null) {
            settings.setHardwareDecoding(quickHwAccelCheck.isSelected());
        }
        
        if (quickDownloadSubsCheck != null) {
            settings.setDownloadSubtitles(quickDownloadSubsCheck.isSelected());
        }
        
        // Log the updated settings
        logger.debug("Updated settings from quick UI - Codec: {}, Format: {}, Hardware: {}", 
                    settings.getVideoCodec(), settings.getOutputFormat(), settings.isHardwareDecoding());
    }


    // ========== setupQuickSettings() - encoder ==========

    private void setupQuickSettings() {
        // Initialize encoder quick settings
        if (quickFormatCombo != null) {
            quickFormatCombo.setItems(FXCollections.observableArrayList(
                "MP4", "MKV", "AVI", "MOV", "WEBM", "TS"
            ));
            quickFormatCombo.setValue("MP4");
        }
        
        // Initialize codec combo with INSTANT hardware detection via Java
        if (quickCodecCombo != null) {
            List<String> availableEncoders = HardwareDetector.getAvailableEncoderList();
            
            // Add "Auto (Best Available)" option at the top
            availableEncoders.add(0, "Auto (Best Available)");
            
            quickCodecCombo.setItems(FXCollections.observableArrayList(availableEncoders));
            
            // Try to load saved codec preference, otherwise use Auto
            String savedCodec = settings.getVideoCodec();
            if (savedCodec != null && availableEncoders.contains(savedCodec)) {
                quickCodecCombo.setValue(savedCodec);
                logger.info("Loaded saved codec preference: {}", savedCodec);
            } else {
                quickCodecCombo.setValue("Auto (Best Available)");
                logger.info("Using Auto codec selection");
            }
            
            logger.info("Codec options initialized instantly with {} encoders", availableEncoders.size());
        }
        
        if (quickFormatCombo != null) {
            String savedFormat = settings.getOutputFormat();
            if (savedFormat != null && !savedFormat.isEmpty()) {
                quickFormatCombo.setValue(savedFormat.toUpperCase());
                logger.debug("Loaded saved format: {}", savedFormat);
            }
        }
        
        if (quickQualityCombo != null) {
            quickQualityCombo.setItems(FXCollections.observableArrayList(
                "Highest (CQ 15)", "High (CQ 18)", "Medium (CQ 23)", "Low (CQ 28)", "Very Low (CQ 32)"
            ));
            // Load saved CRF value
            int savedCrf = settings.getCrfValue();
            String qualityLabel = "Medium (CQ 23)";
            if (savedCrf <= 15) qualityLabel = "Highest (CQ 15)";
            else if (savedCrf <= 18) qualityLabel = "High (CQ 18)";
            else if (savedCrf <= 23) qualityLabel = "Medium (CQ 23)";
            else if (savedCrf <= 28) qualityLabel = "Low (CQ 28)";
            else qualityLabel = "Very Low (CQ 32)";
            quickQualityCombo.setValue(qualityLabel);
            logger.debug("Loaded saved quality: {} (CRF {})", qualityLabel, savedCrf);
        }
        
        if (quickPresetCombo != null) {
            quickPresetCombo.setItems(FXCollections.observableArrayList(
                "Fast", "Medium", "Slow", "Quality", "Balanced"
            ));
            String savedPreset = settings.getQualityPreset();
            if (savedPreset != null && !savedPreset.isEmpty()) {
                // Normalize saved preset to match combo box options
                String normalizedPreset = savedPreset.substring(0, 1).toUpperCase() + savedPreset.substring(1).toLowerCase();
                if ("Fast".equals(normalizedPreset) || "Medium".equals(normalizedPreset) || 
                    "Slow".equals(normalizedPreset) || "Quality".equals(normalizedPreset) || 
                    "Balanced".equals(normalizedPreset)) {
                    quickPresetCombo.setValue(normalizedPreset);
                } else {
                    quickPresetCombo.setValue("Medium");
                }
                logger.debug("Loaded saved preset: {}", normalizedPreset);
            } else {
                quickPresetCombo.setValue("Medium");
            }
        }
        
        if (quickHwAccelCheck != null) {
            quickHwAccelCheck.setSelected(settings.isHardwareDecoding());
            logger.debug("Loaded saved hardware acceleration: {}", settings.isHardwareDecoding());
        }
        
        if (quickDownloadSubsCheck != null) {
            quickDownloadSubsCheck.setSelected(false);
        }
        
        if (quickRenameCheck != null) {
            quickRenameCheck.setSelected(false);
        }
        
        // Initialize subtitle output mode combo
        if (subtitleOutputModeCombo != null) {
            subtitleOutputModeCombo.setItems(FXCollections.observableArrayList(
                "External File (No Processing)",
                "Embed in Video (Fast, No Re-encode)",
                "Burn-in to Video (Slow, Permanent)"
            ));
            subtitleOutputModeCombo.setValue("External File (No Processing)");
            subtitleOutputModeCombo.setTooltip(new Tooltip(
                "External: Saves .srt file next to video (instant)\n" +
                "Embed: Adds subtitle track inside video container (fast, toggleable)\n" +
                "Burn-in: Permanently overlays subtitles on video frames (slow, always visible)"
            ));
        }
        
        // Initialize subtitle quick settings
        if (quickSubProviderCombo != null) {
            quickSubProviderCombo.setItems(FXCollections.observableArrayList(
                "Automatic (Download + AI if needed)",
                "Download Only (Multi-provider)",
                "AI Generation Only (Whisper)",
                "Both (Download & AI)"
            ));
            quickSubProviderCombo.setValue("Automatic (Download + AI if needed)");
            quickSubProviderCombo.setTooltip(new Tooltip(
                "Multi-provider download includes:\n" +
                "• OpenSubtitles.com (with API key)\n" +
                "• OpenSubtitles.org (free, no auth)\n" +
                "• YIFY Subtitles (movies)\n" +
                "• Podnapisi.NET\n" +
                "• SubDivX (Spanish)"
            ));
        }
        
        // Language checkboxes are already initialized with default values in FXML
        // Log level combos for modes
        if (subtitleLogLevelCombo != null) {
            subtitleLogLevelCombo.setItems(FXCollections.observableArrayList("All", "Info", "Warning", "Error"));
            subtitleLogLevelCombo.setValue("All");
        }
        if (renamerLogLevelCombo != null) {
            renamerLogLevelCombo.setItems(FXCollections.observableArrayList("All", "Info", "Warning", "Error"));
            renamerLogLevelCombo.setValue("All");
        }
        
        // Initialize renamer quick settings
        if (quickRenameProviderCombo != null) {
            quickRenameProviderCombo.setItems(FXCollections.observableArrayList(
                "Automatic", "TMDB", "TVDB", "AniList"
            ));
            quickRenameProviderCombo.setValue("Automatic");
        }
        
        if (quickRenameTypeCombo != null) {
            quickRenameTypeCombo.setItems(FXCollections.observableArrayList(
                "Auto-Detect", "TV Show", "Movie", "Anime"
            ));
            quickRenameTypeCombo.setValue("Auto-Detect");
        }
        
        // Show encoder settings by default
        showModePanel(encoderQuickSettings);
    }


    // ========== updateFFmpegStatus() ==========

    private void updateFFmpegStatus(boolean found, String statusText) {
        logger.debug("updateFFmpegStatus called: found={}, statusText={}", found, statusText);
        logger.debug("Button null? {}, Label null? {}, Icon null? {}", 
            ffmpegStatusButton == null, ffmpegStatusLabel == null, ffmpegStatusIcon == null);
        
        if (ffmpegStatusButton == null || ffmpegStatusLabel == null || ffmpegStatusIcon == null) {
            logger.warn("FFmpeg status UI elements not initialized yet");
            return;
        }
        
        // Remove all status classes
        ffmpegStatusButton.getStyleClass().removeAll("ffmpeg-found", "ffmpeg-not-found", "ffmpeg-checking");
        
        if (found) {
            ffmpegStatusButton.getStyleClass().add("ffmpeg-found");
            ffmpegStatusLabel.setText(statusText);
            ffmpegStatusIcon.setText("✓");
            logger.info("FFmpeg status updated: FOUND - {}", statusText);
        } else {
            ffmpegStatusButton.getStyleClass().add("ffmpeg-not-found");
            ffmpegStatusLabel.setText(statusText);
            ffmpegStatusIcon.setText("✗");
            logger.info("FFmpeg status updated: NOT FOUND - {}", statusText);
        }
    }


    // ========== handleFFmpegStatus() ==========

    @FXML
    private void handleFFmpegStatus() {
        handleSettings();
    }

    // ========== Encoder quick settings FXML fields ==========

    // Encoder Quick Settings
    @FXML private ComboBox<String> quickFormatCombo;
    @FXML private ComboBox<String> quickCodecCombo;
    @FXML private ComboBox<String> quickQualityCombo;
    @FXML private ComboBox<String> quickPresetCombo;
    @FXML private CheckBox quickHwAccelCheck;
    @FXML private CheckBox quickDownloadSubsCheck;
    @FXML private CheckBox quickRenameCheck;
    @FXML private Button configSubtitlesButton;
    @FXML private Button configRenamerButton;
    

}

