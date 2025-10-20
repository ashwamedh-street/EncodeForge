package com.encodeforge.controller.components;

import com.encodeforge.model.ConversionJob;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import java.io.File;
import java.nio.file.Path;
import java.util.List;


/**
 * FileInfoController - Display detailed file and media track information
 */
public class FileInfoController implements ISubController {
        
    public FileInfoController() {
        // TODO: Initialize with FXML fields, pythonBridge
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== updateFileInfo() ==========

    private void updateFileInfo(ConversionJob job) {
        if (job == null) {
            fileInfoLabel.setText("Select a file to view details");
            if (mediaInfoSection != null) {
                mediaInfoSection.setVisible(false);
                mediaInfoSection.setManaged(false);
            }
            return;
        }
        
        fileInfoLabel.setText(String.format(
            "File: %s\nPath: %s\nSize: %s\nStatus: %s",
            job.getFileName(),
            job.getInputPath(),
            job.getSizeString(),
            job.getStatus()
        ));
        
        // Show media info section and populate with detailed track info
        if (mediaInfoSection != null) {
            mediaInfoSection.setVisible(true);
            mediaInfoSection.setManaged(true);
        }
        
        // Fetch detailed media info from Python (non-blocking)
        new Thread(() -> {
            try {
                // Add a small delay if processing to avoid conflicts
                if (isProcessing) {
                    Thread.sleep(500);
                }
                
                JsonObject request = new JsonObject();
                request.addProperty("action", "get_media_info");
                request.addProperty("file_path", job.getInputPath());
                
                // Use a shorter timeout for media info during processing
                JsonObject response;
                if (isProcessing) {
                    // Quick timeout during processing to avoid blocking
                    response = pythonBridge.sendCommand(request);
                } else {
                    response = pythonBridge.sendCommand(request);
                }
                
                Platform.runLater(() -> {
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        updateMediaTracks(response);
                    } else {
                        // Show placeholder info during processing
                        if (isProcessing) {
                            showProcessingPlaceholder();
                        } else {
                            // Fallback to basic info
                            if (videoTracksListView != null) videoTracksListView.getItems().clear();
                            if (audioTracksListView != null) audioTracksListView.getItems().clear();
                            if (subtitleTracksListView != null) subtitleTracksListView.getItems().clear();
                        }
                    }
                });
            } catch (Exception e) {
                logger.debug("Could not get media info (possibly due to processing): " + e.getMessage());
                Platform.runLater(() -> {
                    if (isProcessing) {
                        showProcessingPlaceholder();
                    }
                });
            }
        }).start();
    }


    // ========== showProcessingPlaceholder() ==========

    private void showProcessingPlaceholder() {
        // Show placeholder information during processing
        if (videoTracksListView != null) {
            videoTracksListView.getItems().clear();
            videoTracksListView.getItems().add("🔄 Processing - Track info temporarily unavailable");
        }
        if (audioTracksListView != null) {
            audioTracksListView.getItems().clear();
            audioTracksListView.getItems().add("🔄 Processing - Track info temporarily unavailable");
        }
        if (subtitleTracksListView != null) {
            subtitleTracksListView.getItems().clear();
            subtitleTracksListView.getItems().add("🔄 Processing - Track info temporarily unavailable");
        }
    }


    // ========== updateMediaTracks() ==========

    private void updateMediaTracks(JsonObject mediaInfo) {
        // Update video tracks
        if (videoTracksListView != null && mediaInfo.has("video_tracks")) {
            videoTracksListView.getItems().clear();
            JsonArray videoTracks = mediaInfo.getAsJsonArray("video_tracks");
            for (int i = 0; i < videoTracks.size(); i++) {
                JsonObject track = videoTracks.get(i).getAsJsonObject();
                String trackInfo = String.format("Track %d: %s %s @ %s fps",
                    i + 1,
                    track.has("codec") ? track.get("codec").getAsString() : "Unknown",
                    track.has("resolution") ? track.get("resolution").getAsString() : "Unknown",
                    track.has("fps") ? track.get("fps").getAsString() : "Unknown"
                );
                videoTracksListView.getItems().add(trackInfo);
            }
        }
        
        // Update audio tracks
        if (audioTracksListView != null && mediaInfo.has("audio_tracks")) {
            audioTracksListView.getItems().clear();
            JsonArray audioTracks = mediaInfo.getAsJsonArray("audio_tracks");
            for (int i = 0; i < audioTracks.size(); i++) {
                JsonObject track = audioTracks.get(i).getAsJsonObject();
                String trackInfo = String.format("Track %d: %s (%s) - %s channels",
                    i + 1,
                    track.has("codec") ? track.get("codec").getAsString() : "Unknown",
                    track.has("language") ? track.get("language").getAsString() : "und",
                    track.has("channels") ? track.get("channels").getAsString() : "Unknown"
                );
                audioTracksListView.getItems().add(trackInfo);
            }
        }
        
        // Update subtitle tracks
        if (subtitleTracksListView != null && mediaInfo.has("subtitle_tracks")) {
            subtitleTracksListView.getItems().clear();
            JsonArray subTracks = mediaInfo.getAsJsonArray("subtitle_tracks");
            for (int i = 0; i < subTracks.size(); i++) {
                JsonObject track = subTracks.get(i).getAsJsonObject();
                String trackInfo = String.format("Track %d: %s (%s) - %s",
                    i + 1,
                    track.has("codec") ? track.get("codec").getAsString() : "Unknown",
                    track.has("language") ? track.get("language").getAsString() : "und",
                    track.has("forced") && track.get("forced").getAsBoolean() ? "Forced" : "Normal"
                );
                subtitleTracksListView.getItems().add(trackInfo);
            }
        }
    }

    // ========== File info FXML fields ==========

    // File Info Tab
    @FXML private Label fileInfoLabel;
    @FXML private VBox mediaInfoSection;
    @FXML private ListView<String> videoTracksListView;
    @FXML private ListView<String> audioTracksListView;
    @FXML private ListView<String> subtitleTracksListView;
    @FXML private TabPane rightPanelTabs;
    @FXML private Tab fileInfoTab;
    @FXML private Tab logsTab;

}

