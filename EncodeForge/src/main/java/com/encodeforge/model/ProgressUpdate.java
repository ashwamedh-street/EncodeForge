package com.encodeforge.model;

/**
 * Progress update model for dependency installation and downloads
 */
public class ProgressUpdate {
    private final String stage;      // "detecting", "downloading", "installing", "verifying"
    private final int progress;      // 0-100
    private final String message;    // Human-readable status
    private final String detail;     // Technical details for log

    public ProgressUpdate(String stage, int progress, String message, String detail) {
        this.stage = stage;
        this.progress = Math.max(0, Math.min(100, progress)); // Clamp to 0-100
        this.message = message;
        this.detail = detail;
    }

    public String getStage() {
        return stage;
    }

    public int getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return String.format("[%s] %d%% - %s", stage, progress, message);
    }
}

