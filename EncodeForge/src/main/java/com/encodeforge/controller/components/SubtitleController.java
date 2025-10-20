package com.encodeforge.controller.components;

import com.encodeforge.model.ConversionJob;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;


/**
 * SubtitleController - Handle subtitle search, download, and application
 */
public class SubtitleController implements ISubController {
    
    public SubtitleController() {
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
    

    // ========== setupSubtitleFileSelector() ==========

    private void setupSubtitleFileSelector() {
        if (subtitleFileCombo != null) {
            // Add listener for file selection changes
            subtitleFileCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(currentlySelectedFile)) {
                    currentlySelectedFile = newVal;
                    
                    // Extract base filename without status tags
                    String baseFileName = extractBaseFileName(newVal);
                    
                    // Update table to show subtitles for selected file
                    if (subtitlesByFile.containsKey(baseFileName) && availableSubtitlesTable != null) {
                        ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.get(baseFileName);
                        availableSubtitlesTable.setItems(fileSubtitles);
                        
                        // Update stats
                        if (subtitleStatsLabel != null) {
                            int uniqueLangs = fileSubtitles.stream()
                                .map(SubtitleItem::getLanguage)
                                .collect(java.util.stream.Collectors.toSet())
                                .size();
                            subtitleStatsLabel.setText(fileSubtitles.size() + " available | " + uniqueLangs + " language(s)");
                        }
                        log("Displaying subtitles for: " + baseFileName);
                    }
                }
            });
        }
    }


    // ========== updateSubtitleFileList() ==========

    private void updateSubtitleFileList() {
        if (subtitleFileCombo != null && !queuedFiles.isEmpty()) {
            subtitleFileCombo.getItems().clear();
            
            // Add files with status tags
            for (ConversionJob job : queuedFiles) {
                String fileName = new java.io.File(job.getInputPath()).getName();
                String displayName = formatFileNameWithStatus(fileName);
                subtitleFileCombo.getItems().add(displayName);
            }
            
            // Select first file if none selected
            if (currentlySelectedFile == null && !subtitleFileCombo.getItems().isEmpty()) {
                currentlySelectedFile = subtitleFileCombo.getItems().get(0);
                subtitleFileCombo.setValue(currentlySelectedFile);
            } else if (currentlySelectedFile != null) {
                // Update the display of currently selected file
                String updatedDisplayName = formatFileNameWithStatus(extractBaseFileName(currentlySelectedFile));
                subtitleFileCombo.setValue(updatedDisplayName);
            }
        }
    }


    // ========== formatFileNameWithStatus() ==========

    private String formatFileNameWithStatus(String fileName) {
        SubtitleSearchStatus status = subtitleSearchStatus.getOrDefault(fileName, SubtitleSearchStatus.NONE);
        int subtitleCount = subtitlesByFile.containsKey(fileName) ? subtitlesByFile.get(fileName).size() : 0;
        
        switch (status) {
            case SEARCHING:
                return fileName + " [Searching...]";
            case COMPLETED:
                if (subtitleCount > 0) {
                    return fileName + " [✓ " + subtitleCount + " subs]";
                } else {
                    return fileName + " [No results]";
                }
            default:
                return fileName;
        }
    }


    // ========== extractBaseFileName() ==========

    private String extractBaseFileName(String displayName) {
        // Remove status tags to get original filename
        if (displayName.contains(" [")) {
            return displayName.substring(0, displayName.indexOf(" ["));
        }
        return displayName;
    }


    // ========== setupSubtitleTable() ==========

    private void setupSubtitleTable() {
        if (availableSubtitlesTable == null) {
            return;
        }
        
        // Setup checkbox column
        subtitleSelectColumn.setCellValueFactory(cd -> 
            new javafx.beans.property.SimpleBooleanProperty(cd.getValue().isSelected()));
        subtitleSelectColumn.setCellFactory(col -> new javafx.scene.control.TableCell<SubtitleItem, Boolean>() {
            private final javafx.scene.control.CheckBox checkBox = new javafx.scene.control.CheckBox();
            
            {
                checkBox.setOnAction(e -> {
                    SubtitleItem item = getTableView().getItems().get(getIndex());
                    item.setSelected(checkBox.isSelected());
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item != null && item);
                    setGraphic(checkBox);
                }
            }
        });
        
        // Setup other columns
        subtitleLanguageColumn.setCellValueFactory(cd -> 
            new javafx.beans.property.SimpleStringProperty(cd.getValue().getLanguage()));
        subtitleProviderColumn.setCellValueFactory(cd -> 
            new javafx.beans.property.SimpleStringProperty(cd.getValue().getProvider()));
        subtitleScoreColumn.setCellValueFactory(cd -> 
            new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getScore()));
        subtitleFormatColumn.setCellValueFactory(cd -> 
            new javafx.beans.property.SimpleStringProperty(cd.getValue().getFormat()));
        
        // Selection listener to update details panel
        availableSubtitlesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateSubtitleDetails(newVal);
        });
    }


    // ========== updateSubtitleDetails() ==========

    private void updateSubtitleDetails(SubtitleItem subtitle) {
        if (subtitleDetailsLabel == null) {
            return;
        }
        
        if (subtitle == null) {
            subtitleDetailsLabel.setText("Select a subtitle to view details");
            return;
        }
        
        // Build detailed info display
        StringBuilder details = new StringBuilder();
        
        // Provider info
        details.append("🌐 Provider: ").append(subtitle.getProvider()).append("\n");
        details.append("   ").append(getProviderDescription(subtitle.getProvider())).append("\n\n");
        
        // Language
        details.append("🗣️ Language: ").append(subtitle.getLanguage().toUpperCase());
        details.append(" (").append(getLanguageName(subtitle.getLanguage())).append(")\n\n");
        
        // Quality score
        details.append("⭐ Quality Score: ").append(String.format("%.1f", subtitle.getScore())).append("/100\n");
        details.append("   ").append(getScoreDescription(subtitle.getScore())).append("\n\n");
        
        // Format
        details.append("📄 Format: ").append(subtitle.getFormat().toUpperCase()).append("\n");
        details.append("   ").append(getFormatDescription(subtitle.getFormat())).append("\n\n");
        
        // File ID (for debugging/support)
        if (subtitle.getFileId() != null && !subtitle.getFileId().isEmpty()) {
            details.append("🔑 File ID: ").append(subtitle.getFileId()).append("\n\n");
        }
        
        // Download info
        if (subtitle.getDownloadUrl() != null && !subtitle.getDownloadUrl().isEmpty()) {
            details.append("🔗 Download URL:\n");
            details.append("   ").append(truncateUrl(subtitle.getDownloadUrl())).append("\n\n");
        }
        
        // Manual download status
        if (subtitle.isManualDownloadOnly()) {
            details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            details.append("⚠️ MANUAL DOWNLOAD REQUIRED\n");
            details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            details.append("This subtitle requires manual download:\n");
            details.append("1. Visit the URL above\n");
            details.append("2. Find and download the subtitle\n");
            details.append("3. Use 'External File' option below\n");
            details.append("4. Select the downloaded file\n\n");
            details.append("⚠️ Cannot use automatic download for this subtitle\n\n");
        }
        
        // Provider-specific notes
        String providerNote = getProviderNote(subtitle.getProvider());
        if (providerNote != null && !subtitle.isManualDownloadOnly()) {
            details.append("💡 Note: ").append(providerNote).append("\n");
        }
        
        subtitleDetailsLabel.setText(details.toString());
    }


    // ========== getProviderDescription() ==========

    private String getProviderDescription(String provider) {
        switch (provider) {
            case "OpenSubtitles.com":
                return "Official API with high-quality subtitles";
            case "Subscene":
                return "One of the most popular subtitle sites - movies and TV";
            case "Addic7ed":
                return "Excellent for TV shows, especially recent episodes";
            case "Jimaku":
                return "Modern anime subtitle search (English & Japanese)";
            case "SubDL":
                return "Large database for movies and TV shows";
            case "Podnapisi":
                return "Multi-language subtitle database";
            case "AnimeSubtitles":
                return "Multi-language anime subtitle database";
            case "SubDivX":
                return "Largest Spanish subtitle database";
            case "YIFY":
                return "Movie subtitles (matches YIFY releases)";
            case "Subf2m":
                return "Popular subtitle site for movies and TV";
            default:
                return "Community-contributed subtitles";
        }
    }


    // ========== getLanguageName() ==========

    private String getLanguageName(String code) {
        switch (code.toLowerCase()) {
            case "eng": case "en": return "English";
            case "spa": case "es": return "Spanish";
            case "fre": case "fr": return "French";
            case "ger": case "de": return "German";
            case "ita": case "it": return "Italian";
            case "por": case "pt": return "Portuguese";
            case "rus": case "ru": return "Russian";
            case "jpn": case "ja": return "Japanese";
            case "chi": case "zh": return "Chinese";
            case "kor": case "ko": return "Korean";
            case "ara": case "ar": return "Arabic";
            default: return code.toUpperCase();
        }
    }


    // ========== getScoreDescription() ==========

    private String getScoreDescription(double score) {
        if (score >= 90) return "Excellent match - highly recommended";
        if (score >= 75) return "Good match - should work well";
        if (score >= 60) return "Fair match - may need adjustments";
        if (score >= 40) return "Poor match - timing may be off";
        return "Low match - not recommended";
    }


    // ========== getFormatDescription() ==========

    private String getFormatDescription(String format) {
        switch (format.toLowerCase()) {
            case "srt":
                return "SubRip - Most compatible, plain text format";
            case "ass":
                return "Advanced SubStation Alpha - Supports styling and effects";
            case "ssa":
                return "SubStation Alpha - Supports basic styling";
            case "vtt":
                return "WebVTT - Web-based subtitle format";
            default:
                return "Standard subtitle format";
        }
    }


    // ========== truncateUrl() ==========

    private String truncateUrl(String url) {
        if (url.length() > 60) {
            return url.substring(0, 57) + "...";
        }
        return url;
    }


    // ========== getProviderNote() ==========

    private String getProviderNote(String provider) {
        switch (provider) {
            case "Addic7ed":
                return "Auto-download with real scraping; may be blocked by anti-bot";
            case "Subscene":
                return "Auto-download via web scraping; very reliable";
            case "Jimaku":
            case "AnimeSubtitles":
                return "Auto-download via web scraping; anime specialist";
            case "OpenSubtitles.com":
                return "Requires API key configured in Settings";
            case "SubDL":
            case "Podnapisi":
                return "Automatic download via API";
            default:
                return null;
        }
    }


    // ========== handleStartSubtitles() ==========

    private void handleStartSubtitles() {
        isProcessing = true;
        startButton.setDisable(true);
        statusLabel.setText("Generating/downloading subtitles...");
        
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
            job.setStatus("⏳ Queued");
        }
        
        log("Starting subtitle processing for " + filePaths.size() + " file(s)");
        
        // Start subtitle processing in background
        new Thread(() -> {
            try {
                for (String filePath : filePaths) {
                    Platform.runLater(() -> log("Generating subtitles for: " + new File(filePath).getName()));
                    
                    // Try to generate subtitles with Whisper first
                    if (settings.isEnableWhisper()) {
                        JsonObject whisperRequest = new JsonObject();
                        whisperRequest.addProperty("action", "generate_subtitles");
                        whisperRequest.addProperty("video_path", filePath);
                        whisperRequest.addProperty("language", settings.getWhisperLanguages());
                        
                        try {
                            JsonObject response = pythonBridge.sendCommand(whisperRequest);
                            if (response.get("status").getAsString().equals("success")) {
                                log("Generated subtitles for: " + new File(filePath).getName());
                                Platform.runLater(() -> {
                                    queuedFiles.stream()
                                        .filter(job -> job.getInputPath().equals(filePath))
                                        .findFirst()
                                        .ifPresent(job -> job.setStatus("✅ Completed"));
                                });
                            } else {
                                log("Failed to generate subtitles: " + response.get("message").getAsString());
                            }
                        } catch (Exception e) {
                            log("Error generating subtitles: " + e.getMessage());
                        }
                    }
                    
                    // Try to download subtitles
                    if (settings.isDownloadSubtitles()) {
                        JsonObject downloadRequest = new JsonObject();
                        downloadRequest.addProperty("action", "download_subtitles");
                        downloadRequest.addProperty("video_path", filePath);
                        
                        try {
                            JsonObject response = pythonBridge.sendCommand(downloadRequest);
                            if (response.get("status").getAsString().equals("success")) {
                                log("Downloaded subtitles for: " + new File(filePath).getName());
                                Platform.runLater(() -> {
                                    queuedFiles.stream()
                                        .filter(job -> job.getInputPath().equals(filePath))
                                        .findFirst()
                                        .ifPresent(job -> job.setStatus("✅ Completed"));
                                });
                            } else {
                                log("No subtitles found for: " + new File(filePath).getName());
                            }
                        } catch (Exception e) {
                            log("Error downloading subtitles: " + e.getMessage());
                        }
                    }
                }
                
                Platform.runLater(() -> {
                    log("Subtitle processing completed");
                    resetProcessingState();
                    statusLabel.setText("Subtitles complete!");
                    showInfo("Subtitle Processing", "Subtitle processing completed for all files.");
                });
                
            } catch (Exception e) {
                logger.error("Error during subtitle processing", e);
                Platform.runLater(() -> {
                    showError("Subtitle Error", "Failed to process subtitles: " + e.getMessage());
                    resetProcessingState();
                });
            }
        }).start();
    }


    // ========== handleProcessSubtitles() ==========

    @FXML
    private void handleProcessSubtitles() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        // Get the selected mode
        String mode = quickSubProviderCombo != null ? quickSubProviderCombo.getValue() : "Automatic (Download + AI if needed)";
        
        if (mode == null) {
            mode = "Automatic (Download + AI if needed)";
        }
        
        log("Processing subtitles in mode: " + mode);
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // First, search for available subtitles to show in the table
        if (mode.contains("Download Only") || mode.contains("Automatic") || mode.contains("Both")) {
            searchAndDisplaySubtitles();
        } else if (mode.contains("AI Generation Only")) {
            // Do AI generation directly
            log("🤖 AI Generation Mode: Using Whisper to transcribe audio");
            log("⚠️  Note: AI generation is slower but works when subtitles aren't available");
            handleGenerateAI();
        }
    }


    // ========== handleAdvancedSearch() ==========

    @FXML
    private void handleAdvancedSearch() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Starting advanced subtitle search with multiple query variations...");
        searchAndDisplaySubtitles(true);  // true = use advanced search
    }


    // ========== searchAndDisplaySubtitles() ==========

    private void searchAndDisplaySubtitles() {
        searchAndDisplaySubtitles(false);  // false = use regular search
    }
    
    private void searchAndDisplaySubtitles(boolean advancedSearch) {
        if (queuedFiles.isEmpty()) {
            return;
        }
        
        // Get selected languages
        List<String> selectedLanguages = getSelectedLanguages();
        
        if (selectedLanguages.isEmpty()) {
            showWarning("No Languages", "Please select at least one language.");
            return;
        }
        
        if (advancedSearch) {
            log("🔍 Advanced Search: Using multiple query variations for better results...");
            log("📡 Searching 9 subtitle providers (OpenSubtitles, Addic7ed, SubDL, Subf2m, YIFY, Podnapisi, SubDivX, Kitsunekko, Jimaku)");
        } else {
        log("Searching for available subtitles...");
            log("📡 Querying 9 subtitle providers...");
        }
        
        // Log OpenSubtitles limitations
        if (settings.getOpensubtitlesApiKey() != null && !settings.getOpensubtitlesApiKey().trim().isEmpty()) {
            log("✅ OpenSubtitles: API key configured (5 downloads/day, 60+ languages)");
        } else {
            log("ℹ️  OpenSubtitles: No API key - search works, but downloads disabled");
            log("   Get free API key for downloads: https://www.opensubtitles.com/en/consumers");
        }
        
        // Log provider information
        log("ℹ️  Addic7ed: Movies, TV, Anime (all content types supported)");
        log("ℹ️  Kitsunekko: Anime subtitles (English & Japanese)");
        log("ℹ️  Jimaku: Anime only (multilingual support)");
        
        if (subtitleStatsLabel != null) {
            Platform.runLater(() -> subtitleStatsLabel.setText("Searching..."));
        }
        
        // Clear existing results
        if (availableSubtitlesTable != null) {
            Platform.runLater(() -> availableSubtitlesTable.getItems().clear());
        }
        
        // Process files sequentially (Python bridge can only handle one at a time)
        new Thread(() -> {
            try {
                // Update settings once
                JsonObject updateSettings = new JsonObject();
                updateSettings.addProperty("action", "update_settings");
                updateSettings.add("settings", settings.toJson());
                pythonBridge.sendCommand(updateSettings);
                
                // Process each file sequentially
                for (ConversionJob job : queuedFiles) {
                    final String filePath = job.getInputPath();
                    final String fileName = new File(filePath).getName();
                
                // Mark file as searching
                subtitleSearchStatus.put(fileName, SubtitleSearchStatus.SEARCHING);
                Platform.runLater(() -> {
                    updateSubtitleFileList();  // Refresh dropdown with status
                    if (advancedSearch) {
                        log("━━━ Advanced search for: " + fileName + " ━━━");
                    } else {
                        log("━━━ Searching: " + fileName + " ━━━");
                    }
                });
            
                // Create search request with streaming support
                JsonObject request = new JsonObject();
                
                // Use advanced_search action if advanced search is enabled
                if (advancedSearch) {
                    request.addProperty("action", "advanced_search_subtitles");
                    
                    // Check if AniList URL is provided
                    if (anilistUrlField != null && anilistUrlField.getText() != null && !anilistUrlField.getText().trim().isEmpty()) {
                        final String anilistUrl = anilistUrlField.getText().trim();
                        request.addProperty("anilist_url", anilistUrl);
                        Platform.runLater(() -> log("Using AniList URL: " + anilistUrl));
                    }
                } else {
                    request.addProperty("action", "search_subtitles");
                }
                
                request.addProperty("video_path", filePath);
                request.addProperty("streaming", true);  // Enable streaming mode
                
                // Add languages as JSON array
                JsonArray langsArray = new JsonArray();
                for (String lang : selectedLanguages) {
                    langsArray.add(lang);
                }
                request.add("languages", langsArray);
                
                // Initialize subtitle list for this file
                if (!subtitlesByFile.containsKey(fileName)) {
                    subtitlesByFile.put(fileName, FXCollections.observableArrayList());
                }
                final ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.get(fileName);
                
                // Track results for deduplication
                final Set<String> seenFileIds = new HashSet<>();
                final java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(0);
                
                // Use streaming command with progress callback
                try {
                    pythonBridge.sendStreamingCommand(request, response -> {
                        Platform.runLater(() -> {
                            try {
                                // Check for progress updates
                                if (response.has("progress")) {
                                    String provider = response.has("provider") ? response.get("provider").getAsString() : "Unknown";
                                    boolean isComplete = response.has("complete") && response.get("complete").getAsBoolean();
                                    
                                    if (isComplete) {
                                        log("✅ " + provider + " - search complete");
                                    } else {
                                        log("🔍 Searching " + provider + "...");
                                        if (subtitleStatsLabel != null) {
                                            subtitleStatsLabel.setText("Searching " + provider + " for " + fileName);
                                        }
                                    }
                                    return;
                                }
                                
                                // Handle final results or incremental updates
                                if (response.has("status")) {
                                    String status = response.get("status").getAsString();
                                    
                                    if ("success".equals(status) && response.has("subtitles")) {
                                        JsonArray subtitles = response.getAsJsonArray("subtitles");
                                        int newCount = 0;
                                        
                                        // Add new results to this file's list
                                        for (int i = 0; i < subtitles.size(); i++) {
                                            JsonObject sub = subtitles.get(i).getAsJsonObject();
                                            String fileId = sub.has("file_id") ? sub.get("file_id").getAsString() : "";
                                            
                                            // Skip duplicates
                                            if (!fileId.isEmpty() && seenFileIds.contains(fileId)) {
                                                continue;
                                            }
                                            seenFileIds.add(fileId);
                                            
                                            String language = sub.has("language") ? sub.get("language").getAsString() : "unknown";
                                            String provider = sub.has("provider") ? sub.get("provider").getAsString() : "unknown";
                                            double score = sub.has("score") ? sub.get("score").getAsDouble() : 0.0;
                                            String format = sub.has("format") ? sub.get("format").getAsString() : "srt";
                                            String downloadUrl = sub.has("download_url") ? sub.get("download_url").getAsString() : "";
                                            boolean manualOnly = sub.has("manual_download_only") && sub.get("manual_download_only").getAsBoolean();
                                            
                                            SubtitleItem item = new SubtitleItem(false, language, provider, score, format, fileId, downloadUrl, manualOnly);
                                            fileSubtitles.add(item);
                                            newCount++;
                                        }
                                        
                                        totalCount.addAndGet(newCount);
                                        
                                        // Update display if this is the currently selected file
                                        // Need to extract base filename from currentlySelectedFile since it may have status tags
                                        String baseCurrentFile = currentlySelectedFile != null ? extractBaseFileName(currentlySelectedFile) : null;
                                        if (fileName.equals(baseCurrentFile) && availableSubtitlesTable != null) {
                                            availableSubtitlesTable.setItems(fileSubtitles);
                                            int uniqueLangs = fileSubtitles.stream()
                                                .map(SubtitleItem::getLanguage)
                                                .collect(java.util.stream.Collectors.toSet())
                                                .size();
                                            subtitleStatsLabel.setText(totalCount.get() + " available | " + uniqueLangs + " language(s)");
                                        }
                                        
                                        // Check if this is the final result
                                        boolean isFinal = response.has("complete") && response.get("complete").getAsBoolean();
                                        if (isFinal) {
                                            int count = totalCount.get();
                                            log("Found " + count + " total subtitle(s)");
                                            if (count > 0) {
                                                log("✅ Search complete! Results displayed in table.");
                                            } else {
                                                log("⚠️ No subtitles found. Try AI generation instead.");
                                            }
                                        }
                                    } else if ("error".equals(status)) {
                                        String errorMsg = response.has("message") ? response.get("message").getAsString() : "Search failed";
                                        log("❌ Search failed: " + errorMsg);
                                        if (subtitleStatsLabel != null) {
                                            subtitleStatsLabel.setText("Search failed");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("Error processing streaming response", e);
                                log("⚠️ Error processing results: " + e.getMessage());
                            }
                        });
                    });
                    
                    // If streaming fails, fall back to regular search
                } catch (Exception streamErr) {
                    logger.warn("Streaming search not supported, falling back to regular search", streamErr);
                    
                    // Fallback to regular non-streaming search
                    JsonObject response = pythonBridge.sendCommand(request);
                    
                    logger.info("Search response: {}", response.toString());
                    
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        int count = response.has("count") ? response.get("count").getAsInt() : 0;
                        
                        Platform.runLater(() -> {
                            log("Found " + count + " subtitle(s)");
                            
                            if (count > 0 && response.has("subtitles") && response.get("subtitles").isJsonArray()) {
                                JsonArray subtitles = response.getAsJsonArray("subtitles");
                                
                                Set<String> languages = new HashSet<>();
                                for (int i = 0; i < subtitles.size(); i++) {
                                    JsonObject sub = subtitles.get(i).getAsJsonObject();
                                    String language = sub.has("language") ? sub.get("language").getAsString() : "unknown";
                                    String provider = sub.has("provider") ? sub.get("provider").getAsString() : "unknown";
                                    double score = sub.has("score") ? sub.get("score").getAsDouble() : 0.0;
                                    String format = sub.has("format") ? sub.get("format").getAsString() : "srt";
                                    String fileId = sub.has("file_id") ? sub.get("file_id").getAsString() : "";
                                    String downloadUrl = sub.has("download_url") ? sub.get("download_url").getAsString() : "";
                                    boolean manualOnly = sub.has("manual_download_only") && sub.get("manual_download_only").getAsBoolean();
                                    
                                    languages.add(language);
                                    
                                    SubtitleItem item = new SubtitleItem(false, language, provider, score, format, fileId, downloadUrl, manualOnly);
                                    fileSubtitles.add(item);
                                }
                                
                                // Update display if this is the currently selected file
                                if (fileName.equals(currentlySelectedFile) && availableSubtitlesTable != null) {
                                    availableSubtitlesTable.setItems(fileSubtitles);
                                    if (subtitleStatsLabel != null) {
                                        subtitleStatsLabel.setText(count + " available | " + languages.size() + " language(s)");
                                    }
                                }
                                
                                log("✅ Results displayed in table. Select and download as needed.");
                            } else {
                                if (subtitleStatsLabel != null) {
                                    subtitleStatsLabel.setText("0 available | 0 languages");
                                }
                                log("⚠️ No subtitles found. Try AI generation instead.");
                            }
                        });
                    } else {
                        String errorMsg = response.has("message") ? response.get("message").getAsString() : "Search failed";
                        Platform.runLater(() -> {
                            log("❌ Search failed: " + errorMsg);
                            if (subtitleStatsLabel != null) {
                                subtitleStatsLabel.setText("Search failed");
                            }
                        });
                    }
                }
                
                // Mark file as completed
                subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                
                // Log completion for this file
                int subtitleCount = fileSubtitles != null ? fileSubtitles.size() : 0;
                
                // Debug: Verify the file was added to the map
                logger.debug("Stored {} subtitle(s) for file: {}", subtitleCount, fileName);
                logger.debug("Total files in subtitlesByFile map: {}", subtitlesByFile.size());
                
                Platform.runLater(() -> {
                    log("✅ Search complete for: " + fileName + " (" + subtitleCount + " result(s))");
                    // Update file selector with status
                    updateSubtitleFileList();
                    
                    // Select first file if none selected or update current selection display
                    if (currentlySelectedFile == null && !subtitleFileCombo.getItems().isEmpty()) {
                        String firstItem = subtitleFileCombo.getItems().get(0);
                        currentlySelectedFile = firstItem;
                        subtitleFileCombo.setValue(firstItem);
                        
                        // Get the base filename without status tags
                        String baseFileName = extractBaseFileName(firstItem);
                        
                        // Display subtitles for first file
                        if (availableSubtitlesTable != null && subtitlesByFile.containsKey(baseFileName)) {
                            availableSubtitlesTable.setItems(subtitlesByFile.get(baseFileName));
                            
                            // Update stats
                            int uniqueLangs = fileSubtitles.stream()
                                .map(SubtitleItem::getLanguage)
                                .collect(java.util.stream.Collectors.toSet())
                                .size();
                            if (subtitleStatsLabel != null) {
                                subtitleStatsLabel.setText(subtitleCount + " available | " + uniqueLangs + " language(s)");
                            }
                        }
                    } else if (currentlySelectedFile != null) {
                        // Update display if this is the currently selected file
                        String baseCurrentFile = extractBaseFileName(currentlySelectedFile);
                        if (baseCurrentFile.equals(fileName) && availableSubtitlesTable != null && fileSubtitles != null) {
                            availableSubtitlesTable.setItems(fileSubtitles);
                            int uniqueLangs = fileSubtitles.stream()
                                .map(SubtitleItem::getLanguage)
                                .collect(java.util.stream.Collectors.toSet())
                                .size();
                            if (subtitleStatsLabel != null) {
                                subtitleStatsLabel.setText(subtitleCount + " available | " + uniqueLangs + " language(s)");
                            }
                        }
                    }
                });
                } // End of for each file loop
                
                // All files processed - final summary
                int totalFilesWithResults = 0;
                int totalSubtitles = 0;
                StringBuilder filesWithResults = new StringBuilder();
                
                for (java.util.Map.Entry<String, ObservableList<SubtitleItem>> entry : subtitlesByFile.entrySet()) {
                    ObservableList<SubtitleItem> subs = entry.getValue();
                    if (subs != null && !subs.isEmpty()) {
                        totalFilesWithResults++;
                        totalSubtitles += subs.size();
                        filesWithResults.append("\n   • ").append(entry.getKey()).append(": ").append(subs.size()).append(" subtitle(s)");
                    }
                }
                
                logger.info("Search summary: {} files with results, {} total subtitles", totalFilesWithResults, totalSubtitles);
                logger.info("Files with results: {}", filesWithResults.toString());
                
                final int finalTotalFiles = totalFilesWithResults;
                final int finalTotalSubs = totalSubtitles;
                final String finalFilesList = filesWithResults.toString();
                
                Platform.runLater(() -> {
                    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log("🎉 Search complete! Found " + finalTotalSubs + " subtitle(s) across " + finalTotalFiles + " file(s)");
                    if (!finalFilesList.isEmpty()) {
                        log("Results breakdown:" + finalFilesList);
                    }
                    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
                
            } catch (Exception e) {
                logger.error("Error in subtitle search", e);
                Platform.runLater(() -> log("❌ Search error: " + e.getMessage()));
            }
        }, "SubtitleSearch").start();
    }


    // ========== handleAutoSelectBest() ==========

    @FXML
    private void handleAutoSelectBest() {
        if (subtitlesByFile.isEmpty()) {
            showWarning("No Subtitles", "No subtitles available to select. Please search first.");
            return;
        }
        
        List<String> requestedLanguages = getSelectedLanguages();
        if (requestedLanguages.isEmpty()) {
            showWarning("No Languages", "Please select at least one language first.");
            return;
        }
        
        log("Auto-selecting best subtitles for ALL files...");
        
        int totalSelected = 0;
        // Process each file
        for (String fileName : subtitlesByFile.keySet()) {
            ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.get(fileName);
            
            // Group subtitles by language for this file
            java.util.Map<String, List<SubtitleItem>> byLanguage = new java.util.HashMap<>();
            for (SubtitleItem item : fileSubtitles) {
                String lang = item.getLanguage();
                byLanguage.computeIfAbsent(lang, k -> new ArrayList<>()).add(item);
            }
            
            // For each requested language, select the best subtitle (highest score)
            int fileSelectedCount = 0;
            for (String lang : requestedLanguages) {
                List<SubtitleItem> langSubtitles = byLanguage.get(lang);
                if (langSubtitles != null && !langSubtitles.isEmpty()) {
                    // Sort by score descending
                    langSubtitles.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                    SubtitleItem best = langSubtitles.get(0);
                    best.setSelected(true);
                    fileSelectedCount++;
                    totalSelected++;
                }
            }
            
            if (fileSelectedCount > 0) {
                log("  ✅ " + fileName + ": " + fileSelectedCount + " subtitle(s) selected");
            }
        }
        
        // Refresh table to show checkboxes for currently displayed file
        if (availableSubtitlesTable != null) {
            availableSubtitlesTable.refresh();
        }
        
        if (totalSelected > 0) {
            log("✅ Auto-selected " + totalSelected + " best subtitle(s) across " + subtitlesByFile.size() + " file(s)");
        } else {
            log("⚠️ No matching subtitles found for selected languages");
        }
    }


    // ========== getSelectedLanguages() ==========

    private List<String> getSelectedLanguages() {
        List<String> selectedLanguages = new ArrayList<>();
        
        // European Languages (with dialect support)
        if (langEnglishCheck != null && langEnglishCheck.isSelected()) {
            selectedLanguages.add("eng");  // ISO 639-2 for English
            selectedLanguages.add("en");   // Whisper short code
        }
        if (langSpanishCheck != null && langSpanishCheck.isSelected()) {
            selectedLanguages.add("spa");  // Spanish (European)
            selectedLanguages.add("es");   // Whisper short code
        }
        if (langSpanishLACheck != null && langSpanishLACheck.isSelected()) {
            selectedLanguages.add("spa");  // Latin American Spanish (uses same code)
            selectedLanguages.add("es-MX"); // Mexican Spanish variant
        }
        if (langFrenchCheck != null && langFrenchCheck.isSelected()) {
            selectedLanguages.add("fre");
            selectedLanguages.add("fr");
        }
        if (langGermanCheck != null && langGermanCheck.isSelected()) {
            selectedLanguages.add("ger");
            selectedLanguages.add("de");
        }
        if (langItalianCheck != null && langItalianCheck.isSelected()) {
            selectedLanguages.add("ita");
            selectedLanguages.add("it");
        }
        if (langPortugueseCheck != null && langPortugueseCheck.isSelected()) {
            selectedLanguages.add("por");  // Portuguese (European)
            selectedLanguages.add("pt");
        }
        if (langPortugueseBRCheck != null && langPortugueseBRCheck.isSelected()) {
            selectedLanguages.add("pob");  // Portuguese (Brazilian) - subtitle providers
            selectedLanguages.add("pt-BR"); // Whisper variant
        }
        
        // Asian Languages
        if (langJapaneseCheck != null && langJapaneseCheck.isSelected()) {
            selectedLanguages.add("jpn");
            selectedLanguages.add("ja");
        }
        if (langChineseCheck != null && langChineseCheck.isSelected()) {
            selectedLanguages.add("chi");  // Chinese (Simplified)
            selectedLanguages.add("zh");   // Whisper code
            selectedLanguages.add("zh-CN"); // Mainland Chinese
        }
        if (langChineseTWCheck != null && langChineseTWCheck.isSelected()) {
            selectedLanguages.add("zht");  // Traditional Chinese
            selectedLanguages.add("zh-TW"); // Taiwan Chinese
        }
        if (langKoreanCheck != null && langKoreanCheck.isSelected()) {
            selectedLanguages.add("kor");
            selectedLanguages.add("ko");
        }
        if (langHindiCheck != null && langHindiCheck.isSelected()) {
            selectedLanguages.add("hin");
            selectedLanguages.add("hi");
        }
        if (langThaiCheck != null && langThaiCheck.isSelected()) {
            selectedLanguages.add("tha");
            selectedLanguages.add("th");
        }
        if (langVietnameseCheck != null && langVietnameseCheck.isSelected()) {
            selectedLanguages.add("vie");
            selectedLanguages.add("vi");
        }
        
        // Other Major Languages
        if (langRussianCheck != null && langRussianCheck.isSelected()) {
            selectedLanguages.add("rus");
            selectedLanguages.add("ru");
        }
        if (langArabicCheck != null && langArabicCheck.isSelected()) {
            selectedLanguages.add("ara");
            selectedLanguages.add("ar");
        }
        if (langTurkishCheck != null && langTurkishCheck.isSelected()) {
            selectedLanguages.add("tur");
            selectedLanguages.add("tr");
        }
        if (langPolishCheck != null && langPolishCheck.isSelected()) {
            selectedLanguages.add("pol");
            selectedLanguages.add("pl");
        }
        if (langDutchCheck != null && langDutchCheck.isSelected()) {
            selectedLanguages.add("dut");
            selectedLanguages.add("nl");
        }
        if (langSwedishCheck != null && langSwedishCheck.isSelected()) {
            selectedLanguages.add("swe");
            selectedLanguages.add("sv");
        }
        if (langNorwegianCheck != null && langNorwegianCheck.isSelected()) {
            selectedLanguages.add("nor");
            selectedLanguages.add("no");
        }
        
        // Remove duplicates while preserving order
        return new ArrayList<>(new java.util.LinkedHashSet<>(selectedLanguages));
    }


    // ========== handleGenerateAI() ==========

    private void handleGenerateAI() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        // Check if Whisper is available
        try {
            JsonObject whisperStatus = pythonBridge.checkWhisper();
            if (!whisperStatus.has("whisper_available") || !whisperStatus.get("whisper_available").getAsBoolean()) {
                showWarning("Whisper Not Available", 
                    "Whisper AI is not installed. Please install it from Settings > Subtitles.");
                return;
            }
            
            // Check if model is installed
            if (!whisperStatus.has("installed_models") || whisperStatus.get("installed_models").getAsJsonArray().size() == 0) {
                showWarning("Whisper Model Not Downloaded", 
                    "No Whisper models are installed. Please download a model from Settings > Subtitles.\n\n" +
                    "Recommended: 'base' model for good balance of speed and quality.");
                return;
            }
            
        } catch (Exception e) {
            logger.error("Error checking Whisper status", e);
            showError("Error", "Failed to check Whisper status: " + e.getMessage());
            return;
        }
        
        log("Generating AI subtitles with Whisper...");
        log("Using model: " + settings.getWhisperModel() + ", language: " + settings.getWhisperLanguages());
        
        // Process each queued file
        new Thread(() -> {
            int successCount = 0;
            int failedCount = 0;
            
            for (ConversionJob job : queuedFiles) {
                String filePath = job.getInputPath();
                String fileName = new File(filePath).getName();
                
                Platform.runLater(() -> log("Generating subtitles for: " + fileName));
                
                try {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", "generate_subtitles");
                    request.addProperty("video_path", filePath);
                    
                    // Use language from settings, or null for auto-detect
                    String language = settings.getWhisperLanguages();
                    if (language != null && !language.trim().isEmpty() && !"auto".equalsIgnoreCase(language)) {
                        // Extract first language if multiple are specified
                        String[] langs = language.split(",");
                        request.addProperty("language", langs[0].trim());
                    }
                    
                    // Update settings in Python backend to ensure correct model is used
                    JsonObject updateSettings = new JsonObject();
                    updateSettings.addProperty("action", "update_settings");
                    updateSettings.add("settings", settings.toJson());
                    pythonBridge.sendCommand(updateSettings);
                    
                    // Generate subtitles
                    JsonObject response = pythonBridge.sendCommand(request);
                    
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        String subtitlePath = response.has("subtitle_path") ? 
                            response.get("subtitle_path").getAsString() : "unknown";
                        String message = response.has("message") ? 
                            response.get("message").getAsString() : "Subtitles generated successfully";
                        
                        Platform.runLater(() -> {
                            log("✅ Generated: " + new File(subtitlePath).getName() + " - " + message);
                        });
                        successCount++;
                    } else {
                        String errorMsg = response.has("message") ? 
                            response.get("message").getAsString() : "Unknown error";
                        Platform.runLater(() -> {
                            log("❌ Failed: " + fileName + " - " + errorMsg);
                        });
                        failedCount++;
                    }
                    
                } catch (TimeoutException e) {
                    logger.error("Timeout generating subtitles for: " + fileName, e);
                    Platform.runLater(() -> {
                        log("❌ Timeout: " + fileName + " - Generation took too long. Try a smaller model.");
                    });
                    failedCount++;
                } catch (Exception e) {
                    logger.error("Error generating subtitles for: " + fileName, e);
                    
                    String errorMsg = e.getMessage();
                    if (errorMsg != null) {
                        if (errorMsg.contains("model") && errorMsg.contains("not")) {
                            Platform.runLater(() -> {
                                log("❌ Model Error: " + fileName + " - Whisper model not found. Download it from Settings.");
                                showError("Whisper Model Missing", 
                                    "The selected Whisper model is not installed.\n\n" +
                                    "Please go to Settings > Subtitles and download a model first.");
                            });
                        } else if (errorMsg.contains("memory") || errorMsg.contains("OOM")) {
                            Platform.runLater(() -> {
                                log("❌ Memory Error: " + fileName + " - Out of memory. Try a smaller model.");
                                showError("Out of Memory", 
                                    "Not enough memory to run this Whisper model.\n\n" +
                                    "Try using a smaller model like 'tiny' or 'base'.");
                            });
                        } else {
                            Platform.runLater(() -> {
                                log("❌ Error: " + fileName + " - " + errorMsg);
                            });
                        }
                    } else {
                        Platform.runLater(() -> {
                            log("❌ Error: " + fileName + " - Unknown error occurred");
                        });
                    }
                    failedCount++;
                }
            }
            
            // Show completion message
            final int finalSuccess = successCount;
            final int finalFailed = failedCount;
            Platform.runLater(() -> {
                log("AI subtitle generation complete: " + finalSuccess + " success, " + finalFailed + " failed");
                showInfo("AI Generation Complete", 
                    String.format("Generated subtitles for %d file(s).\n%d succeeded, %d failed.", 
                        finalSuccess + finalFailed, finalSuccess, finalFailed));
            });
            
        }, "SubtitleGeneration").start();
    }


    // ========== handleDownloadSubtitles() ==========

    private void handleDownloadSubtitles() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        // Get selected languages
        List<String> selectedLanguages = getSelectedLanguages();
        
        if (selectedLanguages.isEmpty()) {
            showWarning("No Languages", "Please select at least one language.");
            return;
        }
        
        log("Downloading subtitles for " + queuedFiles.size() + " file(s) in " + selectedLanguages.size() + " language(s)...");
        log("Languages: " + String.join(", ", selectedLanguages));
        
        // Process each queued file
        new Thread(() -> {
            int successCount = 0;
            int failedCount = 0;
            
            for (ConversionJob job : queuedFiles) {
                String filePath = job.getInputPath();
                String fileName = new File(filePath).getName();
                
                Platform.runLater(() -> log("Downloading subtitles for: " + fileName));
                
                try {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", "download_subtitles");
                    request.addProperty("video_path", filePath);
                    
                    // Add languages as JSON array
                    JsonArray langsArray = new JsonArray();
                    for (String lang : selectedLanguages) {
                        langsArray.add(lang);
                    }
                    request.add("languages", langsArray);
                    
                    // Update settings in Python backend
                    JsonObject updateSettings = new JsonObject();
                    updateSettings.addProperty("action", "update_settings");
                    updateSettings.add("settings", settings.toJson());
                    pythonBridge.sendCommand(updateSettings);
                    
                    // Download subtitles
                    JsonObject response = pythonBridge.sendCommand(request);
                    
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        String message = response.has("message") ? 
                            response.get("message").getAsString() : "Subtitles downloaded successfully";
                        
                        Platform.runLater(() -> {
                            log("✅ Downloaded: " + fileName + " - " + message);
                        });
                        successCount++;
                    } else {
                        String errorMsg = response.has("message") ? 
                            response.get("message").getAsString() : "Unknown error";
                        
                        // Check if it's an authentication error
                        if (errorMsg.toLowerCase().contains("unauthorized") || 
                            errorMsg.toLowerCase().contains("invalid api key") ||
                            errorMsg.toLowerCase().contains("authentication") ||
                            errorMsg.toLowerCase().contains("401")) {
                            
                            // Invalidate OpenSubtitles credentials
                            settings.setOpensubtitlesValidated(false);
                            settings.save();
                            
                            Platform.runLater(() -> {
                                log("❌ Authentication Error: " + fileName + " - " + errorMsg);
                                log("⚠️ OpenSubtitles validation invalidated. Please re-validate in Settings.");
                                showError("Authentication Failed", 
                                    "OpenSubtitles API authentication failed.\n\n" +
                                    "Error: " + errorMsg + "\n\n" +
                                    "Please go to Settings > Subtitles and re-validate your API key.");
                            });
                        } else {
                            Platform.runLater(() -> {
                                log("❌ Failed: " + fileName + " - " + errorMsg);
                            });
                        }
                        failedCount++;
                    }
                    
                } catch (TimeoutException e) {
                    logger.error("Timeout downloading subtitles for: " + fileName, e);
                    Platform.runLater(() -> {
                        log("❌ Timeout: " + fileName + " - Operation timed out. Check network connection.");
                    });
                    failedCount++;
                } catch (Exception e) {
                    logger.error("Error downloading subtitles for: " + fileName, e);
                    
                    // Check if it's a connection/network error
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("Connection") || errorMsg.contains("Network"))) {
                        Platform.runLater(() -> {
                            log("❌ Network Error: " + fileName + " - Check your internet connection");
                        });
                    } else {
                        Platform.runLater(() -> {
                            log("❌ Error: " + fileName + " - " + (errorMsg != null ? errorMsg : "Unknown error"));
                        });
                    }
                    failedCount++;
                }
            }
            
            // Show completion message
            final int finalSuccess = successCount;
            final int finalFailed = failedCount;
            Platform.runLater(() -> {
                log("Subtitle download complete: " + finalSuccess + " success, " + finalFailed + " failed");
            });
            
        }, "SubtitleDownload").start();
    }


    // ========== handleApplySubtitles() ==========

    private void handleApplySubtitles() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        if (availableSubtitlesTable == null || availableSubtitlesTable.getItems().isEmpty()) {
            showWarning("No Subtitles", "Please search for subtitles first using the 'Process Files' button.");
            return;
        }
        
        // Get currently selected file from dropdown
        String selectedDisplayName = subtitleFileCombo != null ? subtitleFileCombo.getValue() : null;
        if (selectedDisplayName == null || selectedDisplayName.isEmpty()) {
            showWarning("No File Selected", "Please select a file from the dropdown.");
            return;
        }
        
        // Extract base filename without status tags
        String selectedFileName = extractBaseFileName(selectedDisplayName);
        
        // Find the corresponding video path
        String videoPath = null;
        for (ConversionJob job : queuedFiles) {
            String fileName = new java.io.File(job.getInputPath()).getName();
            if (fileName.equals(selectedFileName)) {
                videoPath = job.getInputPath();
                break;
            }
        }
        
        if (videoPath == null) {
            showWarning("File Not Found", "Could not find the selected file in queue.");
            return;
        }
        
        // Get selected subtitles from table
        List<SubtitleItem> selectedSubtitles = new ArrayList<>();
        for (SubtitleItem item : availableSubtitlesTable.getItems()) {
            if (item.isSelected()) {
                selectedSubtitles.add(item);
            }
        }
        
        if (selectedSubtitles.isEmpty()) {
            showWarning("No Selection", "Please select at least one subtitle to apply.");
            return;
        }
        
        // Get selected output mode
        String selectedMode = subtitleOutputModeCombo != null ? subtitleOutputModeCombo.getValue() : "External File (No Processing)";
        String mode = "external"; // default
        
        if (selectedMode.contains("Embed")) {
            mode = "embed";
        } else if (selectedMode.contains("Burn-in")) {
            mode = "burn-in";
        }
        
        log("Applying " + selectedSubtitles.size() + " subtitle(s) to " + selectedFileName + " in mode: " + mode);
        
        final String finalVideoPath = videoPath;
        
        final String finalMode = mode;
        
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (SubtitleItem subtitle : selectedSubtitles) {
                try {
                    Platform.runLater(() -> log("Processing " + subtitle.getLanguage() + " subtitle from " + subtitle.getProvider() + "..."));
                    
                    // Check if manual download only
                    if (subtitle.isManualDownloadOnly()) {
                        Platform.runLater(() -> {
                            log("⚠️ Manual download required for " + subtitle.getProvider());
                            log("   Please download manually from: " + subtitle.getDownloadUrl());
                            log("   Then use 'External File' option to apply it");
                            showWarning("Manual Download Required",
                                "The subtitle from " + subtitle.getProvider() + " requires manual download.\n\n" +
                                "Steps:\n" +
                                "1. Visit: " + subtitle.getDownloadUrl() + "\n" +
                                "2. Download the subtitle file\n" +
                                "3. Use 'External File' option in the Subtitles tab\n" +
                                "4. Browse and select the downloaded file\n\n" +
                                "We couldn't get a direct download link for this subtitle.");
                        });
                        failCount++;
                        continue;
                    }
                    
                    // Step 1: Download the subtitle
                    Platform.runLater(() -> log("Downloading subtitle from " + subtitle.getProvider() + "..."));
                    
                    JsonObject downloadRequest = new JsonObject();
                    downloadRequest.addProperty("action", "download_subtitle");
                    downloadRequest.addProperty("file_id", subtitle.getFileId());
                    downloadRequest.addProperty("provider", subtitle.getProvider());
                    downloadRequest.addProperty("video_path", finalVideoPath);
                    downloadRequest.addProperty("language", subtitle.getLanguage());
                    downloadRequest.addProperty("download_url", subtitle.getDownloadUrl());
                    
                    JsonObject downloadResponse = pythonBridge.sendCommand(downloadRequest);
                    
                    // Check if download succeeded
                    if (!downloadResponse.has("status") || !"success".equals(downloadResponse.get("status").getAsString())) {
                        String errorMsg = downloadResponse.has("message") ? downloadResponse.get("message").getAsString() : "Download failed";
                        boolean requiresManual = downloadResponse.has("requires_manual_download") && 
                                                downloadResponse.get("requires_manual_download").getAsBoolean();
                        
                        // Check if message contains manual download instructions
                        boolean hasManualInstructions = errorMsg.toLowerCase().contains("manual") || 
                                                       errorMsg.toLowerCase().contains("visit:") ||
                                                       errorMsg.toLowerCase().contains("please:");
                        
                        if (requiresManual || hasManualInstructions) {
                            Platform.runLater(() -> {
                                log("⚠️ Manual download required:");
                                // Log the full message with line breaks
                                for (String line : errorMsg.split("\n")) {
                                    log("   " + line);
                                }
                                showWarning("Manual Download Required - " + subtitle.getProvider(), errorMsg);
                            });
                        } else {
                            Platform.runLater(() -> {
                                log("❌ Download failed: " + subtitle.getProvider());
                                // Show detailed error in log
                                for (String line : errorMsg.split("\n")) {
                                    log("   " + line);
                                }
                                showError("Download Failed", 
                                    "Failed to download from " + subtitle.getProvider() + ":\n\n" + errorMsg);
                            });
                        }
                        failCount++;
                        continue;
                    }
                    
                    // Get the downloaded subtitle path - add null check
                    if (!downloadResponse.has("subtitle_path")) {
                        Platform.runLater(() -> {
                            log("❌ Download response missing subtitle_path");
                            log("   Response: " + downloadResponse.toString());
                        });
                        failCount++;
                        continue;
                    }
                    
                    String subtitlePath = downloadResponse.get("subtitle_path").getAsString();
                    Platform.runLater(() -> log("✅ Downloaded to: " + subtitlePath));
                    
                    // Step 2: Apply the subtitle
                    Platform.runLater(() -> log("Applying subtitle in '" + finalMode + "' mode..."));
                    
                    JsonObject applyRequest = new JsonObject();
                    applyRequest.addProperty("action", "apply_subtitles");
                    applyRequest.addProperty("video_path", finalVideoPath);
                    applyRequest.addProperty("subtitle_path", subtitlePath);
                    applyRequest.addProperty("mode", finalMode);
                    applyRequest.addProperty("language", subtitle.getLanguage());
                    
                    JsonObject applyResponse = pythonBridge.sendCommand(applyRequest);
                    
                    if (applyResponse.has("status") && "success".equals(applyResponse.get("status").getAsString())) {
                        String outputPath = applyResponse.has("output_path") ? applyResponse.get("output_path").getAsString() : "unknown";
                        Platform.runLater(() -> log("✅ Success: " + outputPath));
                        successCount++;
                    } else {
                        String errorMsg = applyResponse.has("message") ? applyResponse.get("message").getAsString() : "Unknown error";
                        Platform.runLater(() -> log("❌ Failed: " + errorMsg));
                        failCount++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing subtitle", e);
                    Platform.runLater(() -> log("❌ Error: " + e.getMessage()));
                    failCount++;
                }
            }
            
            final int finalSuccess = successCount;
            final int finalFail = failCount;
            
            Platform.runLater(() -> {
                if (finalSuccess > 0) {
                    log("✅ Applied " + finalSuccess + " subtitle(s) successfully");
                    if (finalFail > 0) {
                        log("⚠️ " + finalFail + " subtitle(s) failed");
                    }
                    showInfo("Subtitles Applied", 
                        "Successfully applied " + finalSuccess + " subtitle(s)" + 
                        (finalFail > 0 ? "\n" + finalFail + " failed" : ""));
                } else {
                    log("❌ All subtitle applications failed");
                    showError("Subtitles Failed", "Failed to apply any subtitles. Check the log for details.");
                }
            });
            
        }, "SubtitleApply").start();


    // ========== handleBatchApplySubtitles() ==========

    @FXML
    private void handleBatchApplySubtitles() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        if (subtitlesByFile.isEmpty()) {
            showWarning("No Subtitles", "No subtitles have been found yet. Please search for subtitles first.");
            return;
        }
        
        // Get selected output mode
        String selectedMode = subtitleOutputModeCombo != null ? subtitleOutputModeCombo.getValue() : "External File (No Processing)";
        String mode = "external"; // default
        
        if (selectedMode.contains("Embed")) {
            mode = "embed";
        } else if (selectedMode.contains("Burn-in")) {
            mode = "burn-in";
        }
        
        // Count files with subtitles
        int filesWithSubtitles = 0;
        for (String fileName : subtitlesByFile.keySet()) {
            ObservableList<SubtitleItem> subs = subtitlesByFile.get(fileName);
            if (subs != null && !subs.isEmpty()) {
                // Count selected subtitles
                long selectedCount = subs.stream().filter(SubtitleItem::isSelected).count();
                if (selectedCount > 0) {
                    filesWithSubtitles++;
                }
            }
        }
        
        if (filesWithSubtitles == 0) {
            showWarning("No Subtitles Selected", 
                "No subtitles are selected for any files.\n\n" +
                "Please:\n" +
                "1. Use 'Auto-Select Best' button to automatically select best subtitles\n" +
                "2. OR manually select subtitles in the table for each file");
            return;
        }
        
        // Confirm with user
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Batch Apply Confirmation");
        confirmAlert.setHeaderText("Apply subtitles to " + filesWithSubtitles + " file(s)?");
        confirmAlert.setContentText(
            "This will process all files with selected subtitles.\n\n" +
            "Mode: " + selectedMode + "\n" +
            "Files to process: " + filesWithSubtitles + "\n\n" +
            "This may take some time depending on the mode selected.\n" +
            "Continue?"
        );
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        
        log("═══════════════════════════════════════════════════");
        log("🔄 Starting Batch Apply for " + filesWithSubtitles + " file(s)");
        log("Mode: " + selectedMode);
        log("═══════════════════════════════════════════════════");
        
        final String finalMode = mode;
        final int finalFilesWithSubtitles = filesWithSubtitles;
        
        new Thread(() -> {
            int totalSuccess = 0;
            int totalFailed = 0;
            int totalSkipped = 0;
            int filesProcessed = 0;
            
            // Process each file that has subtitles
            for (ConversionJob job : queuedFiles) {
                final String videoPath = job.getInputPath();
                final String fileName = new java.io.File(videoPath).getName();
                
                // Check if this file has subtitles
                if (!subtitlesByFile.containsKey(fileName)) {
                    continue;
                }
                
                ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.get(fileName);
                if (fileSubtitles == null || fileSubtitles.isEmpty()) {
                    continue;
                }
                
                // Get selected subtitles for this file
                List<SubtitleItem> selectedSubtitles = new ArrayList<>();
                for (SubtitleItem item : fileSubtitles) {
                    if (item.isSelected()) {
                        selectedSubtitles.add(item);
                    }
                }
                
                if (selectedSubtitles.isEmpty()) {
                    totalSkipped++;
                    continue;
                }
                
                filesProcessed++;
                final int currentFile = filesProcessed;
                final int totalFiles = finalFilesWithSubtitles;
                
                Platform.runLater(() -> {
                    log("");
                    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log("📁 Processing file " + currentFile + "/" + totalFiles + ": " + fileName);
                    log("   " + selectedSubtitles.size() + " subtitle(s) to apply");
                    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
                
                int fileSuccessCount = 0;
                int fileFailCount = 0;
                
                // Process each selected subtitle for this file
                for (SubtitleItem subtitle : selectedSubtitles) {
                    try {
                        Platform.runLater(() -> log("  ➤ Processing " + subtitle.getLanguage() + " subtitle from " + subtitle.getProvider() + "..."));
                        
                        // Check if manual download only
                        if (subtitle.isManualDownloadOnly()) {
                            Platform.runLater(() -> {
                                log("  ⚠️  Manual download required for " + subtitle.getProvider());
                                log("     Please download manually from: " + subtitle.getDownloadUrl());
                            });
                            fileFailCount++;
                            continue;
                        }
                        
                        // Step 1: Download the subtitle
                        Platform.runLater(() -> log("  ⬇️  Downloading from " + subtitle.getProvider() + "..."));
                        
                        JsonObject downloadRequest = new JsonObject();
                        downloadRequest.addProperty("action", "download_subtitle");
                        downloadRequest.addProperty("file_id", subtitle.getFileId());
                        downloadRequest.addProperty("provider", subtitle.getProvider());
                        downloadRequest.addProperty("video_path", videoPath);
                        downloadRequest.addProperty("language", subtitle.getLanguage());
                        downloadRequest.addProperty("download_url", subtitle.getDownloadUrl());
                        
                        JsonObject downloadResponse = pythonBridge.sendCommand(downloadRequest);
                        
                        // Check if download succeeded
                        if (!downloadResponse.has("status") || !"success".equals(downloadResponse.get("status").getAsString())) {
                            String errorMsg = downloadResponse.has("message") ? downloadResponse.get("message").getAsString() : "Download failed";
                            Platform.runLater(() -> log("  ❌ Download failed: " + errorMsg));
                            fileFailCount++;
                            continue;
                        }
                        
                        // Get the downloaded subtitle path
                        if (!downloadResponse.has("subtitle_path")) {
                            Platform.runLater(() -> log("  ❌ Download response missing subtitle_path"));
                            fileFailCount++;
                            continue;
                        }
                        
                        String subtitlePath = downloadResponse.get("subtitle_path").getAsString();
                        Platform.runLater(() -> log("  ✅ Downloaded to: " + subtitlePath));
                        
                        // Step 2: Apply the subtitle
                        Platform.runLater(() -> log("  🔧 Applying subtitle in '" + finalMode + "' mode..."));
                        
                        JsonObject applyRequest = new JsonObject();
                        applyRequest.addProperty("action", "apply_subtitles");
                        applyRequest.addProperty("video_path", videoPath);
                        applyRequest.addProperty("subtitle_path", subtitlePath);
                        applyRequest.addProperty("mode", finalMode);
                        applyRequest.addProperty("language", subtitle.getLanguage());
                        
                        JsonObject applyResponse = pythonBridge.sendCommand(applyRequest);
                        
                        if (applyResponse.has("status") && "success".equals(applyResponse.get("status").getAsString())) {
                            String outputPath = applyResponse.has("output_path") ? applyResponse.get("output_path").getAsString() : "unknown";
                            Platform.runLater(() -> log("  ✅ Success: " + outputPath));
                            fileSuccessCount++;
                        } else {
                            String errorMsg = applyResponse.has("message") ? applyResponse.get("message").getAsString() : "Unknown error";
                            Platform.runLater(() -> log("  ❌ Failed: " + errorMsg));
                            fileFailCount++;
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error processing subtitle for " + fileName, e);
                        Platform.runLater(() -> log("  ❌ Error: " + e.getMessage()));
                        fileFailCount++;
                    }
                }
                
                // Log file summary
                final int finalFileSuccess = fileSuccessCount;
                final int finalFileFail = fileFailCount;
                Platform.runLater(() -> {
                    if (finalFileSuccess > 0) {
                        log("  ✅ File complete: " + finalFileSuccess + " subtitle(s) applied successfully");
                        if (finalFileFail > 0) {
                            log("  ⚠️  " + finalFileFail + " subtitle(s) failed");
                        }
                    } else {
                        log("  ❌ File failed: All subtitles failed to apply");
                    }
                });
                
                totalSuccess += fileSuccessCount;
                totalFailed += fileFailCount;
            }
            
            // Final summary
            final int finalTotalSuccess = totalSuccess;
            final int finalTotalFailed = totalFailed;
            final int finalTotalSkipped = totalSkipped;
            final int finalFilesProcessed = filesProcessed;
            
            Platform.runLater(() -> {
                log("");
                log("═══════════════════════════════════════════════════");
                log("🎉 BATCH APPLY COMPLETE");
                log("═══════════════════════════════════════════════════");
                log("✅ Successfully applied: " + finalTotalSuccess + " subtitle(s)");
                if (finalTotalFailed > 0) {
                    log("❌ Failed: " + finalTotalFailed + " subtitle(s)");
                }
                if (finalTotalSkipped > 0) {
                    log("⏭️  Skipped: " + finalTotalSkipped + " file(s) (no subtitles selected)");
                }
                log("═══════════════════════════════════════════════════");
                
                // Show summary dialog
                if (finalTotalSuccess > 0) {
                    showInfo("Batch Apply Complete", 
                        "Successfully processed " + finalFilesProcessed + " file(s)\n\n" +
                        "✅ Applied: " + finalTotalSuccess + " subtitle(s)\n" +
                        (finalTotalFailed > 0 ? "❌ Failed: " + finalTotalFailed + " subtitle(s)\n" : "") +
                        (finalTotalSkipped > 0 ? "⏭️ Skipped: " + finalTotalSkipped + " file(s)" : "")
                    );
                } else {
                    showError("Batch Apply Failed", 
                        "No subtitles were successfully applied.\n\n" +
                        "Check the log for details.");
                }
            });
            
        }, "BatchSubtitleApply").start();
    }


    // ========== updateSelectedFilesLabel() ==========

    private void updateSelectedFilesLabel() {
        int fileCount = queuedFiles.size();
        String text = fileCount == 0 ? "No files selected" : fileCount + " file(s) selected";
        
        if (subtitleSelectedFilesLabel != null) {
            subtitleSelectedFilesLabel.setText(text);
        }
        if (renamerSelectedFilesLabel != null) {
            renamerSelectedFilesLabel.setText(text);
        }
    }

    // ========== Subtitle state fields ==========

    // Subtitle storage: Map of filename -> list of subtitles for that file
    private final java.util.Map<String, ObservableList<SubtitleItem>> subtitlesByFile = new java.util.HashMap<>();
    private String currentlySelectedFile = null;
    
    // Subtitle search status tracking
    private enum SubtitleSearchStatus { NONE, SEARCHING, COMPLETED }
    private final java.util.Map<String, SubtitleSearchStatus> subtitleSearchStatus = new java.util.HashMap<>();


    // ========== Subtitle FXML fields ==========

    @FXML private ComboBox<String> quickSubProviderCombo;
    // European Languages
    @FXML private CheckBox langEnglishCheck;
    @FXML private CheckBox langSpanishCheck;
    @FXML private CheckBox langSpanishLACheck;
    @FXML private CheckBox langFrenchCheck;
    @FXML private CheckBox langGermanCheck;
    @FXML private CheckBox langItalianCheck;
    @FXML private CheckBox langPortugueseCheck;
    @FXML private CheckBox langPortugueseBRCheck;
    // Asian Languages
    @FXML private CheckBox langJapaneseCheck;
    @FXML private CheckBox langChineseCheck;
    @FXML private CheckBox langChineseTWCheck;
    @FXML private CheckBox langKoreanCheck;
    @FXML private CheckBox langHindiCheck;
    @FXML private CheckBox langThaiCheck;
    @FXML private CheckBox langVietnameseCheck;
    // Other Major Languages
    @FXML private CheckBox langRussianCheck;
    @FXML private CheckBox langArabicCheck;
    @FXML private CheckBox langTurkishCheck;
    @FXML private CheckBox langPolishCheck;
    @FXML private CheckBox langDutchCheck;
    @FXML private CheckBox langSwedishCheck;
    @FXML private CheckBox langNorwegianCheck;
    @FXML private Button whisperStatusButton;
    @FXML private Button openSubsStatusButton;
    @FXML private Button subtitleProcessButton;
    @FXML private Button advancedSearchButton;
    @FXML private Button autoSelectBestButton;
    @FXML private TextField anilistUrlField;
    @FXML private Label subtitleSelectedFilesLabel;
    @FXML private ComboBox<String> subtitleLogLevelCombo;
    @FXML private TextArea subtitleLogArea;


    // ========== Subtitle table columns ==========

    @FXML private ComboBox<String> subtitleFileCombo;
    @FXML private TextArea subtitleDetailsLabel;
    @FXML private Label subtitleStatsLabel;
    @FXML private ComboBox<String> subtitleOutputModeCombo;
    @FXML private TableView<SubtitleItem> availableSubtitlesTable;
    @FXML private TableColumn<SubtitleItem, Boolean> subtitleSelectColumn;
    @FXML private TableColumn<SubtitleItem, String> subtitleLanguageColumn;
    @FXML private TableColumn<SubtitleItem, String> subtitleProviderColumn;
    @FXML private TableColumn<SubtitleItem, Double> subtitleScoreColumn;
    @FXML private TableColumn<SubtitleItem, String> subtitleFormatColumn;

}

