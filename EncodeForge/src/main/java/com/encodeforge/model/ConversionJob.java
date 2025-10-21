package com.encodeforge.model;

import javafx.beans.property.*;

import java.io.File;

/**
 * Model representing a video conversion job
 */
public class ConversionJob {
    private final StringProperty inputPath;
    private final StringProperty fileName;
    private final StringProperty status;
    private final StringProperty statusIcon;
    private final StringProperty operation;
    private final DoubleProperty progress;
    private final StringProperty sizeString;
    private final LongProperty sizeBytes;
    private final StringProperty outputFormat;
    private final StringProperty speed;
    private final StringProperty eta;
    private final StringProperty fps;
    private final StringProperty outputSizeString;
    private final StringProperty timeTaken;
    private String outputPath;
    private long startTime;
    
    public ConversionJob(String inputPath) {
        this.inputPath = new SimpleStringProperty(inputPath);
        
        File file = new File(inputPath);
        this.fileName = new SimpleStringProperty(file.getName());
        this.status = new SimpleStringProperty("pending");
        this.statusIcon = new SimpleStringProperty("⏳");
        this.operation = new SimpleStringProperty("Queued");
        this.progress = new SimpleDoubleProperty(0.0);
        this.sizeBytes = new SimpleLongProperty(file.length());
        this.sizeString = new SimpleStringProperty(formatSize(file.length()));
        this.outputFormat = new SimpleStringProperty("MP4");
        this.speed = new SimpleStringProperty("-");
        this.eta = new SimpleStringProperty("-");
        this.fps = new SimpleStringProperty("0");
        this.outputSizeString = new SimpleStringProperty("N/A");
        this.timeTaken = new SimpleStringProperty("N/A");
        this.outputPath = null;
        this.startTime = 0;
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    // Property getters
    public StringProperty inputPathProperty() { return inputPath; }
    public StringProperty fileNameProperty() { return fileName; }
    public StringProperty statusProperty() { return status; }
    public StringProperty statusIconProperty() { return statusIcon; }
    public StringProperty operationProperty() { return operation; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty sizeStringProperty() { return sizeString; }
    public LongProperty sizeBytesProperty() { return sizeBytes; }
    public StringProperty outputFormatProperty() { return outputFormat; }
    public StringProperty speedProperty() { return speed; }
    public StringProperty etaProperty() { return eta; }
    public StringProperty fpsProperty() { return fps; }
    public StringProperty outputSizeStringProperty() { return outputSizeString; }
    public StringProperty timeTakenProperty() { return timeTaken; }
    
    // Value getters
    public String getInputPath() { return inputPath.get(); }
    public String getFileName() { return fileName.get(); }
    public String getStatus() { return status.get(); }
    public String getStatusIcon() { return statusIcon.get(); }
    public String getOperation() { return operation.get(); }
    public double getProgress() { return progress.get(); }
    public String getSizeString() { return sizeString.get(); }
    public long getSizeBytes() { return sizeBytes.get(); }
    public String getOutputFormat() { return outputFormat.get(); }
    public String getSpeed() { return speed.get(); }
    public String getEta() { return eta.get(); }
    public String getFps() { return fps.get(); }
    public String getOutputSizeString() { return outputSizeString.get(); }
    public String getTimeTaken() { return timeTaken.get(); }
    public String getOutputPath() { return outputPath; }
    public long getStartTime() { return startTime; }
    
    // Setters
    public void setStatus(String status) { 
        this.status.set(status);
        updateStatusIcon();
    }
    
    public void setOperation(String operation) { this.operation.set(operation); }
    
    public void setProgress(double progress) { this.progress.set(progress); }
    
    public void setOutputFormat(String outputFormat) { this.outputFormat.set(outputFormat); }
    
    public void setSpeed(String speed) { this.speed.set(speed); }
    
    public void setEta(String eta) { this.eta.set(eta); }
    
    public void setFps(String fps) { this.fps.set(fps); }
    
    public void setOutputSizeString(String size) { this.outputSizeString.set(size); }
    
    public void setTimeTaken(String time) { this.timeTaken.set(time); }
    
    public void setOutputPath(String path) { this.outputPath = path; }
    
    public void setStartTime(long time) { this.startTime = time; }
    
    private void updateStatusIcon() {
        String currentStatus = status.get();
        switch (currentStatus) {
            case "pending":
            case "queued":
                statusIcon.set("⏳");
                break;
            case "processing":
            case "⚡ Processing":
                statusIcon.set("▶️");
                break;
            case "completed":
            case "✅ Completed":
                statusIcon.set("✅");
                break;
            case "error":
            case "❌ Error":
                statusIcon.set("❌");
                break;
            case "paused":
            case "⏸️ Paused":
                statusIcon.set("⏸️");
                break;
            default:
                // For new status types, extract emoji from status string
                if (currentStatus.startsWith("🔍")) {
                    statusIcon.set("🔍");
                } else if (currentStatus.startsWith("📝")) {
                    statusIcon.set("📝");
                } else if (currentStatus.startsWith("⚙️")) {
                    statusIcon.set("⚙️");
                } else if (currentStatus.startsWith("⚡")) {
                    statusIcon.set("⚡");
                } else {
                    statusIcon.set("❓");
                }
        }
    }
}
