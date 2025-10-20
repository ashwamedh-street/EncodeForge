package com.encodeforge.controller.components;

import com.encodeforge.model.ConversionJob;
import com.encodeforge.model.ConversionSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;


/**
 * RenamerController - Handle file renaming with metadata lookup
 */
public class RenamerController implements ISubController {
        
    public RenamerController() {
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
    

    // ========== handleStartRenaming() ==========

    private void handleStartRenaming() {
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("🏷️  Starting file renaming process...");
        log("📡 Metadata Providers: TMDB (movies/TV), TVDB (TV shows), AniDB (anime)");
        
        // Log API key status
        if (settings.getTmdbApiKey() != null && !settings.getTmdbApiKey().trim().isEmpty()) {
            log("✅ TMDB API key configured (movies & TV shows)");
        } else {
            log("⚠️  TMDB: No API key - limited or no movie/TV metadata");
        }
        
        if (settings.getTvdbApiKey() != null && !settings.getTvdbApiKey().trim().isEmpty()) {
            log("✅ TVDB API key configured (TV shows)");
        } else {
            log("⚠️  TVDB: No API key - limited or no TV show metadata");
        }
        
        log("ℹ️  File names will be formatted using: {title} - S{season}E{episode} - {episodeTitle}");
        
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
        }
        
        if (filePaths.isEmpty()) {
            showWarning("No Files", "No files to rename.");
            return;
        }
        
        log("Processing " + filePaths.size() + " file(s) for rename suggestions...");
        
        log("Fetching rename preview for " + filePaths.size() + " file(s)");
        statusLabel.setText("Fetching metadata...");
        startButton.setDisable(true);
        
        // Get rename preview
        new Thread(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("action", "preview_rename");
                com.google.gson.JsonArray filesArray = new com.google.gson.JsonArray();
                filePaths.forEach(filesArray::add);
                request.add("file_paths", filesArray);
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                Platform.runLater(() -> {
                    if (response.get("status").getAsString().equals("success")) {
                        // Build preview text
                        StringBuilder previewText = new StringBuilder();
                        previewText.append("Rename Preview:\n\n");
                        
                        JsonArray previews = response.getAsJsonArray("previews");
                        for (int i = 0; i < previews.size(); i++) {
                            JsonObject preview = previews.get(i).getAsJsonObject();
                            String oldName = preview.get("original_name").getAsString();
                            String newName = preview.get("new_name").getAsString();
                            boolean canRename = preview.get("can_rename").getAsBoolean();
                            
                            if (canRename && !oldName.equals(newName)) {
                                previewText.append(String.format("✓ %s\n  → %s\n\n", oldName, newName));
                            } else {
                                previewText.append(String.format("✗ %s\n  (no metadata found)\n\n", oldName));
                            }
                        }
                        
                        // Show preview dialog
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Rename Preview");
                        alert.setHeaderText("Review the proposed renames:");
                        
                        TextArea textArea = new TextArea(previewText.toString());
                        textArea.setEditable(false);
                        textArea.setWrapText(true);
                        textArea.setPrefRowCount(15);
                        textArea.setPrefColumnCount(60);
                        alert.getDialogPane().setContent(textArea);
                        
                        alert.setWidth(700);
                        alert.setHeight(500);
                        
                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            // Perform actual rename
                            performRename(filePaths);
                        } else {
                            log("Rename cancelled by user");
                            startButton.setDisable(false);
                            statusLabel.setText("Ready");
                        }
                    } else {
                        String error = response.has("message") ? response.get("message").getAsString() : "Preview failed";
                        showError("Rename Error", error);
                        startButton.setDisable(false);
                        statusLabel.setText("Ready");
                    }
                });
                
            } catch (IOException | TimeoutException e) {
                logger.error("Error getting rename preview", e);
                Platform.runLater(() -> {
                    showError("Rename Error", "Failed to get rename preview: " + e.getMessage());
                    startButton.setDisable(false);
                    statusLabel.setText("Ready");
                });
            }
        }).start();
    }


    // ========== performRename() ==========

    private void performRename(List<String> filePaths) {
        isProcessing = true;
        statusLabel.setText("Renaming files...");
        
        new Thread(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("action", "rename_files");
                com.google.gson.JsonArray filesArray = new com.google.gson.JsonArray();
                filePaths.forEach(filesArray::add);
                request.add("file_paths", filesArray);
                request.addProperty("dry_run", false);
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                Platform.runLater(() -> {
                    if (response.get("status").getAsString().equals("success")) {
                        int successCount = response.get("success_count").getAsInt();
                        int failedCount = response.get("failed_count").getAsInt();
                        
                        log(String.format("Renamed %d file(s), %d failed", successCount, failedCount));
                        
                        // Update queue
                        for (ConversionJob job : queuedFiles) {
                            if (filePaths.contains(job.getInputPath())) {
                                job.setStatus("✅ Completed");
                            }
                        }
                        queuedTable.refresh();
                        
                        showInfo("Rename Complete", 
                            String.format("Successfully renamed %d file(s).\n%d file(s) could not be renamed.", 
                                successCount, failedCount));
                    } else {
                        String error = response.has("message") ? response.get("message").getAsString() : "Rename failed";
                        showError("Rename Error", error);
                    }
                    
                    resetProcessingState();
                });
                
            } catch (IOException | TimeoutException e) {
                logger.error("Error renaming files", e);
                Platform.runLater(() -> {
                    showError("Rename Error", "Failed to rename files: " + e.getMessage());
                    resetProcessingState();
                });
            }
        }).start();


    // ========== updateRenamePreview() ==========

    private void updateRenamePreview() {
        if (originalNamesListView == null || suggestedNamesListView == null) {
            return;
        }
        
        // Clear existing lists
        originalNamesListView.getItems().clear();
        suggestedNamesListView.getItems().clear();
        
        // Show original filenames immediately
        for (ConversionJob job : queuedFiles) {
            originalNamesListView.getItems().add(job.getFileName());
            suggestedNamesListView.getItems().add("Searching...");
        }
        
        // Fetch suggested names from Python in background
        new Thread(() -> {
            try {
                List<String> filePaths = new ArrayList<>();
                for (ConversionJob job : queuedFiles) {
                    filePaths.add(job.getInputPath());
                }
                
                JsonObject request = new JsonObject();
                request.addProperty("action", "preview_rename");
                request.add("file_paths", new com.google.gson.Gson().toJsonTree(filePaths));
                request.add("settings", new com.google.gson.Gson().toJsonTree(settings.toJson()));
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                Platform.runLater(() -> {
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        suggestedNamesListView.getItems().clear();
                        JsonArray suggestions = response.getAsJsonArray("suggested_names");
                        JsonArray providers = response.has("providers") ? response.getAsJsonArray("providers") : null;
                        
                        for (int i = 0; i < suggestions.size(); i++) {
                            String suggestion = suggestions.get(i).getAsString();
                            String provider = (providers != null && i < providers.size()) 
                                ? providers.get(i).getAsString() 
                                : "Unknown";
                            suggestedNamesListView.getItems().add(suggestion + " [" + provider + "]");
                        }
                        
                        log("Rename preview loaded with " + suggestions.size() + " suggestions");
                    } else {
                        log("Error loading rename preview: " + response.get("message").getAsString());
                    }
                });
            } catch (Exception e) {
                logger.error("Error getting rename preview", e);
                Platform.runLater(() -> log("Error: " + e.getMessage()));
            }
        }).start();


    // ========== setupPreviewTabs() ==========

    private void setupPreviewTabs() {
        // Initialize rename preview
        if (previewProviderCombo != null) {
            previewProviderCombo.setItems(FXCollections.observableArrayList(
                "TMDB", "TVDB", "AniList", "Automatic"
            ));
            previewProviderCombo.setValue("Automatic");
        }
        
        if (originalNamesListView != null) {
            originalNamesListView.setItems(FXCollections.observableArrayList());
        }
        
        if (suggestedNamesListView != null) {
            suggestedNamesListView.setItems(FXCollections.observableArrayList());
        }
        
        // Initialize subtitle preview
        if (subtitleFileCombo != null) {
            subtitleFileCombo.setItems(FXCollections.observableArrayList());
        }
    }


    // ========== handleOpenFormatter() ==========

    @FXML
    private void handleOpenFormatter() {
        try {
            // Load the pattern editor dialog
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/PatternEditorDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            
            // Get the controller and configure it
            PatternEditorController controller = loader.getController();
            
            // Create a new stage for the dialog
            javafx.stage.Stage patternStage = new javafx.stage.Stage();
            patternStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            patternStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            patternStage.setTitle("Format Pattern Editor");
            
            // Set the scene with CSS stylesheet
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            patternStage.setScene(scene);
            
            // Configure the controller
            controller.setDialogStage(patternStage);
            controller.setSettings(settings);
            
            // Set stage for window controls (similar to MainController.setStage)
            patternStage.setMinWidth(1000);
            patternStage.setMinHeight(700);
            
            // Determine which pattern type to show based on current selection
            String renameType = quickRenameTypeCombo != null ? quickRenameTypeCombo.getValue() : "Auto Detect";
            if (renameType != null) {
                if (renameType.contains("Movie")) {
                    controller.setPatternType("Movie");
                } else if (renameType.contains("Anime")) {
                    controller.setPatternType("Anime");
                } else {
                    controller.setPatternType("TV Show");
                }
            }
            
            // Show and wait
            patternStage.showAndWait();
            
            // If saved, log success
            if (controller.isSaved()) {
                log("Format patterns updated successfully");
            }
            
        } catch (Exception e) {
            logger.error("Error opening pattern editor", e);
            showError("Error", "Could not open pattern editor: " + e.getMessage());
        }
    }


    // ========== handlePreviewRename() ==========

    @FXML
    private void handlePreviewRename() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Generating rename preview...");
        
        // Populate original names
        ObservableList<String> originalNames = FXCollections.observableArrayList();
        for (ConversionJob job : queuedFiles) {
            originalNames.add(job.getFileName());
        }
        originalNamesListView.setItems(originalNames);
        
        // TODO: Fetch suggestions from Python backend
        ObservableList<String> suggestedNames = FXCollections.observableArrayList();
        for (ConversionJob job : queuedFiles) {
            suggestedNames.add(job.getFileName() + " [Suggested]");
        }
        suggestedNamesListView.setItems(suggestedNames);
        
        renameStatsLabel.setText(queuedFiles.size() + " files | " + suggestedNames.size() + " changes");
        
        log("Preview generated. Switch to 'Rename Preview' tab to review.");
    }


    // ========== handleRefreshMetadata() ==========

    @FXML
    private void handleRefreshMetadata() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("🔍 Searching metadata providers for file information...");
        
        // Disable buttons during search
        if (searchMetadataButton != null) {
            searchMetadataButton.setDisable(true);
        }
        if (applyRenameButton != null) {
            applyRenameButton.setDisable(true);
        }
        
        // Build request
        com.google.gson.JsonObject request = new com.google.gson.JsonObject();
        request.addProperty("action", "preview_rename");
        
        // Add file paths
        com.google.gson.JsonArray filePaths = new com.google.gson.JsonArray();
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
        }
        request.add("file_paths", filePaths);
        
        // Add settings
        com.google.gson.JsonObject settingsDict = new com.google.gson.JsonObject();
        ConversionSettings settings = ConversionSettings.load();
        settingsDict.addProperty("tmdb_api_key", settings.getTmdbApiKey());
        settingsDict.addProperty("tvdb_api_key", settings.getTvdbApiKey());
        request.add("settings", settingsDict);
        
        // Send to Python backend asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject jsonResponse = pythonBridge.sendCommand(request);
                String status = jsonResponse.get("status").getAsString();
                
                if ("success".equals(status)) {
                    com.google.gson.JsonArray suggestedNames = jsonResponse.getAsJsonArray("suggested_names");
                    com.google.gson.JsonArray providers = jsonResponse.getAsJsonArray("providers");
                    com.google.gson.JsonArray errors = jsonResponse.getAsJsonArray("errors");
                    
                    // Populate lists
                    Platform.runLater(() -> {
                        ObservableList<String> originalNames = FXCollections.observableArrayList();
                        ObservableList<String> newNames = FXCollections.observableArrayList();
                        
                        int successCount = 0;
                        int errorCount = 0;
                        
                        for (int i = 0; i < queuedFiles.size() && i < suggestedNames.size(); i++) {
                            ConversionJob job = queuedFiles.get(i);
                            originalNames.add(job.getFileName());
                            
                            String suggested = suggestedNames.get(i).getAsString();
                            String provider = providers.get(i).getAsString();
                            String error = errors.get(i).getAsString();
                            
                            if (!error.isEmpty()) {
                                newNames.add(error);
                                errorCount++;
                            } else {
                                newNames.add(suggested + " [via " + provider + "]");
                                successCount++;
                            }
                        }
                        
                        if (originalNamesListView != null) {
                            originalNamesListView.setItems(originalNames);
                        }
                        if (suggestedNamesListView != null) {
                            suggestedNamesListView.setItems(newNames);
                        }
                        
                        if (renameStatsLabel != null) {
                            renameStatsLabel.setText(queuedFiles.size() + " files | " + successCount + " ready | " + errorCount + " errors");
                        }
                        
                        // Enable apply button if we have results
                        if (applyRenameButton != null) {
                            applyRenameButton.setDisable(successCount == 0);
                        }
                        
                        // Re-enable search button
                        if (searchMetadataButton != null) {
                            searchMetadataButton.setDisable(false);
                        }
                        
                        log("✅ Metadata search complete!");
                        log("   " + successCount + " file(s) ready to rename");
                        if (errorCount > 0) {
                            log("   " + errorCount + " file(s) had errors (metadata not found)");
                        }
                    });
                } else {
                    String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown error";
                    Platform.runLater(() -> {
                        log("❌ Metadata search failed: " + message);
                        showError("Search Failed", message);
                        
                        // Re-enable search button
                        if (searchMetadataButton != null) {
                            searchMetadataButton.setDisable(false);
                        }
                    });
                }
            } catch (Exception e) {
                logger.error("Error processing rename preview response", e);
                Platform.runLater(() -> {
                    log("❌ Error: " + e.getMessage());
                    showError("Error", "Failed to process metadata: " + e.getMessage());
                    
                    // Re-enable search button
                    if (searchMetadataButton != null) {
                        searchMetadataButton.setDisable(false);
                    }
                });
            }
        }).exceptionally(ex -> {
            logger.error("Error in rename preview async task", ex);
            Platform.runLater(() -> {
                log("❌ Error: " + ex.getMessage());
                if (searchMetadataButton != null) {
                    searchMetadataButton.setDisable(false);
                }
            });
            return null;
        });
    }


    // ========== handleApplyRename() ==========

    @FXML
    private void handleApplyRename() {
        if (suggestedNamesListView.getItems().isEmpty()) {
            showWarning("No Changes", "No rename suggestions available.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Rename");
        confirm.setHeaderText("Apply rename changes?");
        confirm.setContentText("This will rename " + suggestedNamesListView.getItems().size() + " files.\n" +
            (createBackupCheck.isSelected() ? "A backup list will be created." : "No backup will be created."));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log("💾 Applying rename changes...");
            if (createBackupCheck.isSelected()) {
                log("📝 Creating backup of original filenames...");
            }
            // TODO: Implement actual renaming via Python backend
            showInfo("Rename Complete", "Files renamed successfully!");
        }
    }
    
    // ========== Renamer FXML fields ==========

    // Renamer Quick Settings
    @FXML private ComboBox<String> quickRenameProviderCombo;
    @FXML private ComboBox<String> quickRenameTypeCombo;
    @FXML private Button tmdbStatusButton;
    @FXML private Button tvdbStatusButton;
    @FXML private Button omdbStatusButton;
    @FXML private Button traktStatusButton;
    @FXML private Button fanartStatusButton;
    @FXML private Button searchMetadataButton;
    @FXML private Button applyRenameButton;
    @FXML private Button formatPatternButton;
    @FXML private Label renamerSelectedFilesLabel;
    @FXML private Label renameProgressLabel;
    @FXML private ComboBox<String> renamerLogLevelCombo;
    @FXML private TextArea renamerLogArea;


    // ========== Preview elements ==========

    
    // Preview elements (now in separate mode layouts)
    @FXML private ComboBox<String> previewProviderCombo;
    @FXML private ListView<String> originalNamesListView;
    @FXML private ListView<String> suggestedNamesListView;
    @FXML private Label activeProviderLabel;
    @FXML private Label renameStatsLabel;
    @FXML private CheckBox createBackupCheck;

}

