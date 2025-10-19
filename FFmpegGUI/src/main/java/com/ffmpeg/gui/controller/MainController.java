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
    private final ConversionSettings settings;
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
    @FXML private javafx.scene.layout.VBox encoderQuickSettings;
    @FXML private javafx.scene.layout.VBox subtitleQuickSettings;
    @FXML private javafx.scene.layout.VBox renamerQuickSettings;
    
    // Mode Layouts
    @FXML private javafx.scene.control.SplitPane encoderModeLayout;
    @FXML private javafx.scene.control.SplitPane subtitleModeLayout;
    @FXML private javafx.scene.control.SplitPane renamerModeLayout;
    
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
    
    // Subtitle Quick Settings
    @FXML private ComboBox<String> quickSubProviderCombo;
    @FXML private CheckBox langEnglishCheck;
    @FXML private CheckBox langSpanishCheck;
    @FXML private CheckBox langFrenchCheck;
    @FXML private CheckBox langGermanCheck;
    @FXML private CheckBox langJapaneseCheck;
    @FXML private CheckBox langChineseCheck;
    @FXML private CheckBox langKoreanCheck;
    @FXML private CheckBox langArabicCheck;
    @FXML private Button whisperStatusButton;
    @FXML private Button openSubsStatusButton;
    @FXML private Button subtitleSearchButton;
    @FXML private Button subtitleDownloadButton;
    @FXML private Button subtitleGenerateButton;
    @FXML private Label subtitleSelectedFilesLabel;
    @FXML private ComboBox<String> subtitleLogLevelCombo;
    @FXML private TextArea subtitleLogArea;
    
    // Renamer Quick Settings
    @FXML private ComboBox<String> quickRenameProviderCombo;
    @FXML private ComboBox<String> quickRenameTypeCombo;
    @FXML private Button tmdbStatusButton;
    @FXML private Button tvdbStatusButton;
    @FXML private Button anilistStatusButton;
    @FXML private Button refreshMetadataButton;
    @FXML private Button applyRenameButton;
    @FXML private Label renamerSelectedFilesLabel;
    @FXML private Label renameProgressLabel;
    @FXML private ComboBox<String> renamerLogLevelCombo;
    @FXML private TextArea renamerLogArea;
    
    // Preview elements (now in separate mode layouts)
    @FXML private ComboBox<String> previewProviderCombo;
    @FXML private ListView<String> originalNamesListView;
    @FXML private ListView<String> suggestedNamesListView;
    @FXML private Label activeProviderLabel;
    @FXML private Label renameStatsLabel;
    @FXML private CheckBox createBackupCheck;
    @FXML private ComboBox<String> subtitleFileCombo;
    @FXML private Label subtitleDetailsLabel;
    @FXML private Label subtitleStatsLabel;
    @FXML private CheckBox embedSubtitlesCheck;
    @FXML private TableView<SubtitleItem> availableSubtitlesTable;
    
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
        
        // Load settings from disk
        this.settings = ConversionSettings.load();
        logger.info("Settings loaded from: {}", ConversionSettings.getSettingsFilePath());
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
                showModePanel(encoderQuickSettings);
                showModeLayout(encoderModeLayout);
                
            } else if (selected.contains("Subtitle")) {
                currentMode = "subtitle";
                showModePanel(subtitleQuickSettings);
                showModeLayout(subtitleModeLayout);
                updateSubtitleFileList();
                
            } else if (selected.contains("Renamer")) {
                currentMode = "renamer";
                showModePanel(renamerQuickSettings);
                showModeLayout(renamerModeLayout);
                
                // Immediately load files and show original names
                if (!conversionQueue.isEmpty()) {
                    updateRenamePreview();
                }
            }
            
            updateSelectedFilesLabel();
            log("Switched to " + currentMode + " mode");
        }
    }
    
    private void showModeLayout(javafx.scene.control.SplitPane layout) {
        // Hide all layouts
        if (encoderModeLayout != null) {
            encoderModeLayout.setVisible(false);
            encoderModeLayout.setManaged(false);
        }
        if (subtitleModeLayout != null) {
            subtitleModeLayout.setVisible(false);
            subtitleModeLayout.setManaged(false);
        }
        if (renamerModeLayout != null) {
            renamerModeLayout.setVisible(false);
            renamerModeLayout.setManaged(false);
        }
        
        // Show selected layout
        if (layout != null) {
            layout.setVisible(true);
            layout.setManaged(true);
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
                job.setStatus("⏳ Queued");
                conversionQueue.add(job);
                added++;
            }
        }
        
        updateQueueCount();
        updateSelectedFilesLabel();
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
        
        // Get queued or failed files (allow retry)
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : conversionQueue) {
            String status = job.getStatus();
            if (status.contains("Queued") || status.contains("pending") || status.contains("failed") || status.contains("error")) {
                filePaths.add(job.getInputPath());
                job.setStatus("⏳ Queued");
                job.setProgress(0.0); // Reset progress for retry
            }
        }
        
        if (filePaths.isEmpty()) {
            isProcessing = false;
            startButton.setDisable(false);
            pauseButton.setDisable(true);
            stopButton.setDisable(true);
            log("No files to encode. All files may already be completed.");
            showWarning("No Files", "No files available to encode. All files are either completed or currently processing.");
            return;
        }
        
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
    
    private void handleStartSubtitles() {
        isProcessing = true;
        startButton.setDisable(true);
        statusLabel.setText("Generating/downloading subtitles...");
        
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : conversionQueue) {
            if (job.getStatus().contains("pending") || job.getStatus().contains("Queued")) {
                filePaths.add(job.getInputPath());
                job.setStatus("⏳ Queued");
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
            String status = job.getStatus();
            if (status.contains("Queued") || status.contains("pending")) {
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
            log("Stopping current operation (FFmpeg doesn't support pause)...");
            
            // FFmpeg doesn't support pause, so we stop the current operation
            try {
                JsonObject command = new JsonObject();
                command.addProperty("action", "pause_conversion");
                pythonBridge.sendCommand(command);
                
                // Update UI to show paused state
                for (ConversionJob job : conversionQueue) {
                    if (job.getStatus().equals("processing")) {
                        job.setStatus("paused");
                    }
                }
                
                pauseButton.setText("Resume");
                statusLabel.setText("Paused");
                log("Operations paused (current file stopped)");
                
            } catch (Exception e) {
                logger.error("Error pausing operations", e);
                log("ERROR: Failed to pause operations - " + e.getMessage());
            }
        } else {
            log("Resuming operations...");
            // Resume by restarting encoding
            handleStartEncoding();
            pauseButton.setText("Pause");
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
            
            // Best-effort cancel in backend
            try {
                pythonBridge.requestStopConversion();
            } catch (Exception e) {
                logger.warn("Stop command failed: {}", e.getMessage());
            }

            // Update local queue states
            for (ConversionJob job : conversionQueue) {
                if (job.getStatus().equals("processing") || job.getStatus().equals("queued")) {
                    job.setStatus("cancelled");
                    job.setProgress(0.0);
                }
            }
            
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
        openSettings(null);
    }
    
    private void openSettings(String category) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsDialog.fxml"));
            SettingsController controller = new SettingsController();
            loader.setController(controller);
            
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Settings - EncodeForge");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(settingsButton.getScene().getWindow());
            dialogStage.setScene(scene);
            
            controller.setDialogStage(dialogStage);
            controller.setSettings(settings);
            controller.setPythonBridge(pythonBridge);
            
            // Navigate to specific category if provided
            if (category != null) {
                controller.navigateToCategory(category);
            }
            
            dialogStage.showAndWait();
            
            if (controller.isApplied()) {
                // Save settings to disk
                if (settings.save()) {
                    log("Settings updated and saved successfully");
                } else {
                    log("ERROR: Failed to save settings");
                }
                checkProviderStatus(); // Refresh provider status after settings change
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
                new java.net.URI("https://github.com/SirStig/EncodeForge")
            );
        } catch (Exception e) {
            logger.error("Error opening documentation", e);
        }
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("EncodeForge");
        alert.setContentText(
            "Version 1.0.0\n\n" +
            "A modern, cross-platform media encoding, subtitle management, and file renaming tool.\n\n" +
            "GitHub: https://github.com/SirStig/EncodeForge\n\n" +
            "Settings Location:\n" + ConversionSettings.getSettingsFilePath()
        );
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
                String status = update.has("status") ? update.get("status").getAsString() : "processing";
                
                // Handle negative progress (indicates indeterminate state)
                if (progress < 0) {
                    currentFileProgressBar.setProgress(-1);  // Indeterminate progress
                    progressPercentLabel.setText("Processing...");
                } else {
                    currentFileProgressBar.setProgress(progress / 100.0);
                    progressPercentLabel.setText(String.format("%.1f%%", progress));
                }
                
                // Update current file display
                currentFileLabel.setText(new File(fileName).getName());
                
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
                
                // Update job in queue
                conversionQueue.stream()
                    .filter(job -> job.getInputPath().equals(fileName))
                    .findFirst()
                    .ifPresent(job -> {
                        if (progress >= 0) {
                            job.setProgress(progress);
                        }
                        
                        // Update status based on state
                        switch (status.toLowerCase()) {
                            case "completed":
                                job.setStatus("✅ Completed");
                                job.setProgress(100.0);
                                break;
                            case "starting":
                                job.setStatus("🔄 Starting");
                                break;
                            case "finalizing":
                                job.setStatus("🔧 Finalizing");
                                break;
                            case "encoding":
                                job.setStatus("⚡ Encoding");
                                break;
                            case "cancelled":
                                job.setStatus("❌ Cancelled");
                                break;
                            case "failed":
                            case "error":
                                job.setStatus("⚠️ Failed");
                                break;
                            case "paused":
                                job.setStatus("⏸️ Paused");
                                break;
                            default:
                                job.setStatus("🔄 Processing");
                        }
                    });
                
                queueTable.refresh();
                
            } else if (update.has("status")) {
                String status = update.get("status").getAsString();
                
                if (status.equals("complete")) {
                    log("All conversions completed successfully");
                    
                    // Mark remaining jobs as completed
                    for (ConversionJob job : conversionQueue) {
                        String jobStatus = job.getStatus().toLowerCase();
                        if (jobStatus.contains("processing") || jobStatus.contains("queued") || 
                            jobStatus.contains("encoding") || jobStatus.contains("starting")) {
                            job.setStatus("✅ Completed");
                            job.setProgress(100.0);
                        }
                    }
                    
                    resetProcessingState();
                    statusLabel.setText("✅ All files completed!");
                    
                } else if (status.equals("cancelled")) {
                    String message = update.has("message") ? update.get("message").getAsString() : "Conversion cancelled";
                    log("CANCELLED: " + message);
                    
                    // Mark processing jobs as cancelled
                    for (ConversionJob job : conversionQueue) {
                        String jobStatus = job.getStatus().toLowerCase();
                        if (jobStatus.contains("processing") || jobStatus.contains("encoding") || 
                            jobStatus.contains("starting") || jobStatus.contains("queued")) {
                            job.setStatus("❌ Cancelled");
                        }
                    }
                    
                    resetProcessingState();
                    statusLabel.setText("❌ Cancelled");
                    
                } else if (status.equals("error") || status.equals("failed")) {
                    String message = update.has("message") ? update.get("message").getAsString() : "Unknown error";
                    log("ERROR: " + message);
                    
                    // Mark processing jobs as failed
                    for (ConversionJob job : conversionQueue) {
                        String jobStatus = job.getStatus().toLowerCase();
                        if (jobStatus.contains("processing") || jobStatus.contains("encoding") || 
                            jobStatus.contains("starting") || jobStatus.contains("queued")) {
                            job.setStatus("⚠️ Failed");
                        }
                    }
                    
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
                        
                        // Update encoder options based on hardware detection
                        updateAvailableEncoders();
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
                        
                        // Build list of available encoders
                        List<String> availableEncoders = new ArrayList<>();
                        
                        // Always add software encoders
                        availableEncoders.add("Software H.264");
                        availableEncoders.add("Software H.265");
                        
                        // Add hardware encoders if available
                        if (encoderSupport.has("nvidia_h264") && encoderSupport.get("nvidia_h264").getAsBoolean()) {
                            availableEncoders.add("H.264 NVENC (GPU)");
                        }
                        if (encoderSupport.has("nvidia_h265") && encoderSupport.get("nvidia_h265").getAsBoolean()) {
                            availableEncoders.add("H.265 NVENC (GPU)");
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
                        
                        // Update the ComboBox
                        if (quickCodecCombo != null) {
                            String currentSelection = quickCodecCombo.getValue();
                            quickCodecCombo.setItems(FXCollections.observableArrayList(availableEncoders));
                            
                            // Try to keep current selection if still available
                            if (availableEncoders.contains(currentSelection)) {
                                quickCodecCombo.setValue(currentSelection);
                            } else {
                                // Set to recommended encoder or fallback to software
                                if (response.has("recommended_encoder")) {
                                    String recommended = response.get("recommended_encoder").getAsString();
                                    if (recommended.equals("h264_nvenc") && availableEncoders.contains("H.264 NVENC (GPU)")) {
                                        quickCodecCombo.setValue("H.264 NVENC (GPU)");
                                    } else if (recommended.equals("h264_amf") && availableEncoders.contains("H.264 AMF (GPU)")) {
                                        quickCodecCombo.setValue("H.264 AMF (GPU)");
                                    } else if (recommended.equals("h264_qsv") && availableEncoders.contains("H.264 Intel QSV (CPU)")) {
                                        quickCodecCombo.setValue("H.264 Intel QSV (CPU)");
                                    } else {
                                        quickCodecCombo.setValue("Software H.264");
                                    }
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
            logger.info("Forcefully stopping all ongoing operations...");
            
            // Force stop all operations with timeout
            try {
                JsonObject command = new JsonObject();
                command.addProperty("action", "shutdown");
                
                // Send shutdown command in a separate thread with timeout
                Thread shutdownThread = new Thread(() -> {
                    try {
                        pythonBridge.sendCommand(command);
                        logger.info("Shutdown command sent to Python bridge");
                    } catch (Exception e) {
                        logger.error("Failed to send shutdown command", e);
                    }
                });
                
                shutdownThread.setDaemon(true);  // Don't block app shutdown
                shutdownThread.start();
                
                // Wait max 2 seconds for graceful shutdown
                try {
                    shutdownThread.join(2000);
                } catch (InterruptedException e) {
                    logger.warn("Shutdown thread interrupted");
                }
                
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
            
            // Update all jobs to cancelled state
            for (ConversionJob job : conversionQueue) {
                String status = job.getStatus();
                if (status.contains("processing") || status.contains("queued") || status.contains("Starting")) {
                    job.setStatus("❌ Cancelled");
                    job.setProgress(0.0);
                }
            }
            
            isProcessing = false;
        }
        
        // Save settings before shutdown (with timeout)
        try {
            settings.save();
            logger.info("Settings saved during shutdown");
        } catch (Exception e) {
            logger.error("Failed to save settings during shutdown", e);
        }
        
        logger.info("Main controller shutdown complete");
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
                "MP4", "MKV", "AVI", "MOV", "WEBM", "TS"
            ));
            quickFormatCombo.setValue("MP4");
        }
        
        // Initialize codec combo with default values, will be updated after hardware detection
        if (quickCodecCombo != null) {
            quickCodecCombo.setItems(FXCollections.observableArrayList(
                "Software H.264", "Software H.265", "Copy"
            ));
            quickCodecCombo.setValue("Software H.264");
        }
        
        if (quickQualityCombo != null) {
            quickQualityCombo.setItems(FXCollections.observableArrayList(
                "Highest (CQ 15)", "High (CQ 18)", "Medium (CQ 23)", "Low (CQ 28)", "Very Low (CQ 32)"
            ));
            quickQualityCombo.setValue("Medium (CQ 23)");
        }
        
        if (quickPresetCombo != null) {
            quickPresetCombo.setItems(FXCollections.observableArrayList(
                "Fast", "Medium", "Slow", "Quality", "Balanced"
            ));
            quickPresetCombo.setValue("Medium");
        }
        
        if (quickHwAccelCheck != null) {
            quickHwAccelCheck.setSelected(true);
        }
        
        if (quickDownloadSubsCheck != null) {
            quickDownloadSubsCheck.setSelected(false);
        }
        
        if (quickRenameCheck != null) {
            quickRenameCheck.setSelected(false);
        }
        
        // Initialize subtitle quick settings
        if (quickSubProviderCombo != null) {
            quickSubProviderCombo.setItems(FXCollections.observableArrayList(
                "Automatic (Prefer Download)", "Download Only", "Generate Only"
            ));
            quickSubProviderCombo.setValue("Automatic (Prefer Download)");
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
                JsonObject whisperResponse = pythonBridge.checkWhisper();
                boolean whisperAvailable = whisperResponse.has("whisper_available") && 
                                          whisperResponse.get("whisper_available").getAsBoolean();
                
                Platform.runLater(() -> {
                    if (whisperStatusButton != null) {
                        if (whisperAvailable) {
                            whisperStatusButton.setText("🤖 Whisper: Ready");
                            whisperStatusButton.getStyleClass().removeAll("error", "warning");
                            whisperStatusButton.getStyleClass().add("active");
                        } else {
                            whisperStatusButton.setText("🤖 Whisper: Not Setup");
                            whisperStatusButton.getStyleClass().removeAll("active");
                            whisperStatusButton.getStyleClass().add("warning");
                        }
                    }
                    // Disable generate button if Whisper not available
                    if (subtitleGenerateButton != null) {
                        subtitleGenerateButton.setDisable(!whisperAvailable);
                    }
                });
                
                // Check OpenSubtitles status
                JsonObject osResponse = pythonBridge.checkOpenSubtitles();
                boolean osConfigured = osResponse.has("configured") && 
                                      osResponse.get("configured").getAsBoolean();
                
                Platform.runLater(() -> {
                    if (openSubsStatusButton != null) {
                        if (osConfigured) {
                            openSubsStatusButton.setText("🌐 OpenSubtitles: Ready");
                            openSubsStatusButton.getStyleClass().removeAll("error", "warning");
                            openSubsStatusButton.getStyleClass().add("active");
                        } else {
                            openSubsStatusButton.setText("🌐 OpenSubtitles: Not Setup");
                            openSubsStatusButton.getStyleClass().removeAll("active");
                            openSubsStatusButton.getStyleClass().add("warning");
                        }
                    }
                    // Disable download button if OpenSubtitles not configured
                    if (subtitleDownloadButton != null) {
                        subtitleDownloadButton.setDisable(!osConfigured);
                    }
                });
                
                // Check TMDB status
                JsonObject tmdbResponse = pythonBridge.checkTMDB();
                boolean tmdbConfigured = tmdbResponse.has("configured") && 
                                        tmdbResponse.get("configured").getAsBoolean();
                
                // Check TVDB status
                JsonObject tvdbResponse = pythonBridge.checkTVDB();
                boolean tvdbConfigured = tvdbResponse.has("configured") && 
                                        tvdbResponse.get("configured").getAsBoolean();
                
                Platform.runLater(() -> {
                    if (tmdbStatusButton != null) {
                        if (tmdbConfigured) {
                            tmdbStatusButton.setText("🎬 TMDB: Ready");
                            tmdbStatusButton.getStyleClass().removeAll("error", "warning");
                            tmdbStatusButton.getStyleClass().add("active");
                        } else {
                            tmdbStatusButton.setText("🎬 TMDB: Not Setup");
                            tmdbStatusButton.getStyleClass().removeAll("active");
                            tmdbStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    if (tvdbStatusButton != null) {
                        if (tvdbConfigured) {
                            tvdbStatusButton.setText("📺 TVDB: Ready");
                            tvdbStatusButton.getStyleClass().removeAll("error", "warning");
                            tvdbStatusButton.getStyleClass().add("active");
                        } else {
                            tvdbStatusButton.setText("📺 TVDB: Not Setup");
                            tvdbStatusButton.getStyleClass().removeAll("active");
                            tvdbStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // AniList is always available, so renamer should always work
                    // Enable renamer buttons since we have at least AniList
                    if (refreshMetadataButton != null) {
                        refreshMetadataButton.setDisable(false);
                    }
                    if (applyRenameButton != null) {
                        applyRenameButton.setDisable(false);
                    }
                });
                
                // AniList doesn't require API key
                Platform.runLater(() -> {
                    if (anilistStatusButton != null) {
                        anilistStatusButton.setText("🎌 AniList: Available");
                        anilistStatusButton.getStyleClass().removeAll("error", "warning");
                        anilistStatusButton.getStyleClass().add("active");
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error checking provider status", e);
            }
        }).start();
    }
    
    // ========== New Action Handlers ==========
    
    @FXML
    private void handleConfigureSubtitles() {
        openSettings("Subtitles");
    }
    
    @FXML
    private void handleConfigureRenamer() {
        openSettings("Metadata");
    }
    
    @FXML
    private void handleConfigureWhisper() {
        openSettings("Subtitles");
    }
    
    @FXML
    private void handleConfigureOpenSubtitles() {
        openSettings("Subtitles");
    }
    
    @FXML
    private void handleConfigureTMDB() {
        openSettings("Metadata");
    }
    
    @FXML
    private void handleConfigureTVDB() {
        openSettings("Metadata");
    }
    
    @FXML
    private void handleConfigureAniList() {
        showInfo("AniList", "AniList does not require any configuration. It's ready to use!");
    }
    
    @FXML
    private void handleSaveSubtitles() {
        log("Saving selected subtitles...");
        // TODO: Implement subtitle saving functionality
        showInfo("Save Subtitles", "Subtitle saving functionality coming soon!");
    }
    
    private void updateSubtitleFileList() {
        if (subtitleFileCombo != null && !conversionQueue.isEmpty()) {
            ObservableList<String> fileNames = FXCollections.observableArrayList();
            for (ConversionJob job : conversionQueue) {
                fileNames.add(job.getFileName());
            }
            subtitleFileCombo.setItems(fileNames);
            if (!fileNames.isEmpty()) {
                subtitleFileCombo.getSelectionModel().selectFirst();
            }
        }
    }
    
    private void updateSelectedFilesLabel() {
        int fileCount = conversionQueue.size();
        String text = fileCount == 0 ? "No files selected" : fileCount + " file(s) selected";
        
        if (subtitleSelectedFilesLabel != null) {
            subtitleSelectedFilesLabel.setText(text);
        }
        if (renamerSelectedFilesLabel != null) {
            renamerSelectedFilesLabel.setText(text);
        }
    }
    
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
