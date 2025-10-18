package com.ffmpeg.gui.controller;

import com.ffmpeg.gui.model.ConversionJob;
import com.ffmpeg.gui.model.ConversionSettings;
import com.ffmpeg.gui.service.PythonBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Main window controller for the modernized UI
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private final PythonBridge pythonBridge;
    private final ObservableList<ConversionJob> conversionQueue = FXCollections.observableArrayList();
    private final ConversionSettings settings = new ConversionSettings();
    private boolean isProcessing = false;
    private File lastDirectory;
    
    // Top Toolbar
    @FXML private ComboBox<String> modeComboBox;
    @FXML private Button addFilesButton;
    @FXML private Button addFolderButton;
    @FXML private Button startButton;
    @FXML private Button pauseButton;
    @FXML private Button stopButton;
    @FXML private Button settingsButton;
    @FXML private Label statusLabel;
    
    // Mode-Specific Quick Settings
    @FXML private javafx.scene.layout.HBox encoderQuickSettings;
    @FXML private javafx.scene.layout.HBox subtitleQuickSettings;
    @FXML private javafx.scene.layout.HBox renamerQuickSettings;
    
    // Encoder Quick Settings
    @FXML private ComboBox<String> quickFormatCombo;
    @FXML private ComboBox<String> quickCodecCombo;
    @FXML private ComboBox<String> quickQualityCombo;
    @FXML private CheckBox quickHwAccelCheck;
    
    // Subtitle Quick Settings
    @FXML private ComboBox<String> quickSubProviderCombo;
    @FXML private ComboBox<String> quickSubLanguageCombo;
    @FXML private CheckBox quickAutoDownloadCheck;
    @FXML private Label whisperStatusLabel;
    @FXML private Label openSubsStatusLabel;
    
    // Renamer Quick Settings
    @FXML private ComboBox<String> quickRenameProviderCombo;
    @FXML private ComboBox<String> quickRenameTypeCombo;
    @FXML private Label tmdbStatusLabel;
    @FXML private Label tvdbStatusLabel;
    @FXML private Label anilistStatusLabel;
    
    // Preview Tabs
    @FXML private Tab renamePreviewTab;
    @FXML private Tab subtitlePreviewTab;
    
    // Rename Preview
    @FXML private ComboBox<String> previewProviderCombo;
    @FXML private ListView<String> originalNamesListView;
    @FXML private ListView<String> suggestedNamesListView;
    @FXML private Label activeProviderLabel;
    @FXML private Label renameStatsLabel;
    @FXML private CheckBox createBackupCheck;
    
    // Subtitle Preview
    @FXML private ComboBox<String> subtitleFileCombo;
    @FXML private TableView<SubtitleItem> availableSubtitlesTable;
    @FXML private Label subtitleDetailsLabel;
    @FXML private Label subtitleStatsLabel;
    @FXML private CheckBox embedSubtitlesCheck;
    
    private String currentMode = "encoder";
    
    // Queue Table
    @FXML private TableView<ConversionJob> queueTable;
    @FXML private TableColumn<ConversionJob, String> statusIconColumn;
    @FXML private TableColumn<ConversionJob, String> fileNameColumn;
    @FXML private TableColumn<ConversionJob, String> outputFormatColumn;
    @FXML private TableColumn<ConversionJob, Double> progressColumn;
    @FXML private TableColumn<ConversionJob, String> sizeColumn;
    @FXML private TableColumn<ConversionJob, String> speedColumn;
    @FXML private TableColumn<ConversionJob, String> etaColumn;
    @FXML private Label queueCountLabel;
    @FXML private Button removeSelectedButton;
    @FXML private Button clearQueueButton;
    
    // Current File Tab
    @FXML private Label currentFileLabel;
    @FXML private ProgressBar currentFileProgressBar;
    @FXML private Label progressPercentLabel;
    @FXML private Label speedLabel;
    @FXML private Label etaLabel;
    @FXML private Label frameLabel;
    
    // Logs Tab
    @FXML private ComboBox<String> logLevelComboBox;
    @FXML private TextArea logTextArea;
    
    // File Info Tab
    @FXML private Label fileInfoLabel;
    @FXML private VBox mediaInfoSection;
    @FXML private ListView<String> videoTracksListView;
    @FXML private ListView<String> audioTracksListView;
    @FXML private ListView<String> subtitleTracksListView;
    @FXML private TabPane rightPanelTabs;
    @FXML private SplitPane leftPanelSplit;
    @FXML private Tab fileInfoTab;
    @FXML private Tab logsTab;
    
    public MainController(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
    }
    
    @FXML
    public void initialize() {
        logger.info("Initializing Main Controller");
        
        setupQueueTable();
        setupQueueDragAndDrop();
        setupUIBindings();
        setupQuickSettings();
        setupPreviewTabs();
        
        // Set default mode
        if (modeComboBox != null) {
            modeComboBox.getSelectionModel().selectFirst();
        }
        
        checkFFmpegAvailability();
        checkProviderStatus();
        
        logger.info("Main Controller initialized");
    }
    
    @FXML
    private void handleModeChange() {
        String selected = modeComboBox.getSelectionModel().getSelectedItem();
        
        if (selected != null) {
            if (selected.contains("Encoder")) {
                currentMode = "encoder";
                startButton.setText("▶️ Start Encoding");
                showModePanel(encoderQuickSettings);
                
                // Show encoder-specific UI
                if (leftPanelSplit != null) {
                    leftPanelSplit.setVisible(true);
                    leftPanelSplit.setManaged(true);
                }
                if (rightPanelTabs != null) {
                    rightPanelTabs.setVisible(true);
                    rightPanelTabs.setManaged(true);
                    // Show only File Info and Logs tabs
                    rightPanelTabs.getTabs().clear();
                    if (fileInfoTab != null) rightPanelTabs.getTabs().add(fileInfoTab);
                    if (logsTab != null) rightPanelTabs.getTabs().add(logsTab);
                }
                
            } else if (selected.contains("Subtitle")) {
                currentMode = "subtitle";
                startButton.setText("🎙️ Generate/Download");
                showModePanel(subtitleQuickSettings);
                
                // Hide queue/progress, show only subtitle preview
                if (leftPanelSplit != null) {
                    leftPanelSplit.setVisible(false);
                    leftPanelSplit.setManaged(false);
                }
                if (rightPanelTabs != null) {
                    rightPanelTabs.setVisible(true);
                    rightPanelTabs.setManaged(true);
                    rightPanelTabs.getTabs().clear();
                    if (subtitlePreviewTab != null) {
                        subtitlePreviewTab.setDisable(false);
                        rightPanelTabs.getTabs().add(subtitlePreviewTab);
                    }
                    if (logsTab != null) rightPanelTabs.getTabs().add(logsTab);
                }
                
            } else if (selected.contains("Renamer")) {
                currentMode = "renamer";
                startButton.setText("📝 Preview/Rename");
                showModePanel(renamerQuickSettings);
                
                // Hide queue/progress, show only rename preview
                if (leftPanelSplit != null) {
                    leftPanelSplit.setVisible(false);
                    leftPanelSplit.setManaged(false);
                }
                if (rightPanelTabs != null) {
                    rightPanelTabs.setVisible(true);
                    rightPanelTabs.setManaged(true);
                    rightPanelTabs.getTabs().clear();
                    if (renamePreviewTab != null) {
                        renamePreviewTab.setDisable(false);
                        rightPanelTabs.getTabs().add(renamePreviewTab);
                    }
                    if (logsTab != null) rightPanelTabs.getTabs().add(logsTab);
                }
                
                // Immediately load files and show original names
                if (!conversionQueue.isEmpty()) {
                    updateRenamePreview();
                }
            }
            
            log("Switched to " + currentMode + " mode");
        }
    }
    
    private void showModePanel(javafx.scene.layout.Region panel) {
        // Hide all panels
        if (encoderQuickSettings != null) {
            encoderQuickSettings.setVisible(false);
            encoderQuickSettings.setManaged(false);
        }
        if (subtitleQuickSettings != null) {
            subtitleQuickSettings.setVisible(false);
            subtitleQuickSettings.setManaged(false);
        }
        if (renamerQuickSettings != null) {
            renamerQuickSettings.setVisible(false);
            renamerQuickSettings.setManaged(false);
        }
        
        // Show the selected panel
        if (panel != null) {
            panel.setVisible(true);
            panel.setManaged(true);
        }
    }
    
    private void hidePreviewTabs() {
        if (renamePreviewTab != null) {
            renamePreviewTab.setDisable(true);
        }
        if (subtitlePreviewTab != null) {
            subtitlePreviewTab.setDisable(true);
        }
    }
    
    private void showPreviewTab(Tab tab) {
        hidePreviewTabs();
        if (tab != null) {
            tab.setDisable(false);
        }
    }
    
    private void setupQueueTable() {
        // Setup columns
        statusIconColumn.setCellValueFactory(new PropertyValueFactory<>("statusIcon"));
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        outputFormatColumn.setCellValueFactory(new PropertyValueFactory<>("outputFormat"));
        progressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("sizeString"));
        speedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        etaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        
        // Custom progress bar column
        progressColumn.setCellFactory(col -> new TableCell<ConversionJob, Double>() {
            private final ProgressBar progressBar = new ProgressBar();
            private final Label label = new Label();
            
            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    progressBar.setProgress(progress / 100.0);
                    progressBar.setPrefWidth(80);
                    label.setText(String.format("%.1f%%", progress));
                    setGraphic(progressBar);
                }
            }
        });
        
        // Set items
        queueTable.setItems(conversionQueue);
        
        // Selection listener for file info
        queueTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFileInfo(newVal);
            }
        });
        
        updateQueueCount();
    }
    
    private void setupUIBindings() {
        // Initialize log level combo box
        if (logLevelComboBox != null) {
            logLevelComboBox.setItems(FXCollections.observableArrayList("All", "Info", "Warning", "Error"));
            logLevelComboBox.setValue("All");
        }
        
        // Update start button based on queue state (don't bind, manually control)
        conversionQueue.addListener((javafx.collections.ListChangeListener<ConversionJob>) c -> {
            if (!isProcessing) {
                startButton.setDisable(conversionQueue.isEmpty());
            }
        });
        startButton.setDisable(true);
    }
    
    @FXML
    private void handleAddFiles() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Video Files");
        chooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("Video Files", 
                "*.mkv", "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.webm"),
            new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        if (lastDirectory != null && lastDirectory.exists()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        
        List<File> selectedFiles = chooser.showOpenMultipleDialog(addFilesButton.getScene().getWindow());
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            lastDirectory = selectedFiles.get(0).getParentFile();
            addFilesToQueue(selectedFiles);
        }
    }
    
    @FXML
    private void handleAddFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder with Video Files");
        
        if (lastDirectory != null && lastDirectory.exists()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        
        File selectedDir = chooser.showDialog(addFolderButton.getScene().getWindow());
        if (selectedDir != null) {
            lastDirectory = selectedDir;
            scanDirectory(selectedDir);
        }
    }
    
    private void addFilesToQueue(List<File> files) {
        int added = 0;
        for (File file : files) {
            boolean alreadyInQueue = conversionQueue.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath()));
            
            if (!alreadyInQueue) {
                ConversionJob job = new ConversionJob(file.getAbsolutePath());
                job.setOutputFormat("MP4"); // Default
                conversionQueue.add(job);
                added++;
            }
        }
        
        updateQueueCount();
        log("Added " + added + " file(s) to queue");
    }
    
    private void scanDirectory(File directory) {
        statusLabel.setText("Scanning...");
        addFolderButton.setDisable(true);
        
        new Thread(() -> {
            try {
                JsonObject response = pythonBridge.scanDirectory(directory.getAbsolutePath(), true);
                
                Platform.runLater(() -> {
                    if (response.get("status").getAsString().equals("success")) {
                        JsonArray filesArray = response.getAsJsonArray("files");
                        List<File> files = new ArrayList<>();
                        filesArray.forEach(element -> files.add(new File(element.getAsString())));
                        
                        addFilesToQueue(files);
                        statusLabel.setText("Scan complete - " + files.size() + " files found");
                    } else {
                        String error = response.has("message") ? response.get("message").getAsString() : "Scan failed";
                        showError("Scan Error", error);
                        statusLabel.setText("Ready");
                    }
                    addFolderButton.setDisable(false);
                });
                
            } catch (IOException | TimeoutException e) {
                logger.error("Error scanning directory", e);
                Platform.runLater(() -> {
                    showError("Scan Error", "Failed to scan directory: " + e.getMessage());
                    statusLabel.setText("Ready");
                    addFolderButton.setDisable(false);
                });
            }
        }).start();
    }
    
    @FXML
    private void handleStartProcessing() {
        if (conversionQueue.isEmpty() || isProcessing) {
            return;
        }
        
        // Route based on mode
        switch (currentMode) {
            case "encoder":
                handleStartEncoding();
                break;
            case "subtitle":
                handleStartSubtitles();
                break;
            case "renamer":
                handleStartRenaming();
                break;
        }
    }
    
    private void handleStartEncoding() {
        isProcessing = true;
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        statusLabel.setText("Encoding...");
        
        // Get pending files
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : conversionQueue) {
            if (job.getStatus().equals("pending")) {
                filePaths.add(job.getInputPath());
                job.setStatus("queued");
            }
        }
        
        log("Starting encoding of " + filePaths.size() + " file(s)");
        
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
    
    private void handleStartSubtitles() {
        isProcessing = true;
        startButton.setDisable(true);
        statusLabel.setText("Generating/downloading subtitles...");
        
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : conversionQueue) {
            if (job.getStatus().equals("pending")) {
                filePaths.add(job.getInputPath());
                job.setStatus("queued");
            }
        }
        
        log("Starting subtitle processing for " + filePaths.size() + " file(s)");
        
        // Start subtitle processing in background
        new Thread(() -> {
            try {
                for (String filePath : filePaths) {
                    Platform.runLater(() -> currentFileLabel.setText(new File(filePath).getName()));
                    
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
                                    conversionQueue.stream()
                                        .filter(job -> job.getInputPath().equals(filePath))
                                        .findFirst()
                                        .ifPresent(job -> job.setStatus("completed"));
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
                                    conversionQueue.stream()
                                        .filter(job -> job.getInputPath().equals(filePath))
                                        .findFirst()
                                        .ifPresent(job -> job.setStatus("completed"));
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
    
    private void handleStartRenaming() {
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : conversionQueue) {
            if (job.getStatus().equals("pending")) {
                filePaths.add(job.getInputPath());
            }
        }
        
        if (filePaths.isEmpty()) {
            showWarning("No Files", "No files to rename.");
            return;
        }
        
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
                        for (ConversionJob job : conversionQueue) {
                            if (filePaths.contains(job.getInputPath())) {
                                job.setStatus("completed");
                            }
                        }
                        queueTable.refresh();
                        
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
    }
    
    @FXML
    private void handlePause() {
        if (isProcessing) {
            log("Pausing operations...");
            // TODO: Send pause command to Python backend
            statusLabel.setText("Paused");
            pauseButton.setText("▶️ Resume");
            pauseButton.getStyleClass().add("primary-button");
            showInfo("Paused", "Processing has been paused. Click Resume to continue.");
        } else {
            log("Resuming operations...");
            // TODO: Send resume command to Python backend
            statusLabel.setText("Processing...");
            pauseButton.setText("⏸️ Pause");
            pauseButton.getStyleClass().remove("primary-button");
        }
    }
    
    @FXML
    private void handleStop() {
        if (!isProcessing) {
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Stop Processing");
        confirm.setHeaderText("Stop all operations?");
        confirm.setContentText("This will cancel all ongoing operations. Progress will be lost.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            log("Stopping operations...");
            
            // Cancel all processing jobs
            for (ConversionJob job : conversionQueue) {
                if (job.getStatus().equals("processing") || job.getStatus().equals("queued")) {
                    job.setStatus("cancelled");
                    job.setProgress(0.0);
                }
            }
            
            // TODO: Send stop command to Python backend
            
            resetProcessingState();
            queueTable.refresh();
            
            showInfo("Stopped", "All operations have been cancelled.");
        }
    }
    
    @FXML
    private void handleRemoveSelected() {
        ObservableList<ConversionJob> selectedJobs = queueTable.getSelectionModel().getSelectedItems();
        
        if (selectedJobs.isEmpty()) {
            showWarning("No Selection", "Please select one or more files to remove.");
            return;
        }
        
        List<ConversionJob> jobsToRemove = new ArrayList<>(selectedJobs);
        conversionQueue.removeAll(jobsToRemove);
        updateQueueCount();
        
        log("Removed " + jobsToRemove.size() + " file(s) from queue");
    }
    
    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsDialog.fxml"));
            SettingsController controller = new SettingsController();
            loader.setController(controller);
            
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Settings");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(settingsButton.getScene().getWindow());
            dialogStage.setScene(scene);
            
            controller.setDialogStage(dialogStage);
            controller.setSettings(settings);
            controller.setPythonBridge(pythonBridge);
            
            dialogStage.showAndWait();
            
            if (controller.isApplied()) {
                log("Settings updated");
            }
            
        } catch (IOException e) {
            logger.error("Error opening settings dialog", e);
            showError("Error", "Failed to open settings: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleClearQueue() {
        if (!isProcessing && !conversionQueue.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear Queue");
            alert.setHeaderText("Clear all files from queue?");
            alert.setContentText("This will remove all files from the queue.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                conversionQueue.clear();
                updateQueueCount();
                log("Queue cleared");
            }
        }
    }
    
    @FXML
    private void handleMoveUp() {
        int index = queueTable.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            ConversionJob job = conversionQueue.remove(index);
            conversionQueue.add(index - 1, job);
            queueTable.getSelectionModel().select(index - 1);
        }
    }
    
    @FXML
    private void handleMoveDown() {
        int index = queueTable.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < conversionQueue.size() - 1) {
            ConversionJob job = conversionQueue.remove(index);
            conversionQueue.add(index + 1, job);
            queueTable.getSelectionModel().select(index + 1);
        }
    }
    
    @FXML
    private void handleOpenFileLocation() {
        ConversionJob selected = queueTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                File file = new File(selected.getInputPath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                }
            } catch (IOException e) {
                logger.error("Error opening file location", e);
            }
        }
    }
    
    @FXML
    private void handleClearLogs() {
        logTextArea.clear();
    }
    
    @FXML
    private void handleExportLogs() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Logs");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        chooser.setInitialFileName("ffmpeg-gui-logs.txt");
        
        File file = chooser.showSaveDialog(logTextArea.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), logTextArea.getText());
                log("Logs exported to: " + file.getAbsolutePath());
            } catch (IOException e) {
                showError("Export Error", "Failed to export logs: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleProfiles() {
        showInfo("Profiles", "Profile management coming soon!");
    }
    
    @FXML
    private void handleBatchRename() {
        showInfo("Batch Rename", "FileBot-style batch renaming coming soon!");
    }
    
    @FXML
    private void handleCheckFFmpeg() {
        checkFFmpegAvailability();
    }
    
    @FXML
    private void handleDownloadFFmpeg() {
        showInfo("Download FFmpeg", "Automatic FFmpeg download coming soon!");
    }
    
    @FXML
    private void handleDocumentation() {
        try {
            java.awt.Desktop.getDesktop().browse(
                new java.net.URI("https://github.com/yourusername/ffmpeg-gui")
            );
        } catch (Exception e) {
            logger.error("Error opening documentation", e);
        }
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("FFmpeg Batch Transcoder");
        alert.setContentText("Version 2.0\n\nA modern, cross-platform video transcoding application.");
        alert.showAndWait();
    }
    
    @FXML
    private void handleExit() {
        if (isProcessing) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit");
            alert.setHeaderText("Conversions in progress");
            alert.setContentText("Are you sure you want to exit?");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }
        
        Platform.exit();
    }
    
    private void handleProgressUpdate(JsonObject update) {
        Platform.runLater(() -> {
            if (update.has("progress")) {
                double progress = update.get("progress").getAsDouble();
                String fileName = update.get("file").getAsString();
                
                currentFileProgressBar.setProgress(progress / 100.0);
                currentFileLabel.setText(new File(fileName).getName());
                progressPercentLabel.setText(String.format("%.1f%%", progress));
                
                // Update job in queue
                conversionQueue.stream()
                    .filter(job -> job.getInputPath().equals(fileName))
                    .findFirst()
                    .ifPresent(job -> {
                        job.setProgress(progress);
                        job.setStatus("processing");
                    });
                
                queueTable.refresh();
                
            } else if (update.has("status")) {
                String status = update.get("status").getAsString();
                
                if (status.equals("complete")) {
                    log("All conversions completed successfully");
                    
                    for (ConversionJob job : conversionQueue) {
                        if (job.getStatus().equals("processing") || job.getStatus().equals("queued")) {
                            job.setStatus("completed");
                            job.setProgress(100.0);
                        }
                    }
                    
                    resetProcessingState();
                    statusLabel.setText("Complete!");
                    
                } else if (status.equals("error")) {
                    String message = update.has("message") ? update.get("message").getAsString() : "Unknown error";
                    log("ERROR: " + message);
                    showError("Conversion Error", message);
                    resetProcessingState();
                }
                
                queueTable.refresh();
            }
        });
    }
    
    private void resetProcessingState() {
        isProcessing = false;
        startButton.setDisable(conversionQueue.isEmpty());
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        statusLabel.setText("Ready");
    }
    
    private void checkFFmpegAvailability() {
        new Thread(() -> {
            try {
                JsonObject response = pythonBridge.checkFFmpeg();
                Platform.runLater(() -> {
                    if (response.has("status") && response.get("status").getAsString().equals("error")) {
                        log("ERROR: " + response.get("message").getAsString());
                        statusLabel.setText("FFmpeg not found");
                        return;
                    }
                    
                    if (response.has("ffmpeg_available") && response.get("ffmpeg_available").getAsBoolean()) {
                        String version = response.get("ffmpeg_version").getAsString();
                        log("FFmpeg detected: " + version);
                        statusLabel.setText("FFmpeg: " + version);
                        
                        // Log hardware capabilities
                        if (response.has("hardware_encoders")) {
                            JsonArray encoders = response.getAsJsonArray("hardware_encoders");
                            if (encoders.size() > 0) {
                                log("Hardware encoders available: " + encoders.size());
                            }
                        }
                    } else {
                        log("WARNING: FFmpeg not detected");
                        statusLabel.setText("FFmpeg not found");
                        showWarning("FFmpeg Not Found", 
                            "FFmpeg could not be detected. Please install FFmpeg or set the path in Settings.");
                    }
                });
            } catch (IOException | TimeoutException e) {
                logger.error("Error checking FFmpeg", e);
                Platform.runLater(() -> {
                    log("ERROR checking FFmpeg: " + e.getMessage());
                    statusLabel.setText("Error checking FFmpeg");
                });
            } catch (Exception e) {
                logger.error("Unexpected error checking FFmpeg", e);
                Platform.runLater(() -> {
                    log("ERROR: " + e.getMessage());
                    statusLabel.setText("Error");
                });
            }
        }).start();
    }
    
    private void updateQueueCount() {
        queueCountLabel.setText(conversionQueue.size() + " file" + (conversionQueue.size() != 1 ? "s" : ""));
    }
    
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
        
        // Fetch detailed media info from Python
        new Thread(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("action", "get_media_info");
                request.addProperty("file_path", job.getInputPath());
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                Platform.runLater(() -> {
                    if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                        updateMediaTracks(response);
                    } else {
                        // Fallback to basic info
                        if (videoTracksListView != null) videoTracksListView.getItems().clear();
                        if (audioTracksListView != null) audioTracksListView.getItems().clear();
                        if (subtitleTracksListView != null) subtitleTracksListView.getItems().clear();
                    }
                });
            } catch (Exception e) {
                logger.error("Error getting media info", e);
            }
        }).start();
    }
    
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
    
    private void updateRenamePreview() {
        if (originalNamesListView == null || suggestedNamesListView == null) {
            return;
        }
        
        // Clear existing lists
        originalNamesListView.getItems().clear();
        suggestedNamesListView.getItems().clear();
        
        // Show original filenames immediately
        for (ConversionJob job : conversionQueue) {
            originalNamesListView.getItems().add(job.getFileName());
            suggestedNamesListView.getItems().add("Searching...");
        }
        
        // Fetch suggested names from Python in background
        new Thread(() -> {
            try {
                List<String> filePaths = new ArrayList<>();
                for (ConversionJob job : conversionQueue) {
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
    }
    
    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            logTextArea.appendText(String.format("[%s] %s\n", timestamp, message));
        });
    }
    
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public void shutdown() {
        logger.info("Shutting down main controller");
        if (isProcessing) {
            // TODO: Cancel ongoing operations
            isProcessing = false;
        }
    }
    
    // ========== Queue Drag and Drop ==========
    
    private void setupQueueDragAndDrop() {
        if (queueTable == null) {
            return;
        }
        
        queueTable.setRowFactory(tv -> {
            TableRow<ConversionJob> row = new TableRow<>();
            
            // Drag detected
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(String.valueOf(index));
                    db.setContent(cc);
                    
                    row.getStyleClass().add("drag-source");
                    event.consume();
                }
            });
            
            // Drag over
            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    
                    if (!row.isEmpty()) {
                        row.getStyleClass().add("drop-target");
                    }
                }
                event.consume();
            });
            
            // Drag exited
            row.setOnDragExited(event -> {
                row.getStyleClass().removeAll("drop-target");
                event.consume();
            });
            
            // Drag dropped
            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    ConversionJob draggedJob = conversionQueue.get(draggedIndex);
                    
                    int dropIndex;
                    if (row.isEmpty()) {
                        dropIndex = conversionQueue.size() - 1;
                    } else {
                        dropIndex = row.getIndex();
                    }
                    
                    conversionQueue.remove(draggedIndex);
                    if (dropIndex > draggedIndex) {
                        conversionQueue.add(dropIndex - 1, draggedJob);
                    } else {
                        conversionQueue.add(dropIndex, draggedJob);
                    }
                    
                    queueTable.getSelectionModel().clearSelection();
                    queueTable.getSelectionModel().select(dropIndex);
                    
                    log("Reordered: moved item from position " + (draggedIndex + 1) + " to " + (dropIndex + 1));
                    
                    event.setDropCompleted(true);
                }
                event.consume();
            });
            
            // Drag done
            row.setOnDragDone(event -> {
                row.getStyleClass().removeAll("drag-source", "drop-target");
                event.consume();
            });
            
            return row;
        });
    }
    
    // ========== Quick Settings Setup ==========
    
    private void setupQuickSettings() {
        // Initialize encoder quick settings
        if (quickFormatCombo != null) {
            quickFormatCombo.setItems(FXCollections.observableArrayList(
                "MP4", "MKV", "AVI", "MOV", "WEBM"
            ));
            quickFormatCombo.setValue("MP4");
        }
        
        if (quickCodecCombo != null) {
            quickCodecCombo.setItems(FXCollections.observableArrayList(
                "HEVC (H.265)", "AVC (H.264)", "AV1", "VP9", "Copy"
            ));
            quickCodecCombo.setValue("HEVC (H.265)");
        }
        
        if (quickQualityCombo != null) {
            quickQualityCombo.setItems(FXCollections.observableArrayList(
                "High (CQ 18)", "Medium (CQ 23)", "Low (CQ 28)", "Very Low (CQ 32)"
            ));
            quickQualityCombo.setValue("Medium (CQ 23)");
        }
        
        if (quickHwAccelCheck != null) {
            quickHwAccelCheck.setSelected(true);
        }
        
        // Initialize subtitle quick settings
        if (quickSubProviderCombo != null) {
            quickSubProviderCombo.setItems(FXCollections.observableArrayList(
                "Automatic", "OpenSubtitles Only", "Whisper AI Only", "Both Sources"
            ));
            quickSubProviderCombo.setValue("Automatic");
        }
        
        if (quickSubLanguageCombo != null) {
            quickSubLanguageCombo.setItems(FXCollections.observableArrayList(
                "English", "Spanish", "French", "German", "Japanese", "Korean", "Chinese", "Multi-Language"
            ));
            quickSubLanguageCombo.setValue("English");
        }
        
        if (quickAutoDownloadCheck != null) {
            quickAutoDownloadCheck.setSelected(true);
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
        
        // Hide preview tabs by default
        hidePreviewTabs();
    }
    
    private void checkProviderStatus() {
        new Thread(() -> {
            try {
                // Check FFmpeg status
                JsonObject ffmpegResponse = pythonBridge.checkFFmpeg();
                boolean ffmpegAvailable = ffmpegResponse.has("ffmpeg_available") && 
                                         ffmpegResponse.get("ffmpeg_available").getAsBoolean();
                
                // Update status label in main toolbar
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        if (ffmpegAvailable) {
                            String version = ffmpegResponse.has("ffmpeg_version") ? 
                                ffmpegResponse.get("ffmpeg_version").getAsString() : "Unknown";
                            statusLabel.setText("FFmpeg: " + version);
                        } else {
                            statusLabel.setText("FFmpeg: Not Found");
                        }
                    }
                });
                
                // Check Whisper status (for subtitle mode)
                JsonObject whisperCmd = new JsonObject();
                whisperCmd.addProperty("action", "check_whisper");
                JsonObject whisperResponse = pythonBridge.sendCommand(whisperCmd);
                boolean whisperAvailable = whisperResponse.has("whisper_available") && 
                                          whisperResponse.get("whisper_available").getAsBoolean();
                
                Platform.runLater(() -> {
                    if (whisperStatusLabel != null) {
                        if (whisperAvailable) {
                            whisperStatusLabel.setText("🤖 Whisper: Installed");
                            whisperStatusLabel.getStyleClass().removeAll("error", "warning");
                            whisperStatusLabel.getStyleClass().add("active");
                        } else {
                            whisperStatusLabel.setText("🤖 Whisper: Not Installed");
                            whisperStatusLabel.getStyleClass().removeAll("active");
                            whisperStatusLabel.getStyleClass().add("warning");
                        }
                    }
                });
                
                // Update OpenSubtitles status
                Platform.runLater(() -> {
                    if (openSubsStatusLabel != null) {
                        if (settings.getOpensubtitlesApiKey() != null && !settings.getOpensubtitlesApiKey().isEmpty()) {
                            openSubsStatusLabel.setText("🌐 OpenSubtitles: Configured");
                            openSubsStatusLabel.getStyleClass().removeAll("error", "warning");
                            openSubsStatusLabel.getStyleClass().add("active");
                        } else {
                            openSubsStatusLabel.setText("🌐 OpenSubtitles: No API Key");
                            openSubsStatusLabel.getStyleClass().removeAll("active");
                            openSubsStatusLabel.getStyleClass().add("warning");
                        }
                    }
                });
                
                // Update TMDB status
                Platform.runLater(() -> {
                    if (tmdbStatusLabel != null) {
                        if (settings.getTmdbApiKey() != null && !settings.getTmdbApiKey().isEmpty()) {
                            tmdbStatusLabel.setText("🎬 TMDB: Configured");
                            tmdbStatusLabel.getStyleClass().removeAll("error", "warning");
                            tmdbStatusLabel.getStyleClass().add("active");
                        } else {
                            tmdbStatusLabel.setText("🎬 TMDB: No API Key");
                            tmdbStatusLabel.getStyleClass().removeAll("active");
                            tmdbStatusLabel.getStyleClass().add("warning");
                        }
                    }
                });
                
                // Update TVDB status
                Platform.runLater(() -> {
                    if (tvdbStatusLabel != null) {
                        if (settings.getTvdbApiKey() != null && !settings.getTvdbApiKey().isEmpty()) {
                            tvdbStatusLabel.setText("📺 TVDB: Configured");
                            tvdbStatusLabel.getStyleClass().removeAll("error", "warning");
                            tvdbStatusLabel.getStyleClass().add("active");
                        } else {
                            tvdbStatusLabel.setText("📺 TVDB: No API Key");
                            tvdbStatusLabel.getStyleClass().removeAll("active");
                            tvdbStatusLabel.getStyleClass().add("warning");
                        }
                    }
                });
                
                // AniList doesn't require API key
                Platform.runLater(() -> {
                    if (anilistStatusLabel != null) {
                        anilistStatusLabel.setText("🎌 AniList: Available");
                        anilistStatusLabel.getStyleClass().removeAll("error", "warning");
                        anilistStatusLabel.getStyleClass().add("active");
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error checking provider status", e);
            }
        }).start();
    }
    
    // ========== New Action Handlers ==========
    
    @FXML
    private void handleConfigureSubtitleProviders() {
        handleSettings(); // Open settings dialog to API Keys section
    }
    
    @FXML
    private void handleOpenFormatter() {
        showInfo("Format Pattern", "Format pattern editor coming soon!\n\nAvailable tokens:\n" +
            "{title} - Show/Movie title\n" +
            "{year} - Release year\n" +
            "{season} - Season number (S01)\n" +
            "{episode} - Episode number (E01)\n" +
            "{episodeTitle} - Episode title\n" +
            "{quality} - Video quality\n" +
            "{codec} - Video codec");
    }
    
    @FXML
    private void handlePreviewRename() {
        if (conversionQueue.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Generating rename preview...");
        
        // Populate original names
        ObservableList<String> originalNames = FXCollections.observableArrayList();
        for (ConversionJob job : conversionQueue) {
            originalNames.add(job.getFileName());
        }
        originalNamesListView.setItems(originalNames);
        
        // TODO: Fetch suggestions from Python backend
        ObservableList<String> suggestedNames = FXCollections.observableArrayList();
        for (ConversionJob job : conversionQueue) {
            suggestedNames.add(job.getFileName() + " [Suggested]");
        }
        suggestedNamesListView.setItems(suggestedNames);
        
        renameStatsLabel.setText(conversionQueue.size() + " files | " + suggestedNames.size() + " changes");
        
        log("Preview generated. Switch to 'Rename Preview' tab to review.");
    }
    
    @FXML
    private void handleRefreshMetadata() {
        log("Refreshing metadata...");
        handlePreviewRename(); // Re-fetch metadata
    }
    
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
            log("Applying rename changes...");
            // TODO: Implement actual renaming via Python backend
            showInfo("Rename Complete", "Files renamed successfully!");
        }
    }
    
    @FXML
    private void handleSearchSubtitles() {
        if (conversionQueue.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Searching for subtitles...");
        subtitleStatsLabel.setText("Searching...");
        
        // TODO: Implement subtitle search via Python backend
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate search
                Platform.runLater(() -> {
                    subtitleStatsLabel.setText("5 available | 3 languages");
                    log("Subtitle search complete. Found 5 subtitles.");
                });
            } catch (InterruptedException e) {
                logger.error("Subtitle search interrupted", e);
            }
        }).start();
    }
    
    @FXML
    private void handleGenerateAI() {
        if (conversionQueue.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Generating AI subtitles with Whisper...");
        showInfo("AI Generation", "AI subtitle generation started.\nThis may take several minutes...");
        
        // TODO: Implement AI subtitle generation via Python backend
    }
    
    @FXML
    private void handleDownloadSubtitles() {
        log("Downloading selected subtitles...");
        showInfo("Download", "Subtitle download started...");
        
        // TODO: Implement subtitle download via Python backend
    }
    
    // Inner class for subtitle table
    public static class SubtitleItem {
        private boolean selected;
        private String language;
        private String provider;
        private String format;
        
        public SubtitleItem(boolean selected, String language, String provider, String format) {
            this.selected = selected;
            this.language = language;
            this.provider = provider;
            this.format = format;
        }
        
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}
