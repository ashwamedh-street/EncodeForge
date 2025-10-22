package com.encodeforge.controller;

import com.encodeforge.model.ConversionJob;
import com.encodeforge.model.ConversionSettings;
import com.encodeforge.service.DependencyManager;
import com.encodeforge.service.PythonBridge;
import com.encodeforge.util.HardwareDetector;
import com.encodeforge.util.PathManager;
import com.encodeforge.util.StatusManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Main window controller for the modernized UI
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private final PythonBridge pythonBridge;
    private final DependencyManager dependencyManager;
    private final StatusManager statusManager;
    // Separate queues for different states
    private final ObservableList<ConversionJob> queuedFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> processingFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> completedFiles = FXCollections.observableArrayList();
    
    // Subtitle storage: Map of filename -> list of subtitles for that file
    // Using ConcurrentHashMap for thread-safe parallel subtitle searches
    private final java.util.Map<String, ObservableList<SubtitleItem>> subtitlesByFile = new java.util.concurrent.ConcurrentHashMap<>();
    private String currentlySelectedFile = null;
    
    // Subtitle search status tracking
    private enum SubtitleSearchStatus { NONE, SEARCHING, COMPLETED }
    // Using ConcurrentHashMap for thread-safe parallel status updates
    private final java.util.Map<String, SubtitleSearchStatus> subtitleSearchStatus = new java.util.concurrent.ConcurrentHashMap<>();
    
    private final ConversionSettings settings;
    private boolean isProcessing = false;
    private File lastDirectory;
    private Stage primaryStage;
    
    // Window dragging variables
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Window resizing variables
    private double resizeStartX = 0;
    private double resizeStartY = 0;
    private double resizeStartWidth = 0;
    private double resizeStartHeight = 0;
    private double resizeStartStageX = 0;
    private double resizeStartStageY = 0;
    private ResizeDirection resizeDirection = ResizeDirection.NONE;
    private boolean isResizing = false;
    
    // Minimum window size
    private static final double MIN_WIDTH = 1000;
    private static final double MIN_HEIGHT = 700;
    
    // Resize border thickness
    private static final double RESIZE_BORDER = 8;
    
    // Resize direction enum
    private enum ResizeDirection {
        NONE, N, S, E, W, NE, NW, SE, SW
    }
    
    // Window Controls
    @FXML private Label appIconLabel;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    
    // Icon sizes
    private static final int WINDOW_CONTROL_ICON_SIZE = 14;
    private static final int TOOLBAR_ICON_SIZE = 14;
    private static final int SMALL_ICON_SIZE = 12;
    
    // Sidebar Controls
    @FXML private Button encoderModeButton;
    @FXML private Button subtitleModeButton;
    @FXML private Button renamerModeButton;
    @FXML private Button addFilesButton;
    @FXML private Button addFolderButton;
    @FXML private Button settingsButton;
    @FXML private Button ffmpegStatusButton;
    @FXML private javafx.scene.control.Label ffmpegStatusIcon;
    @FXML private javafx.scene.control.Label ffmpegStatusLabel;
    
    // Control Buttons
    @FXML private Button startButton;
    @FXML private Button pauseButton;
    @FXML private Button stopButton;
    @FXML private Label statusLabel;
    
    // Mode-Specific Quick Settings
    @FXML private javafx.scene.layout.VBox encoderQuickSettings;
    @FXML private javafx.scene.layout.VBox subtitleQuickSettings;
    @FXML private javafx.scene.layout.VBox renamerQuickSettings;
    
    // Mode Layouts
    @FXML private javafx.scene.control.SplitPane encoderModeLayout;
    @FXML private javafx.scene.control.SplitPane subtitleModeLayout;
    @FXML private javafx.scene.control.SplitPane renamerModeLayout;
    
    // Queue Split Pane (for resizable sections)
    @FXML private javafx.scene.control.SplitPane queueSplitPane;
    
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
    @FXML private TextField anidbUrlField;
    @FXML private Label subtitleSelectedFilesLabel;
    @FXML private ComboBox<String> subtitleLogLevelCombo;
    @FXML private TextArea subtitleLogArea;
    
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
    @FXML private ComboBox<String> renamerLogLevelCombo;
    @FXML private TextArea renamerLogArea;
    
    // Subtitle Progress Bar Elements
    @FXML private ProgressBar subtitleTotalProgressBar;
    @FXML private Label subtitleProgressLabel;
    @FXML private Label subtitleProgressStatusLabel;
    
    // Log Toggle Elements
    @FXML private Button encoderLogToggleButton;
    @FXML private VBox encoderRightPanel;
    
    @FXML private Button subtitleLogToggleButton;
    @FXML private TabPane subtitleLogsTabPane;
    @FXML private VBox subtitleRightPanel;
    
    @FXML private Button renamerLogToggleButton;
    @FXML private TabPane renamerLogsTabPane;
    @FXML private VBox renamerRightPanel;
    
    // Log panel visibility state
    private boolean encoderLogsPanelVisible = true;
    private boolean subtitleLogsPanelVisible = true;
    private boolean renamerLogsPanelVisible = true;
    
    // Preview elements (now in separate mode layouts)
    @FXML private ComboBox<String> previewProviderCombo;
    @FXML private ListView<String> originalNamesListView;
    @FXML private ListView<String> suggestedNamesListView;
    @FXML private Label activeProviderLabel;
    @FXML private Label renameStatsLabel;
    @FXML private CheckBox createBackupCheck;
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
    
    private String currentMode = "encoder";
    
    // Queue Table
    // Queued Files Table
    @FXML private TableView<ConversionJob> queuedTable;
    @FXML private TableColumn<ConversionJob, String> queuedStatusColumn;
    @FXML private TableColumn<ConversionJob, String> queuedFileColumn;
    @FXML private TableColumn<ConversionJob, String> queuedOutputColumn;
    @FXML private TableColumn<ConversionJob, String> queuedSizeColumn;
    @FXML private Label queuedCountLabel;
    @FXML private Button removeQueuedButton;
    @FXML private Button clearQueuedButton;
    
    // Processing Files Table
    @FXML private TableView<ConversionJob> processingTable;
    @FXML private TableColumn<ConversionJob, String> procStatusColumn;
    @FXML private TableColumn<ConversionJob, String> procFileColumn;
    @FXML private TableColumn<ConversionJob, String> procOperationColumn;
    @FXML private TableColumn<ConversionJob, Double> procProgressColumn;
    @FXML private TableColumn<ConversionJob, String> procFpsColumn;
    @FXML private TableColumn<ConversionJob, String> procSpeedColumn;
    @FXML private TableColumn<ConversionJob, String> procEtaColumn;
    @FXML private Label processingCountLabel;
    
    // Completed Files Table
    @FXML private TableView<ConversionJob> completedTable;
    @FXML private TableColumn<ConversionJob, String> compStatusColumn;
    @FXML private TableColumn<ConversionJob, String> compFileColumn;
    @FXML private TableColumn<ConversionJob, String> compOutputColumn;
    @FXML private TableColumn<ConversionJob, String> compSizeColumn;
    @FXML private TableColumn<ConversionJob, String> compNewSizeColumn;
    @FXML private TableColumn<ConversionJob, String> compTimeColumn;
    @FXML private Label completedCountLabel;
    @FXML private Button clearCompletedButton;
    
    
    // Logs Tab (Encoder Mode)
    @FXML private ComboBox<String> logLevelComboBox;
    @FXML private TextArea logTextArea;
    
    // File Info Tab
    @FXML private Label fileInfoLabel;
    @FXML private VBox mediaInfoSection;
    @FXML private ListView<String> videoTracksListView;
    @FXML private ListView<String> audioTracksListView;
    @FXML private ListView<String> subtitleTracksListView;
    @FXML private TabPane rightPanelTabs;
    @FXML private Tab fileInfoTab;
    @FXML private Tab logsTab;
    
    public MainController(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
        
        // Get dependency manager from MainApp (will be passed in future refactor)
        // For now, create a new one
        try {
            this.dependencyManager = new DependencyManager();
        } catch (IOException e) {
            logger.error("Failed to initialize DependencyManager", e);
            throw new RuntimeException("Could not initialize dependency manager", e);
        }
        
        this.statusManager = StatusManager.getInstance();
        
        // Load settings from disk
        this.settings = ConversionSettings.load();
        logger.info("Settings loaded from: {}", ConversionSettings.getSettingsFilePath());
        
        // FFmpeg is now managed by DependencyManager, no need for embedded setup here
    }
    
    @FXML
    public void initialize() {
        logger.info("Initializing Main Controller");
        
        // Initialize all icons
        initializeWindowControlIcons();
        initializeToolbarIcons();
        
        setupQueueTable();
        setupQueueDragAndDrop();
        setupQueueSplitPane();
        setupUIBindings();
        setupQuickSettings();
        setupPreviewTabs();
        setupSubtitleFileSelector();
        setupSubtitleProgressBar();
        
        // Set default mode to encoder
        handleEncoderMode();
        
        // Defer status check to let Python backend fully initialize
        // This improves startup performance by showing UI first
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a moment for Python backend to be ready
                Thread.sleep(500);
                updateAllStatus();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Status check interrupted");
            }
        });
        
        // Check for ongoing conversions from previous session
        CompletableFuture.runAsync(this::checkForOngoingConversions);
        
        logger.info("Main Controller initialized (async status check deferred)");
    }
    
    private void setupSubtitleFileSelector() {
        if (subtitleFileCombo != null) {
            // Add listener for file selection changes
            subtitleFileCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(currentlySelectedFile)) {
                    currentlySelectedFile = newVal;
                    
                    // Extract base filename without status tags
                    String baseFileName = extractBaseFileName(newVal);
                    
                    // Debug: Log what we're looking for
                    logger.debug("Dropdown selection changed to: {} (base: {})", newVal, baseFileName);
                    logger.debug("Available files in map: {}", subtitlesByFile.keySet());
                    
                    // Update table to show subtitles for selected file
                    if (subtitlesByFile.containsKey(baseFileName) && availableSubtitlesTable != null) {
                        ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.get(baseFileName);
                        logger.debug("Found {} subtitles for file: {}", fileSubtitles.size(), baseFileName);
                        
                        // Log first subtitle for verification
                        if (!fileSubtitles.isEmpty()) {
                            SubtitleItem first = fileSubtitles.get(0);
                            logger.debug("First subtitle: {} from {}", first.getFormat(), first.getProvider());
                        }
                        
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
                    } else {
                        logger.warn("No subtitles found in map for: {}", baseFileName);
                    }
                }
            });
        }
    }
    
    /**
     * Get all files from all queues (queued, processing, completed)
     */
    private List<ConversionJob> getAllFiles() {
        List<ConversionJob> allFiles = new java.util.ArrayList<>();
        allFiles.addAll(queuedFiles);
        allFiles.addAll(processingFiles);
        allFiles.addAll(completedFiles);
        return allFiles;
    }
    
    private void updateSubtitleFileList() {
        if (subtitleFileCombo != null) {
            List<ConversionJob> allFiles = getAllFiles();
            if (!allFiles.isEmpty()) {
            subtitleFileCombo.getItems().clear();
            
                // Add files with status tags from ALL queues
                for (ConversionJob job : allFiles) {
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
    }
    
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
    
    private String extractBaseFileName(String displayName) {
        // Remove status tags to get original filename
        if (displayName.contains(" [")) {
            return displayName.substring(0, displayName.indexOf(" ["));
        }
        return displayName;
    }
    
    @FXML
    private void handleEncoderMode() {
        currentMode = "encoder";
        showModePanel(encoderQuickSettings);
        showModeLayout(encoderModeLayout);
        updateModeButtonSelection(encoderModeButton);
        updateSelectedFilesLabel();
        log("Switched to encoder mode");
    }
    
    @FXML
    private void handleSubtitleMode() {
        currentMode = "subtitle";
        showModePanel(subtitleQuickSettings);
        showModeLayout(subtitleModeLayout);
        updateModeButtonSelection(subtitleModeButton);
        updateSubtitleFileList();
        updateSelectedFilesLabel();
        log("Switched to subtitle mode");
    }
    
    @FXML
    private void handleRenamerMode() {
        currentMode = "renamer";
        showModePanel(renamerQuickSettings);
        showModeLayout(renamerModeLayout);
        updateModeButtonSelection(renamerModeButton);
        
        // Show original filenames without searching
        if (!queuedFiles.isEmpty()) {
            showOriginalFilenames();
        }
        updateSelectedFilesLabel();
        log("Switched to metadata mode");
    }
    
    private void updateModeButtonSelection(Button selectedButton) {
        // Remove selected style from all mode buttons
        encoderModeButton.getStyleClass().removeAll("selected");
        subtitleModeButton.getStyleClass().removeAll("selected");
        renamerModeButton.getStyleClass().removeAll("selected");
        
        // Add selected style to the chosen button
        selectedButton.getStyleClass().add("selected");
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
    
    /**
     * Update FFmpeg status display in sidebar
     */
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
    
    /**
     * Handle FFmpeg status button click - opens settings to FFmpeg section
     */
    @FXML
    private void handleFFmpegStatus() {
        handleSettings();
        // TODO: Navigate to FFmpeg settings section when settings dialog is opened
    }
    
    
    private void setupQueueTable() {
        setupQueuedTable();
        setupProcessingTable();
        setupCompletedTable();
        setupSubtitleTable();
    }
    
    private void setupQueuedTable() {
        // Setup columns
        queuedStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("⏳"));
        queuedFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        queuedOutputColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(settings.getOutputFormat().toUpperCase()));
        queuedSizeColumn.setCellValueFactory(cd -> {
            try {
                long size = java.nio.file.Files.size(java.nio.file.Paths.get(cd.getValue().getInputPath()));
                return new javafx.beans.property.SimpleStringProperty(formatFileSize(size));
            } catch (java.io.IOException e) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
        });
        
        // Set items
        queuedTable.setItems(queuedFiles);
        
        // Selection listener
        queuedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFileInfo(newVal);
            }
        });
    }
    
    private void setupProcessingTable() {
        // Setup columns
        procStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("⚡"));
        procFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        procOperationColumn.setCellValueFactory(new PropertyValueFactory<>("operation"));
        procProgressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        procFpsColumn.setCellValueFactory(new PropertyValueFactory<>("fps"));
        procSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        procEtaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        
        // Custom progress bar column
        procProgressColumn.setCellFactory(col -> new TableCell<ConversionJob, Double>() {
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
                    VBox box = new VBox(2, progressBar, label);
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    setGraphic(box);
                }
            }
        });
        
        // Set items
        processingTable.setItems(processingFiles);
    }
    
    private void setupCompletedTable() {
        // Setup columns
        compStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("✅"));
        compFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        compOutputColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(settings.getOutputFormat().toUpperCase()));
        compSizeColumn.setCellValueFactory(cd -> {
            try {
                long size = java.nio.file.Files.size(java.nio.file.Paths.get(cd.getValue().getInputPath()));
                return new javafx.beans.property.SimpleStringProperty(formatFileSize(size));
            } catch (java.io.IOException e) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
        });
        compNewSizeColumn.setCellValueFactory(new PropertyValueFactory<>("outputSizeString"));
        compTimeColumn.setCellValueFactory(new PropertyValueFactory<>("timeTaken"));
        
        // Set items
        completedTable.setItems(completedFiles);
        
        // Selection listener
        completedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFileInfo(newVal);
            }
        });
    }
    
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
    
    private String getScoreDescription(double score) {
        if (score >= 90) return "Excellent match - highly recommended";
        if (score >= 75) return "Good match - should work well";
        if (score >= 60) return "Fair match - may need adjustments";
        if (score >= 40) return "Poor match - timing may be off";
        return "Low match - not recommended";
    }
    
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
    
    private String truncateUrl(String url) {
        if (url.length() > 60) {
            return url.substring(0, 57) + "...";
        }
        return url;
    }
    
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
    
    private void updateQueueCounts() {
        if (queuedCountLabel != null) {
            queuedCountLabel.setText(queuedFiles.size() + " file" + (queuedFiles.size() != 1 ? "s" : ""));
        }
        if (processingCountLabel != null) {
            processingCountLabel.setText(processingFiles.size() + " file" + (processingFiles.size() != 1 ? "s" : ""));
        }
        if (completedCountLabel != null) {
            completedCountLabel.setText(completedFiles.size() + " file" + (completedFiles.size() != 1 ? "s" : ""));
        }
        
        // Update start button state
        if (startButton != null) {
            startButton.setDisable(queuedFiles.isEmpty() || isProcessing);
        }
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
    
    private void setupUIBindings() {
        // Initialize log level combo box
        if (logLevelComboBox != null) {
            logLevelComboBox.setItems(FXCollections.observableArrayList("All", "Info", "Warning", "Error"));
            logLevelComboBox.setValue("All");
        }
        
        // Update start button based on queue state
        queuedFiles.addListener((javafx.collections.ListChangeListener<ConversionJob>) c -> {
            updateQueueCounts();
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
        int skipped = 0;
        for (File file : files) {
            // Check if already in any queue
            boolean alreadyInQueue = queuedFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath())) ||
                processingFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath())) ||
                completedFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath()));
            
            if (!alreadyInQueue) {
                ConversionJob job = new ConversionJob(file.getAbsolutePath());
                job.setOutputFormat("MP4"); // Default
                job.setStatus("⏳ Queued");
                queuedFiles.add(job);
                added++;
            } else {
                skipped++;
            }
        }
        
        updateQueueCounts();
        updateSelectedFilesLabel();
        
        // Update subtitle file list if in subtitle mode
        if ("subtitle".equals(currentMode)) {
            updateSubtitleFileList();
        }
        
        // Update metadata/renamer file list if in renamer mode
        if ("renamer".equals(currentMode)) {
            showOriginalFilenames();
        }
        
        if (added > 0) {
            log("Added " + added + " file(s) to queue" + (skipped > 0 ? " (" + skipped + " duplicate(s) skipped)" : ""));
        } else if (skipped > 0) {
            log("Skipped " + skipped + " duplicate file(s) - already in queue");
        }
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
        if (queuedFiles.isEmpty() || isProcessing) {
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
        
        // Get files from queued list
        List<String> filePaths = new ArrayList<>();
        List<ConversionJob> jobsToProcess = new ArrayList<>();
        
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
            job.setStatus("⚡ Processing");
            job.setOperation("Starting...");
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
    
    private void performRename(List<String> filePaths) {
        isProcessing = true;
        log("Renaming files...");
        
        log("Starting rename process for " + filePaths.size() + " files");
        for (int i = 0; i < filePaths.size(); i++) {
            log("File " + (i + 1) + ": " + filePaths.get(i));
        }
        
        new Thread(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("action", "rename_files");
                com.google.gson.JsonArray filesArray = new com.google.gson.JsonArray();
                filePaths.forEach(filesArray::add);
                request.add("file_paths", filesArray);
                request.addProperty("dry_run", false);
                request.addProperty("create_backup", createBackupCheck.isSelected());
                
                JsonObject response = pythonBridge.sendCommand(request);
                
                log("Python response received: " + response.toString());
                
                Platform.runLater(() -> {
                    if (response.get("status").getAsString().equals("success")) {
                        int successCount = response.get("renamed").getAsInt();
                        int totalCount = response.get("total").getAsInt();
                        int failedCount = totalCount - successCount;
                        
                        log(String.format("Renamed %d file(s), %d failed", successCount, failedCount));
                        
                        // Update queue with new file paths
                        JsonArray results = response.getAsJsonArray("results");
                        for (int i = 0; i < results.size(); i++) {
                            JsonObject result = results.get(i).getAsJsonObject();
                            String originalPath = result.get("original").getAsString();
                            String newPath = result.get("new_path").getAsString();
                            boolean success = result.get("success").getAsBoolean();
                            
                            // Find and update the corresponding job
                            for (ConversionJob job : queuedFiles) {
                                if (job.getInputPath().equals(originalPath)) {
                                    if (success && newPath != null && !newPath.isEmpty()) {
                                        // Update the job with the new path
                                        job.inputPathProperty().set(newPath);
                                        // Update the filename to match the new path
                                        File newFile = new File(newPath);
                                        job.fileNameProperty().set(newFile.getName());
                                        job.setStatus("✅ Renamed");
                                    } else {
                                        job.setStatus("❌ Rename Failed");
                                    }
                                    break;
                                }
                            }
                        }
                        
                        queuedTable.refresh();
                        
                        // Clear the rename preview lists since files have been renamed
                        if (suggestedNamesListView != null) {
                            suggestedNamesListView.getItems().clear();
                        }
                        if (originalNamesListView != null) {
                            originalNamesListView.getItems().clear();
                        }
                        
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
    
    private void performJavaRename() {
        isProcessing = true;
        log("Renaming files using Java implementation...");
        
        new Thread(() -> {
            try {
                List<String> backupData = new ArrayList<>();
                int successCount = 0;
                int failedCount = 0;
                
                // Get the suggested names from the UI
                ObservableList<String> suggestedNames = suggestedNamesListView.getItems();
                
                if (suggestedNames.size() != queuedFiles.size()) {
                    Platform.runLater(() -> {
                        showError("Rename Error", "Mismatch between files and suggested names. Please refresh the rename preview.");
                        isProcessing = false;
                        resetProcessingState();
                    });
                    return;
                }
                
                log("Starting rename process for " + queuedFiles.size() + " files");
                
                for (int i = 0; i < queuedFiles.size(); i++) {
                    ConversionJob job = queuedFiles.get(i);
                    String originalPath = job.getInputPath();
                    String suggestedName = suggestedNames.get(i);
                    
                    try {
                        File originalFile = new File(originalPath);
                        if (!originalFile.exists()) {
                            log("❌ File not found: " + originalFile.getName());
                            failedCount++;
                            continue;
                        }
                        
                        // Extract extension from original file
                        String extension = "";
                        String originalName = originalFile.getName();
                        int lastDot = originalName.lastIndexOf('.');
                        if (lastDot > 0) {
                            extension = originalName.substring(lastDot);
                        }
                        
                        // Create new file path - check if suggested name already has extension
                        String finalFileName;
                        if (suggestedName.toLowerCase().endsWith(extension.toLowerCase())) {
                            // Suggested name already includes the extension
                            finalFileName = suggestedName;
                        } else {
                            // Add the extension to the suggested name
                            finalFileName = suggestedName + extension;
                        }
                        
                        File newFile = new File(originalFile.getParent(), finalFileName);
                        
                        // Check if file already has the correct name
                        if (originalFile.getName().equals(newFile.getName())) {
                            log("✅ File already correctly named: " + originalFile.getName());
                            successCount++;
                            continue;
                        }
                        
                        // Check if target file already exists
                        if (newFile.exists()) {
                            log("❌ Target file already exists: " + newFile.getName());
                            failedCount++;
                            continue;
                        }
                        
                        // Store backup information
                        if (createBackupCheck.isSelected()) {
                            backupData.add(String.format("%s -> %s", originalPath, newFile.getAbsolutePath()));
                        }
                        
                        // Perform the rename
                        boolean renamed = originalFile.renameTo(newFile);
                        
                        if (renamed) {
                            log("✅ Renamed: " + originalFile.getName() + " -> " + newFile.getName());
                            successCount++;
                            
                            // Update the job with new path on JavaFX thread
                            final String newPath = newFile.getAbsolutePath();
                            final String newFileName = newFile.getName();
                            Platform.runLater(() -> {
                                job.inputPathProperty().set(newPath);
                                job.fileNameProperty().set(newFileName);
                                job.setStatus("✅ Renamed");
                            });
                        } else {
                            log("❌ Failed to rename: " + originalFile.getName());
                            failedCount++;
                            Platform.runLater(() -> job.setStatus("❌ Rename Failed"));
                        }
                        
                    } catch (Exception e) {
                        log("❌ Error renaming file: " + e.getMessage());
                        failedCount++;
                        Platform.runLater(() -> job.setStatus("❌ Rename Error"));
                    }
                }
                
                // Create backup file if requested
                if (createBackupCheck.isSelected() && !backupData.isEmpty()) {
                    try {
                        createBackupFile(backupData);
                        log("📝 Backup file created");
                    } catch (Exception e) {
                        log("⚠️ Failed to create backup file: " + e.getMessage());
                    }
                }
                
                final int finalSuccessCount = successCount;
                final int finalFailedCount = failedCount;
                
                Platform.runLater(() -> {
                    // Refresh the table to show updated file names
                    queuedTable.refresh();
                    
                    // Update the original names list to show the new names, keep suggested names
                    originalNamesListView.getItems().clear();
                    for (ConversionJob job : queuedFiles) {
                        originalNamesListView.getItems().add(job.getFileName());
                    }
                    
                    log(String.format("✅ Rename complete: %d successful, %d failed", finalSuccessCount, finalFailedCount));
                    
                    showInfo("Rename Complete", 
                        String.format("Successfully renamed %d file(s).\n%d file(s) could not be renamed.", 
                            finalSuccessCount, finalFailedCount));
                    
                    isProcessing = false;
                    resetProcessingState();
                });
                
            } catch (Exception e) {
                logger.error("Error during Java renaming", e);
                Platform.runLater(() -> {
                    showError("Rename Error", "Failed to rename files: " + e.getMessage());
                    isProcessing = false;
                    resetProcessingState();
                });
            }
        }).start();
    }
    
    private void createBackupFile(List<String> backupData) throws IOException {
        // Create backup directory in AppData/Local/EncodeForge/backups
        Path backupDir = PathManager.getBackupsDir();
        
        // Create backup file with timestamp
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupFile = backupDir.resolve("rename_backup_" + timestamp + ".txt");
        
        // Write backup data
        List<String> lines = new ArrayList<>();
        lines.add("# EncodeForge Rename Backup - " + java.time.LocalDateTime.now());
        lines.add("# Original Path -> New Path");
        lines.add("");
        lines.addAll(backupData);
        
        Files.write(backupFile, lines, StandardCharsets.UTF_8);
        log("📝 Backup saved to: " + backupFile.toString());
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
                for (ConversionJob job : processingFiles) {
                    job.setStatus("⏸️ Paused");
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
        
        // Create styled content
        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
        
        javafx.scene.text.Text warningIcon = new javafx.scene.text.Text("⚠");
        warningIcon.setStyle("-fx-fill: #ffb900; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        javafx.scene.text.Text warningText = new javafx.scene.text.Text("This will cancel all ongoing operations. Progress will be lost.");
        warningText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
        warningText.setWrappingWidth(400);
        
        contentBox.getChildren().addAll(warningIcon, warningText);
        confirm.getDialogPane().setContent(contentBox);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = confirm.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            log("Stopping operations...");
            
            // Best-effort cancel in backend
            try {
                logger.info("Sending stop conversion command to Python bridge...");
                pythonBridge.requestStopConversion();
                logger.info("Stop conversion command sent successfully");
            } catch (Exception e) {
                logger.error("Stop command failed: {}", e.getMessage(), e);
            }

            // Already updated in the earlier cancel logic
            
            resetProcessingState();
            queuedTable.refresh();
            
            showInfo("Stopped", "All operations have been cancelled.");
        }
    }
    
    @FXML
    private void handleRemoveQueued() {
        ObservableList<ConversionJob> selectedJobs = queuedTable.getSelectionModel().getSelectedItems();
        
        if (selectedJobs.isEmpty()) {
            showWarning("No Selection", "Please select one or more files to remove.");
            return;
        }
        
        List<ConversionJob> jobsToRemove = new ArrayList<>(selectedJobs);
        queuedFiles.removeAll(jobsToRemove);
        updateQueueCounts();
        
        log("Removed " + jobsToRemove.size() + " file(s) from queue");
    }
    
    @FXML
    private void handleRemoveCompleted() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            completedFiles.remove(selected);
            updateQueueCounts();
            log("Removed completed file from list");
        }
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
            dialogStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            dialogStage.setTitle("Settings - EncodeForge");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(settingsButton.getScene().getWindow());
            dialogStage.setScene(scene);
            
            controller.setDialogStage(dialogStage);
            controller.setSettings(settings);
            controller.setPythonBridge(pythonBridge);
            controller.setDependencyManager(dependencyManager);  // Pass DependencyManager for FFmpeg detection
            controller.setStatusManager(statusManager);  // Pass StatusManager for status display
            
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
                // Refresh all provider status after settings change
                CompletableFuture.runAsync(this::updateAllStatus);
            }
            
        } catch (IOException e) {
            logger.error("Error opening settings dialog", e);
            showError("Error", "Failed to open settings: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleClearQueued() {
        if (!isProcessing && !queuedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear Queued");
            alert.setHeaderText("Clear all queued files?");
            alert.setContentText("This will remove all queued files.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                queuedFiles.clear();
                updateQueueCounts();
                log("Queued files cleared");
            }
        }
    }
    
    @FXML
    private void handleClearCompleted() {
        if (!completedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear Completed");
            alert.setHeaderText("Clear all completed files?");
            alert.setContentText("This will remove all completed files from the list.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                completedFiles.clear();
                updateQueueCounts();
                log("Completed files cleared");
            }
        }
    }
    
    @FXML
    private void handleMoveUpQueued() {
        int index = queuedTable.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            ConversionJob job = queuedFiles.remove(index);
            queuedFiles.add(index - 1, job);
            queuedTable.getSelectionModel().select(index - 1);
        }
    }
    
    @FXML
    private void handleMoveDownQueued() {
        int index = queuedTable.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < queuedFiles.size() - 1) {
            ConversionJob job = queuedFiles.remove(index);
            queuedFiles.add(index + 1, job);
            queuedTable.getSelectionModel().select(index + 1);
        }
    }
    
    @FXML
    private void handleOpenFileLocationQueued() {
        ConversionJob selected = queuedTable.getSelectionModel().getSelectedItem();
        openFileLocation(selected);
    }
    
    @FXML
    private void handleOpenFileLocationCompleted() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        openFileLocation(selected);
    }
    
    @FXML
    private void handleOpenOutputFile() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getOutputPath() != null) {
            try {
                File file = new File(selected.getOutputPath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file);
                } else {
                    showWarning("File Not Found", "Output file does not exist: " + file.getName());
                }
            } catch (IOException e) {
                logger.error("Error opening output file", e);
                showError("Error", "Could not open output file: " + e.getMessage());
            }
        }
    }
    
    private void openFileLocation(ConversionJob job) {
        if (job != null) {
            try {
                File file = new File(job.getInputPath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                }
            } catch (IOException e) {
                logger.error("Error opening file location", e);
                showError("Error", "Could not open file location: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleClearLogs() {
        // Clear all three log areas so they stay synchronized
        if (logTextArea != null) {
        logTextArea.clear();
        }
        if (subtitleLogArea != null) {
            subtitleLogArea.clear();
        }
        if (renamerLogArea != null) {
            renamerLogArea.clear();
        }
        log("Logs cleared");
    }
    
    @FXML
    private void handleExportLogs() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Logs");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        chooser.setInitialFileName("encodeforge-logs.txt");
        
        // Determine which log area to export based on visible window
        TextArea activeLogArea = logTextArea;
        if (currentMode.equals("subtitle") && subtitleLogArea != null) {
            activeLogArea = subtitleLogArea;
        } else if (currentMode.equals("renamer") && renamerLogArea != null) {
            activeLogArea = renamerLogArea;
        }
        
        File file = chooser.showSaveDialog(activeLogArea.getScene().getWindow());
        if (file != null) {
            try {
                // All log areas have the same content, so just export the active one
                java.nio.file.Files.writeString(file.toPath(), activeLogArea.getText());
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
        CompletableFuture.runAsync(this::updateAllStatus);
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
    private void handleCheckForUpdates() {
        com.encodeforge.util.UpdateChecker.checkAndShowUpdateDialog();
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About EncodeForge");
        alert.setHeaderText(null);
        
        // Create styled content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12);
        content.setPadding(new javafx.geometry.Insets(10));
        
        // App name
        javafx.scene.text.Text appName = new javafx.scene.text.Text("EncodeForge");
        appName.setStyle("-fx-fill: #16c60c; -fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Version
        javafx.scene.text.Text versionText = new javafx.scene.text.Text("Version 0.3.1");
        versionText.setStyle("-fx-fill: #4ec9b0; -fx-font-size: 14px;");
        
        // Description
        javafx.scene.text.Text descriptionText = new javafx.scene.text.Text(
            "A modern, cross-platform media encoding, subtitle management, and file renaming tool.\n\n" +
            "Features:\n" +
            "• Hardware-accelerated video encoding (NVENC, QuickSync, VideoToolbox)\n" +
            "• AI-powered subtitle generation with OpenAI Whisper\n" +
            "• Multi-provider subtitle download and synchronization\n" +
            "• Automated metadata retrieval and file renaming\n" +
            "• Batch processing with customizable profiles\n" +
            "• Support for HDR, HDR10+, Dolby Vision"
        );
        descriptionText.setStyle("-fx-fill: #cccccc; -fx-font-size: 12px;");
        descriptionText.setWrappingWidth(450);
        
        // Separator
        javafx.scene.control.Separator separator = new javafx.scene.control.Separator();
        
        // Links
        javafx.scene.layout.VBox linksBox = new javafx.scene.layout.VBox(6);
        
        javafx.scene.control.Hyperlink githubLink = new javafx.scene.control.Hyperlink("🔗 GitHub Repository");
        githubLink.setStyle("-fx-text-fill: #0078d4; -fx-font-size: 12px;");
        githubLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/SirStig/EncodeForge"));
            } catch (Exception ex) {
                logger.error("Error opening GitHub URL", ex);
            }
        });
        
        javafx.scene.control.Hyperlink issuesLink = new javafx.scene.control.Hyperlink("🐛 Report an Issue");
        issuesLink.setStyle("-fx-text-fill: #0078d4; -fx-font-size: 12px;");
        issuesLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/SirStig/EncodeForge/issues"));
            } catch (Exception ex) {
                logger.error("Error opening Issues URL", ex);
            }
        });
        
        linksBox.getChildren().addAll(githubLink, issuesLink);
        
        // Copyright
        javafx.scene.text.Text copyrightText = new javafx.scene.text.Text("© 2025 EncodeForge. Licensed under MIT License.");
        copyrightText.setStyle("-fx-fill: #858585; -fx-font-size: 10px;");
        
        content.getChildren().addAll(appName, versionText, descriptionText, separator, linksBox, copyrightText);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(500);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
        alert.showAndWait();
    }
    
    @FXML
    private void handleExit() {
        if (isProcessing) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit");
            alert.setHeaderText("Conversions in progress");
            
            // Create styled content
            javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
            
            javafx.scene.text.Text warningIcon = new javafx.scene.text.Text("⚠");
            warningIcon.setStyle("-fx-fill: #ffb900; -fx-font-size: 16px; -fx-font-weight: bold;");
            
            javafx.scene.text.Text warningText = new javafx.scene.text.Text("Are you sure you want to exit?");
            warningText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
            warningText.setWrappingWidth(400);
            
            contentBox.getChildren().addAll(warningIcon, warningText);
            alert.getDialogPane().setContent(contentBox);
            
            // Style the dialog
            javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }
        
        Platform.exit();
    }
    
    // ========================================
    // ADDITIONAL MENU HANDLERS
    // ========================================
    
    @FXML
    private void handleClearQueue() {
        if (!queuedFiles.isEmpty() || !processingFiles.isEmpty() || !completedFiles.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Clear Queue");
            confirm.setHeaderText("Clear all items from queue?");
            confirm.setContentText("This will remove all items from all queues (queued, processing, and completed).");
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                queuedFiles.clear();
                processingFiles.clear();
                completedFiles.clear();
                updateQueueCounts();
                logger.info("All queues cleared");
            }
        }
    }
    
    @FXML
    private void handleRemoveSelected() {
        // Remove from whichever table has selection
        ObservableList<ConversionJob> selectedQueued = queuedTable.getSelectionModel().getSelectedItems();
        ObservableList<ConversionJob> selectedProcessing = processingTable.getSelectionModel().getSelectedItems();
        ObservableList<ConversionJob> selectedCompleted = completedTable.getSelectionModel().getSelectedItems();
        
        int removed = 0;
        if (!selectedQueued.isEmpty()) {
            queuedFiles.removeAll(new java.util.ArrayList<>(selectedQueued));
            removed += selectedQueued.size();
        }
        if (!selectedProcessing.isEmpty()) {
            processingFiles.removeAll(new java.util.ArrayList<>(selectedProcessing));
            removed += selectedProcessing.size();
        }
        if (!selectedCompleted.isEmpty()) {
            completedFiles.removeAll(new java.util.ArrayList<>(selectedCompleted));
            removed += selectedCompleted.size();
        }
        
        if (removed > 0) {
            updateQueueCounts();
            logger.info("Removed " + removed + " selected items");
        }
    }
    
    @FXML
    private void handleOpenOutputFolder() {
        String outputPath = settings.getOutputDirectory();
        if (outputPath == null || outputPath.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Output Folder");
            alert.setHeaderText("Output folder not configured");
            alert.setContentText("Please configure an output folder in Settings.");
            alert.showAndWait();
            return;
        }
        
        try {
            java.awt.Desktop.getDesktop().open(new java.io.File(outputPath));
        } catch (Exception e) {
            logger.error("Error opening output folder", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not open output folder");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
    @FXML
    private void handleSelectAll() {
        queuedTable.getSelectionModel().selectAll();
        processingTable.getSelectionModel().selectAll();
        completedTable.getSelectionModel().selectAll();
    }
    
    @FXML
    private void handleDeselectAll() {
        queuedTable.getSelectionModel().clearSelection();
        processingTable.getSelectionModel().clearSelection();
        completedTable.getSelectionModel().clearSelection();
    }
    
    @FXML
    private void handleExtractSubtitles() {
        // Switch to subtitle mode
        handleSubtitleMode();
    }
    
    @FXML
    private void handleOpenLogsFolder() {
        try {
            java.io.File logsDir = new java.io.File("EncodeForge/logs");
            if (!logsDir.exists()) {
                logsDir = new java.io.File("logs");
            }
            if (logsDir.exists()) {
                java.awt.Desktop.getDesktop().open(logsDir);
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Logs Folder");
                alert.setHeaderText("Logs folder not found");
                alert.setContentText("The logs folder could not be located.");
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Error opening logs folder", e);
        }
    }
    
    @FXML
    private void handleOpenSettingsFolder() {
        try {
            java.io.File settingsDir = com.encodeforge.util.PathManager.getBaseDir().toFile();
            if (settingsDir.exists()) {
                java.awt.Desktop.getDesktop().open(settingsDir);
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Settings Folder");
                alert.setHeaderText("Settings folder not found");
                alert.setContentText("The settings folder could not be located.");
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Error opening settings folder", e);
        }
    }
    
    @FXML
    private void handleViewLogs() {
        try {
            java.io.File logFile = new java.io.File("EncodeForge/logs/encodeforge.log");
            if (!logFile.exists()) {
                logFile = new java.io.File("logs/encodeforge.log");
            }
            if (logFile.exists()) {
                java.awt.Desktop.getDesktop().open(logFile);
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("View Logs");
                alert.setHeaderText("Log file not found");
                alert.setContentText("The log file could not be located.");
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Error opening log file", e);
        }
    }
    
    @FXML
    private void handleReportIssue() {
        try {
            java.awt.Desktop.getDesktop().browse(
                new java.net.URI("https://github.com/SirStig/EncodeForge/issues/new")
            );
        } catch (Exception e) {
            logger.error("Error opening issue reporter", e);
        }
    }
    
    // ========================================
    // END ADDITIONAL MENU HANDLERS
    // ========================================
    
    private void handleProgressUpdate(JsonObject update) {
        Platform.runLater(() -> {
            // Handle conversion completion message
            if (update.has("type") && "conversion_complete".equals(update.get("type").getAsString())) {
                handleConversionComplete(update);
                return;
            }
            
            // Handle progress updates (both old format and new format)
            if (update.has("progress") || (update.has("type") && "progress".equals(update.get("type").getAsString()))) {
                double progress = update.get("progress").getAsDouble();
                String fileName = update.get("file").getAsString();
                String status = update.has("status") ? update.get("status").getAsString() : "processing";
                
                // Progress is now displayed in the table, not separate UI elements
                
                // Build detailed status text with all metrics
                StringBuilder statusText = new StringBuilder();
                
                // Status with emoji
                switch (status.toLowerCase()) {
                    case "analyzing":
                        statusText.append("🔍 Analyzing...");
                        break;
                    case "analyzing_subtitles":
                        statusText.append("🔍 Analyzing subtitles...");
                        break;
                    case "converting_subtitles":
                        statusText.append("📝 Converting subtitles...");
                        break;
                    case "preparing":
                        statusText.append("⚙️ Preparing...");
                        break;
                    case "ready":
                        statusText.append("⚡ Starting encoding...");
                        break;
                    case "progress":
                        statusText.append("⚡ Encoding...");
                        break;
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
                    case "complete":
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
                    .filter(j -> new File(j.getInputPath()).getName().equals(fileName))
                    .findFirst()
                    .orElse(null);
                
                if (job != null) {
                    String lowerStatus = status.toLowerCase();
                    // Update job status based on the new status types
                    switch (lowerStatus) {
                        case "analyzing":
                            job.setStatus("Analyzing...");
                            job.setOperation("Analyzing video file...");
                            job.setProgress(1.0); // Fixed progress for analyzing phase
                            job.setFps("0");
                            job.setSpeed("0x");
                            job.setEta("Analyzing...");
                            break;
                        case "analyzing_subtitles":
                            job.setStatus("Analyzing subtitles...");
                            job.setOperation("Analyzing subtitle tracks...");
                            job.setProgress(2.0); // Fixed progress for subtitle analysis
                            job.setFps("0");
                            job.setSpeed("0x");
                            job.setEta("Analyzing...");
                            break;
                        case "converting_subtitles":
                            job.setStatus("Converting subtitles...");
                            job.setOperation("Converting subtitles...");
                            job.setProgress(3.0); // Fixed progress for subtitle conversion
                            job.setFps("0");
                            job.setSpeed("0x");
                            job.setEta("Converting...");
                            break;
                        case "preparing":
                            job.setStatus("Preparing...");
                            job.setOperation("Preparing conversion...");
                            job.setProgress(4.0); // Fixed progress for preparation phase
                            job.setFps("0");
                            job.setSpeed("0x");
                            job.setEta("Preparing...");
                            break;
                        case "ready":
                            job.setStatus("Starting encoding...");
                            job.setOperation("Starting encoding...");
                            job.setProgress(5.0); // Fixed progress for ready phase
                            job.setFps("0");
                            job.setSpeed("0x");
                            job.setEta("Starting...");
                            break;
                        case "progress":
                            job.setStatus("Encoding...");
                            job.setOperation("Encoding video...");
                            // Map backend progress (0-100) to frontend progress (6-100)
                            double mappedProgress = Math.max(6.0, Math.min(100.0, 6.0 + (progress * 0.94)));
                            job.setProgress(mappedProgress);
                            // Keep existing FPS, speed, ETA from update
                            break;
                        case "processing":
                            job.setStatus("Encoding...");
                            job.setOperation("Encoding video...");
                            // Map backend progress (0-100) to frontend progress (6-100)
                            mappedProgress = Math.max(6.0, Math.min(100.0, 6.0 + (progress * 0.94)));
                            job.setProgress(mappedProgress);
                            // Keep existing FPS, speed, ETA from update
                            break;
                        default:
                            logger.debug("Unhandled status: '{}' - updating progress only", status);
                            // For other statuses, update progress normally
                            if (progress >= 0) {
                                job.setProgress(progress);
                            }
                    }
                    
                    // Update real-time metrics for encoding phase
                    if (update.has("fps")) {
                        job.setFps(String.format("%.1f", update.get("fps").getAsDouble()));
                    }
                    if (update.has("speed")) {
                        job.setSpeed(update.get("speed").getAsString());
                    }
                    if (update.has("eta")) {
                        job.setEta(update.get("eta").getAsString());
                    }
                    
                    // Refresh the table to show updated values
                    processingTable.refresh();
                    
                    // Handle completion - move to completed list
                    if (status.equals("completed") || status.equals("complete")) {
                        job.setStatus("✅ Completed");
                        job.setOperation("Completed");
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
    
    private void handleConversionComplete(JsonObject result) {
        String status = result.get("status").getAsString();
        String message = result.get("message").getAsString();
        
        log("Conversion completed: " + message);
        
        if ("success".equals(status)) {
            // Move all processing jobs to completed
            for (ConversionJob job : new ArrayList<>(processingFiles)) {
                job.setStatus("✅ Completed");
                job.setProgress(100.0);
                
                // Calculate time taken
                long elapsedMs = System.currentTimeMillis() - job.getStartTime();
                long minutes = elapsedMs / 60000;
                long seconds = (elapsedMs % 60000) / 1000;
                job.setEta(String.format("%dm %ds", minutes, seconds));
                
                processingFiles.remove(job);
                completedFiles.add(job);
            }
            
            updateQueueCounts();
            showInfo("Conversion Complete", message);
        } else {
            // Handle error - move jobs back to queue
            for (ConversionJob job : new ArrayList<>(processingFiles)) {
                job.setStatus("⚠️ Failed");
                job.setProgress(0.0);
                processingFiles.remove(job);
                queuedFiles.add(job);
            }
            
            updateQueueCounts();
            showError("Conversion Error", message);
        }
        
        resetProcessingState();
    }
    
    private void resetProcessingState() {
        isProcessing = false;
        updateQueueCounts();
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        statusLabel.setText("Ready");
    }
    
    /**
     * Consolidated status check for all providers and services.
     * Uses StatusManager to cache results and avoid redundant API calls.
     * FFmpeg detection now uses Java-side DependencyManager instead of Python.
     */
    private void updateAllStatus() {
        try {
            logger.info("Starting consolidated status check");
            
            // First, sync settings with Python backend
            try {
                JsonObject updateSettings = new JsonObject();
                updateSettings.addProperty("action", "update_settings");
                updateSettings.add("settings", settings.toJson());
                pythonBridge.sendCommand(updateSettings);
                logger.info("Settings synchronized with Python backend");
            } catch (Exception e) {
                logger.error("Failed to sync settings with Python backend", e);
            }
            
            // Check FFmpeg using Java DependencyManager (not Python)
            boolean ffmpegAvailable = false;
            String ffmpegVersion = "Not Found";
            try {
                ffmpegAvailable = dependencyManager.checkFFmpeg().get();
                if (ffmpegAvailable) {
                    java.nio.file.Path ffmpegPath = dependencyManager.getInstalledFFmpegPath();
                    if (ffmpegPath != null) {
                        ffmpegVersion = "Found";
                        logger.info("FFmpeg detected via DependencyManager at: {}", ffmpegPath);
                    } else {
                        // FFmpeg found in system PATH
                        ffmpegVersion = "System PATH";
                        logger.info("FFmpeg detected in system PATH");
                    }
                }
            } catch (Exception e) {
                logger.error("Error checking FFmpeg via DependencyManager", e);
            }
            
            // Get Python backend status (Whisper, providers, etc)
            JsonObject response = pythonBridge.getAllStatus();
            
            if (response.has("status") && response.get("status").getAsString().equals("error")) {
                logger.error("Error getting Python status: " + response.get("message").getAsString());
            }
            
            // Update FFmpeg info in response (override Python's check with Java's)
            if (response.has("ffmpeg")) {
                JsonObject ffmpegInfo = response.getAsJsonObject("ffmpeg");
                ffmpegInfo.addProperty("available", ffmpegAvailable);
                if (ffmpegAvailable && ffmpegInfo.has("version")) {
                    ffmpegVersion = ffmpegInfo.get("version").getAsString();
                }
            }
            
            // Update StatusManager with the consolidated response
            com.encodeforge.util.StatusManager.getInstance().updateFromResponse(response);
            
            // Update FFmpeg status separately (using Java check)
            final boolean finalFfmpegAvailable = ffmpegAvailable;
            final String finalFfmpegVersion = ffmpegVersion;
            
            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                updateFFmpegStatus(finalFfmpegAvailable, finalFfmpegVersion);
                updateUIFromStatus();
            });
            
            logger.info("Consolidated status check completed successfully");
            
        } catch (IOException | TimeoutException e) {
            logger.error("Error checking status", e);
            Platform.runLater(() -> {
                log("ERROR checking status: " + e.getMessage());
                updateFFmpegStatus(false, "Error");
            });
        } catch (Exception e) {
            logger.error("Unexpected error checking status", e);
            Platform.runLater(() -> {
                log("ERROR: " + e.getMessage());
                updateFFmpegStatus(false, "Error");
            });
        }
    }
    
    /**
     * Update all UI elements based on the current StatusManager state.
     * This method should be called on the JavaFX Application thread.
     * Note: FFmpeg status is updated separately via Java DependencyManager, not here.
     */
    private void updateUIFromStatus() {
        com.encodeforge.util.StatusManager statusMgr = com.encodeforge.util.StatusManager.getInstance();
        
        // FFmpeg status is now updated separately via Java DependencyManager in updateAllStatus()
        // Don't update it here to avoid overwriting with stale Python data
        
        // Update Whisper status
        if (statusMgr.isWhisperAvailable()) {
            log("Whisper available: " + statusMgr.getWhisperVersion());
            if (whisperStatusButton != null) {
                whisperStatusButton.setText("🎤 Whisper ✓");
                whisperStatusButton.getStyleClass().removeAll("error", "warning");
                whisperStatusButton.getStyleClass().add("active");
            }
        } else {
            if (whisperStatusButton != null) {
                whisperStatusButton.setText("🎤 Whisper ✗");
                whisperStatusButton.getStyleClass().removeAll("active");
                whisperStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update OpenSubtitles status
        if (statusMgr.isOpenSubtitlesAvailable()) {
            if (openSubsStatusButton != null) {
                String statusText = statusMgr.isOpenSubtitlesLoggedIn() ? "✓ (20/day)" : "✓ (5/day)";
                openSubsStatusButton.setText("🎬 OpenSubs " + statusText);
                openSubsStatusButton.getStyleClass().removeAll("error", "warning");
                openSubsStatusButton.getStyleClass().add("active");
            }
        }
        
        // Update metadata provider status buttons
        updateMetadataProviderButtons();
        
        // Log provider counts
        int totalMetadataProviders = statusMgr.getTotalMetadataProviders();
        log("=== Metadata Providers: " + totalMetadataProviders + "/10 available ===");
        log("API Key Providers:");
        if (statusMgr.isTmdbConfigured()) log("  ✓ TMDB (Movies & TV)");
        else log("  ⚠️ TMDB (Movies & TV) - API key needed");
        if (statusMgr.isTvdbConfigured()) log("  ✓ TVDB (TV Shows)");
        else log("  ⚠️ TVDB (TV Shows) - API key needed");
        if (statusMgr.isOmdbConfigured()) log("  ✓ OMDB (Movies & TV)");
        else log("  ⚠️ OMDB (Movies & TV) - API key needed");
        if (statusMgr.isTraktConfigured()) log("  ✓ Trakt (Movies & TV)");
        else log("  ⚠️ Trakt (Movies & TV) - API key needed");
        if (statusMgr.isFanartConfigured()) log("  ✓ Fanart.tv (Artwork)");
        else log("  ⚠️ Fanart.tv (Artwork) - API key needed");
        log("Free Providers (No API Key Required):");
        log("  ✅ AniDB (Anime) - always available");
        log("  ✅ Kitsu (Anime) - always available");
        log("  ✅ Jikan/MAL (Anime) - always available");
        log("  ✅ TVmaze (TV Shows) - always available");
    }
    
    /**
     * Update metadata provider status buttons based on StatusManager
     */
    private void updateMetadataProviderButtons() {
        com.encodeforge.util.StatusManager statusMgr = com.encodeforge.util.StatusManager.getInstance();
        
        // Update TMDB button
        if (tmdbStatusButton != null) {
            if (statusMgr.isTmdbConfigured()) {
                tmdbStatusButton.setText("TMDB: Ready" + (settings.isTmdbValidated() ? " ✓" : ""));
                tmdbStatusButton.getStyleClass().removeAll("error", "warning");
                tmdbStatusButton.getStyleClass().add("active");
            } else {
                tmdbStatusButton.setText("TMDB: Not Setup");
                tmdbStatusButton.getStyleClass().removeAll("active");
                tmdbStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update TVDB button
        if (tvdbStatusButton != null) {
            if (statusMgr.isTvdbConfigured()) {
                tvdbStatusButton.setText("TVDB: Ready" + (settings.isTvdbValidated() ? " ✓" : ""));
                tvdbStatusButton.getStyleClass().removeAll("error", "warning");
                tvdbStatusButton.getStyleClass().add("active");
            } else {
                tvdbStatusButton.setText("TVDB: Not Setup");
                tvdbStatusButton.getStyleClass().removeAll("active");
                tvdbStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update OMDB button
        if (omdbStatusButton != null) {
            if (statusMgr.isOmdbConfigured()) {
                omdbStatusButton.setText("OMDB: Ready");
                omdbStatusButton.getStyleClass().removeAll("error", "warning");
                omdbStatusButton.getStyleClass().add("active");
            } else {
                omdbStatusButton.setText("OMDB: Not Setup");
                omdbStatusButton.getStyleClass().removeAll("active");
                omdbStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update Trakt button
        if (traktStatusButton != null) {
            if (statusMgr.isTraktConfigured()) {
                traktStatusButton.setText("Trakt: Ready");
                traktStatusButton.getStyleClass().removeAll("error", "warning");
                traktStatusButton.getStyleClass().add("active");
            } else {
                traktStatusButton.setText("Trakt: Not Setup");
                traktStatusButton.getStyleClass().removeAll("active");
                traktStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update Fanart button
        if (fanartStatusButton != null) {
            if (statusMgr.isFanartConfigured()) {
                fanartStatusButton.setText("Fanart: Ready");
                fanartStatusButton.getStyleClass().removeAll("error", "warning");
                fanartStatusButton.getStyleClass().add("active");
            } else {
                fanartStatusButton.setText("Fanart: Not Setup");
                fanartStatusButton.getStyleClass().removeAll("active");
                fanartStatusButton.getStyleClass().add("warning");
            }
        }
        
        // Update active provider label
        int totalProviders = statusMgr.getTotalMetadataProviders();
        if (activeProviderLabel != null) {
            activeProviderLabel.setText(totalProviders + "/10 Providers Available");
            activeProviderLabel.setStyle("-fx-text-fill: #4ec9b0; -fx-font-weight: bold;");
        }
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
            if (selectedCodec.equals("Auto (Best Available)")) {
                // Auto mode - detect and enable best available hardware encoder
                List<String> availableEncoders = HardwareDetector.getAvailableEncoderList();
                settings.setHardwareDecoding(true);
                
                // Reset all hardware flags first
                settings.setUseNvenc(false);
                settings.setUseAmf(false);
                settings.setUseQsv(false);
                settings.setUseVideotoolbox(false);
                
                // Enable the best available hardware encoder in order of preference
                if (availableEncoders.stream().anyMatch(e -> e.contains("NVENC"))) {
                    settings.setUseNvenc(true);
                    logger.info("Auto mode: Enabled NVENC hardware encoding");
                } else if (availableEncoders.stream().anyMatch(e -> e.contains("AMF"))) {
                    settings.setUseAmf(true);
                    logger.info("Auto mode: Enabled AMF hardware encoding");
                } else if (availableEncoders.stream().anyMatch(e -> e.contains("QSV"))) {
                    settings.setUseQsv(true);
                    logger.info("Auto mode: Enabled QSV hardware encoding");
                } else if (availableEncoders.stream().anyMatch(e -> e.contains("VideoToolbox"))) {
                    settings.setUseVideotoolbox(true);
                    logger.info("Auto mode: Enabled VideoToolbox hardware encoding");
                } else {
                    // No hardware encoders available, fall back to software
                    settings.setHardwareDecoding(false);
                    logger.info("Auto mode: No hardware encoders available, using software encoding");
                }
            } else if (selectedCodec.contains("NVENC")) {
                settings.setUseNvenc(true);
                settings.setUseAmf(false);
                settings.setUseQsv(false);
                settings.setUseVideotoolbox(false);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("AMF")) {
                settings.setUseNvenc(false);
                settings.setUseAmf(true);
                settings.setUseQsv(false);
                settings.setUseVideotoolbox(false);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("QSV")) {
                settings.setUseNvenc(false);
                settings.setUseAmf(false);
                settings.setUseQsv(true);
                settings.setUseVideotoolbox(false);
                settings.setHardwareDecoding(true);
            } else if (selectedCodec.contains("VideoToolbox")) {
                settings.setUseNvenc(false);
                settings.setUseAmf(false);
                settings.setUseQsv(false);
                settings.setUseVideotoolbox(true);
                settings.setHardwareDecoding(true);
            } else {
                // Software encoding
                settings.setUseNvenc(false);
                settings.setUseAmf(false);
                settings.setUseQsv(false);
                settings.setUseVideotoolbox(false);
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
    
    /**
     * Show original filenames without searching for metadata
     * Called when entering metadata mode
     */
    private void showOriginalFilenames() {
        if (originalNamesListView == null || suggestedNamesListView == null) {
            return;
        }
        
        // Clear existing lists
        originalNamesListView.getItems().clear();
        suggestedNamesListView.getItems().clear();
        
        // Show original filenames from ALL queues and leave suggested names blank
        List<ConversionJob> allFiles = getAllFiles();
        for (ConversionJob job : allFiles) {
            originalNamesListView.getItems().add(job.getFileName());
            suggestedNamesListView.getItems().add("");  // Blank until search
        }
        
        // Disable apply button until search is done
        if (applyRenameButton != null) {
            applyRenameButton.setDisable(true);
        }
        
        log("Showing " + allFiles.size() + " file(s) - click Search to fetch metadata");
    }
    
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
    }
    
    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logEntry = String.format("[%s] %s\n", timestamp, message);
            
            // Write to all three log areas so logs appear in all modes
            if (logTextArea != null) {
                logTextArea.appendText(logEntry);
            }
            if (subtitleLogArea != null) {
                subtitleLogArea.appendText(logEntry);
            }
            if (renamerLogArea != null) {
                renamerLogArea.appendText(logEntry);
            }
        });
    }
    
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        // Create styled content
        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
        
        javafx.scene.text.Text errorIcon = new javafx.scene.text.Text("✗");
        errorIcon.setStyle("-fx-fill: #d13438; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        javafx.scene.text.Text errorText = new javafx.scene.text.Text(content);
        errorText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
        errorText.setWrappingWidth(400);
        
        contentBox.getChildren().addAll(errorIcon, errorText);
        alert.getDialogPane().setContent(contentBox);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
        alert.showAndWait();
    }
    
    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        // Create styled content
        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
        
        javafx.scene.text.Text warningIcon = new javafx.scene.text.Text("⚠");
        warningIcon.setStyle("-fx-fill: #ffb900; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        javafx.scene.text.Text warningText = new javafx.scene.text.Text(content);
        warningText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
        warningText.setWrappingWidth(400);
        
        contentBox.getChildren().addAll(warningIcon, warningText);
        alert.getDialogPane().setContent(contentBox);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
        alert.showAndWait();
    }
    
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        // Create styled content
        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
        
        javafx.scene.text.Text infoIcon = new javafx.scene.text.Text("ℹ");
        infoIcon.setStyle("-fx-fill: #0078d4; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        javafx.scene.text.Text infoText = new javafx.scene.text.Text(content);
        infoText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
        infoText.setWrappingWidth(400);
        
        contentBox.getChildren().addAll(infoIcon, infoText);
        alert.getDialogPane().setContent(contentBox);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
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
            
            // Update all jobs to cancelled state (already handled earlier)
            // Processing files are moved back to queued with cancelled status
            
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
        if (queuedTable == null) {
            return;
        }
        
        queuedTable.setRowFactory(tv -> {
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
                    ConversionJob draggedJob = queuedFiles.get(draggedIndex);
                    
                    int dropIndex;
                    if (row.isEmpty()) {
                        dropIndex = queuedFiles.size() - 1;
                    } else {
                        dropIndex = row.getIndex();
                    }
                    
                    queuedFiles.remove(draggedIndex);
                    if (dropIndex > draggedIndex) {
                        queuedFiles.add(dropIndex - 1, draggedJob);
                    } else {
                        queuedFiles.add(dropIndex, draggedJob);
                    }
                    
                    queuedTable.getSelectionModel().clearSelection();
                    queuedTable.getSelectionModel().select(dropIndex);
                    
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
    
    // ========== Queue Split Pane Setup ==========
    
    private void setupQueueSplitPane() {
        if (queueSplitPane == null) {
            logger.warn("Queue split pane not found");
            return;
        }
        
        // Set initial divider positions (40% for Queued, 20% for Processing, 40% for Completed)
        Platform.runLater(() -> {
            queueSplitPane.setDividerPositions(0.4, 0.6);
        });
        
        // Add listener to save divider positions when changed (for future persistence)
        queueSplitPane.getDividers().forEach(divider -> {
            divider.positionProperty().addListener((obs, oldPos, newPos) -> {
                // Positions could be saved to settings here if needed
            });
        });
        
        logger.info("Queue split pane configured with resizable sections");
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
                "Automatic", "TMDB", "TVDB", "OMDB", "Trakt", "AniDB", "Kitsu", "Jikan/MAL", "TVmaze"
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
        // Initialize rename preview - starts empty, will be populated with providers that have results
        if (previewProviderCombo != null) {
            previewProviderCombo.setItems(FXCollections.observableArrayList());
            previewProviderCombo.setPromptText("Search to see provider results");
            // Add listener to switch between provider results
            previewProviderCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty()) {
                    switchProviderView(newVal);
                }
            });
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
    
    // Store results per provider for comparison view
    private java.util.Map<String, java.util.List<String>> providerResults = new java.util.HashMap<>();
    
    /**
     * Store raw metadata for re-formatting when patterns change
     */
    private com.google.gson.JsonArray lastMetadataArray = null;
    private com.google.gson.JsonObject lastProviderMetadata = null;
    
    /**
     * Format metadata using user's pattern from settings
     */
    private String formatMetadata(com.google.gson.JsonObject metadata, ConversionJob job) {
        if (metadata == null || metadata.isJsonNull() || metadata.entrySet().isEmpty()) {
            return "";
        }
        
        try {
            // Log metadata for debugging
            logger.debug("Formatting metadata: " + metadata.toString());
            // Determine which pattern to use based on metadata content
            String pattern = "";
            String fileExtension = "";
            
            if (job != null) {
                String fileName = job.getFileName();
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    fileExtension = fileName.substring(lastDot);
                }
            }
            
            // Check if it's a TV show (has season & episode) or movie
            boolean isTV = metadata.has("season") && metadata.has("episode");
            boolean isMovie = metadata.has("year") && !isTV;
            
            // Determine if it's anime (could be from AniDB, Kitsu, Jikan)
            String source = metadata.has("source") ? metadata.get("source").getAsString() : "";
            boolean isAnime = source.equals("anidb") || source.equals("kitsu") || source.equals("jikan");
            
            // Select appropriate pattern
            // Priority: TV show pattern for anything with season/episode, then anime pattern for anime without seasons, then movie pattern
            if (isTV && settings.getTvShowPattern() != null && !settings.getTvShowPattern().isEmpty()) {
                pattern = settings.getTvShowPattern();
            } else if (isAnime && !isTV && settings.getAnimePattern() != null && !settings.getAnimePattern().isEmpty()) {
                // Only use anime pattern if it's anime but doesn't have season/episode info
                pattern = settings.getAnimePattern();
            } else if (isMovie && settings.getMoviePattern() != null && !settings.getMoviePattern().isEmpty()) {
                pattern = settings.getMoviePattern();
            } else {
                // Fallback to default pattern
                pattern = "{title} - S{season}E{episode} - {episodeTitle}";
            }
            
            // Replace tokens with metadata values
            String formatted = pattern;
            
            // {title} or {show_title} - TV show/anime title
            if (metadata.has("show_title")) {
                formatted = formatted.replace("{title}", metadata.get("show_title").getAsString());
            } else if (metadata.has("title")) {
                formatted = formatted.replace("{title}", metadata.get("title").getAsString());
            } else {
                formatted = formatted.replace("{title}", "Unknown");
            }
            
            // {season} - Season number
            if (metadata.has("season") && !metadata.get("season").isJsonNull()) {
                int season = metadata.get("season").getAsInt();
                formatted = formatted.replace("{season}", String.format("%02d", season));
                formatted = formatted.replace("{s}", String.format("%02d", season));
                formatted = formatted.replace("{S}", String.format("%02d", season));
            } else {
                formatted = formatted.replace("{season}", "00");
                formatted = formatted.replace("{s}", "00");
                formatted = formatted.replace("{S}", "00");
            }
            
            // {episode} - Episode number
            if (metadata.has("episode") && !metadata.get("episode").isJsonNull()) {
                int episode = metadata.get("episode").getAsInt();
                formatted = formatted.replace("{episode}", String.format("%02d", episode));
                formatted = formatted.replace("{e}", String.format("%02d", episode));
                formatted = formatted.replace("{E}", String.format("%02d", episode));
            } else {
                formatted = formatted.replace("{episode}", "00");
                formatted = formatted.replace("{e}", "00");
                formatted = formatted.replace("{E}", "00");
            }
            
            // {episodeTitle} or {episode_title} - Episode title
            if (metadata.has("episode_title") && !metadata.get("episode_title").isJsonNull()) {
                String epTitle = metadata.get("episode_title").getAsString();
                // Skip generic "Episode X" titles - they're not real episode titles
                if (epTitle.isEmpty() || epTitle.matches("^Episode\\s+\\d+$")) {
                    formatted = formatted.replace(" - {episodeTitle}", "");  // Remove the separator too
                    formatted = formatted.replace(" - {episode_title}", "");
                    formatted = formatted.replace("{episodeTitle}", "");
                    formatted = formatted.replace("{episode_title}", "");
                } else {
                    formatted = formatted.replace("{episodeTitle}", epTitle);
                    formatted = formatted.replace("{episode_title}", epTitle);
                }
            } else {
                // No episode title available - remove it and the separator
                formatted = formatted.replace(" - {episodeTitle}", "");
                formatted = formatted.replace(" - {episode_title}", "");
                formatted = formatted.replace("{episodeTitle}", "");
                formatted = formatted.replace("{episode_title}", "");
            }
            
            // {year} - Release year
            if (metadata.has("year") && !metadata.get("year").isJsonNull()) {
                formatted = formatted.replace("{year}", metadata.get("year").getAsString());
            } else if (metadata.has("show_year") && !metadata.get("show_year").isJsonNull()) {
                formatted = formatted.replace("{year}", metadata.get("show_year").getAsString());
            } else {
                formatted = formatted.replace("{year}", "");
            }
            
            // Clean up extra spaces and dashes
            formatted = formatted.replaceAll("\\s+-\\s+$", "");  // Remove trailing " - "
            formatted = formatted.replaceAll("\\s+", " ");  // Collapse multiple spaces
            formatted = formatted.trim();
            
            // Add file extension
            if (!fileExtension.isEmpty()) {
                formatted += fileExtension;
            }
            
            return formatted;
            
        } catch (Exception e) {
            logger.error("Error formatting metadata: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Re-format existing metadata with new pattern when user changes patterns
     */
    private void reformatMetadataWithNewPattern() {
        if (lastMetadataArray == null || lastProviderMetadata == null) {
            return;
        }
        
        Platform.runLater(() -> {
            try {
                // Settings object already updated by PatternEditorController, no need to reload
                
                ObservableList<String> originalNames = FXCollections.observableArrayList();
                ObservableList<String> newNames = FXCollections.observableArrayList();
                
                int successCount = 0;
                int errorCount = 0;
                
                // Clear and populate provider results map
                providerResults.clear();
                String mainProvider = null;
                
                // Format metadata from each provider using NEW pattern
                if (lastProviderMetadata != null) {
                    for (String providerName : lastProviderMetadata.keySet()) {
                        com.google.gson.JsonArray metadataArrayForProvider = lastProviderMetadata.getAsJsonArray(providerName);
                        java.util.List<String> formattedList = new java.util.ArrayList<>();
                        
                        for (int i = 0; i < metadataArrayForProvider.size(); i++) {
                            com.google.gson.JsonObject meta = metadataArrayForProvider.get(i).getAsJsonObject();
                            ConversionJob job = i < queuedFiles.size() ? queuedFiles.get(i) : null;
                            String formatted = formatMetadata(meta, job);
                            formattedList.add(formatted);
                        }
                        
                        providerResults.put(providerName, formattedList);
                    }
                    
                    // Repopulate provider comparison dropdown
                    if (previewProviderCombo != null && !providerResults.isEmpty()) {
                        ObservableList<String> availableProviders = FXCollections.observableArrayList(providerResults.keySet());
                        previewProviderCombo.setItems(availableProviders);
                    }
                }
                
                // Re-format main list
                for (int i = 0; i < queuedFiles.size() && i < lastMetadataArray.size(); i++) {
                    ConversionJob job = queuedFiles.get(i);
                    originalNames.add(job.getFileName());
                    
                    com.google.gson.JsonElement metadataElement = lastMetadataArray.get(i);
                    
                    // Format metadata using NEW pattern from settings
                    String suggested = "";
                    if (!metadataElement.isJsonNull() && metadataElement.isJsonObject()) {
                        com.google.gson.JsonObject metadata = metadataElement.getAsJsonObject();
                        suggested = formatMetadata(metadata, job);
                    }
                    
                    if (suggested.isEmpty()) {
                        newNames.add("");
                        errorCount++;
                    } else {
                        newNames.add(suggested);
                        successCount++;
                    }
                }
                
                if (originalNamesListView != null) {
                    originalNamesListView.setItems(originalNames);
                }
                if (suggestedNamesListView != null) {
                    suggestedNamesListView.setItems(newNames);
                }
                
                // Auto-select the current provider in dropdown
                String currentProvider = previewProviderCombo != null ? previewProviderCombo.getValue() : null;
                if (currentProvider != null && providerResults.containsKey(currentProvider)) {
                    suggestedNamesListView.setItems(FXCollections.observableArrayList(providerResults.get(currentProvider)));
                }
                
                if (renameStatsLabel != null) {
                    renameStatsLabel.setText(queuedFiles.size() + " files | " + successCount + " ready | " + errorCount + " errors");
                }
                
                // Enable apply button if we have results
                if (applyRenameButton != null) {
                    applyRenameButton.setDisable(successCount == 0);
                }
                
                log("✅ Filenames re-formatted with new pattern!");
                
            } catch (Exception e) {
                logger.error("Error re-formatting metadata with new pattern", e);
                showError("Format Error", "Could not re-format filenames: " + e.getMessage());
            }
        });
    }
    
    private void switchProviderView(String provider) {
        if (providerResults.containsKey(provider) && originalNamesListView != null && suggestedNamesListView != null) {
            // Show results for selected provider
            java.util.List<String> results = providerResults.get(provider);
            suggestedNamesListView.getItems().clear();
            suggestedNamesListView.getItems().addAll(results);
            
            if (activeProviderLabel != null) {
                activeProviderLabel.setText("[" + provider + "]");
            }
            
            log("Showing results from provider: " + provider);
        }
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
        // Check if Whisper is installed
        if (!statusManager.isWhisperAvailable()) {
            // Open Whisper setup wizard
            try {
                WhisperSetupDialog setupDialog = new WhisperSetupDialog(dependencyManager);
                setupDialog.showAndWait();
                
                // Refresh status after installation
                if (setupDialog.isInstallationComplete()) {
                    // Reset Python core to detect newly installed Whisper
                    try {
                        pythonBridge.resetCore();
                        logger.info("Python core reset after Whisper installation");
                    } catch (Exception e) {
                        logger.warn("Failed to reset Python core", e);
                    }
                    
                    // Refresh status to update UI
                    CompletableFuture.runAsync(this::updateAllStatus);
                    showInfo("Whisper Installed", "AI subtitle generation is now available!");
                }
            } catch (Exception e) {
                logger.error("Failed to open Whisper setup dialog", e);
                showError("Error", "Failed to open Whisper setup: " + e.getMessage());
            }
        } else {
            // Whisper already installed, open settings to configure
        openSettings("Subtitles");
        }
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
    private void handleConfigureAniDB() {
        showInfo("AniDB", "AniDB does not require any configuration. It's ready to use!");
    }
    
    /**
     * Downloads and applies a single subtitle to a video file.
     * Reusable method to avoid code duplication between single and batch apply operations.
     * 
     * @param subtitle The subtitle item to download and apply
     * @param videoPath Path to the video file
     * @param mode Apply mode: "external", "embed", or "burn-in"
     * @param logPrefix Prefix for log messages (e.g., "  " for batch operations)
     * @return true if successful, false otherwise
     */
    private boolean downloadAndApplySubtitle(SubtitleItem subtitle, String videoPath, String mode, String logPrefix) {
        try {
            Platform.runLater(() -> log(logPrefix + "➤ Processing " + subtitle.getLanguage() + " subtitle from " + subtitle.getProvider() + "..."));
            
            // Check if manual download only
            if (subtitle.isManualDownloadOnly()) {
                Platform.runLater(() -> {
                    log(logPrefix + "⚠️ Manual download required for " + subtitle.getProvider());
                    log(logPrefix + "Please download manually from: " + subtitle.getDownloadUrl());
                });
                return false;
            }
            
            // Step 1: Download the subtitle
            Platform.runLater(() -> log(logPrefix + "⬇️  Downloading from " + subtitle.getProvider() + "..."));
            
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
                Platform.runLater(() -> log(logPrefix + "❌ Download failed: " + errorMsg));
                return false;
            }
            
            // Get the downloaded subtitle path
            if (!downloadResponse.has("subtitle_path")) {
                Platform.runLater(() -> log(logPrefix + "❌ Download response missing subtitle_path"));
                return false;
            }
            
            String subtitlePath = downloadResponse.get("subtitle_path").getAsString();
            Platform.runLater(() -> log(logPrefix + "✅ Downloaded to: " + subtitlePath));
            
            // Step 2: Apply the subtitle
            Platform.runLater(() -> log(logPrefix + "🔧 Applying subtitle in '" + mode + "' mode..."));
            
            JsonObject applyRequest = new JsonObject();
            applyRequest.addProperty("action", "apply_subtitles");
            applyRequest.addProperty("video_path", videoPath);
            applyRequest.addProperty("subtitle_path", subtitlePath);
            applyRequest.addProperty("mode", mode);
            applyRequest.addProperty("language", subtitle.getLanguage());
            
            JsonObject applyResponse = pythonBridge.sendCommand(applyRequest);
            
            if (applyResponse.has("status") && "success".equals(applyResponse.get("status").getAsString())) {
                String outputPath = applyResponse.has("output_path") ? applyResponse.get("output_path").getAsString() : "unknown";
                Platform.runLater(() -> log(logPrefix + "✅ Success: " + outputPath));
                return true;
            } else {
                String errorMsg = applyResponse.has("message") ? applyResponse.get("message").getAsString() : "Unknown error";
                Platform.runLater(() -> log(logPrefix + "❌ Failed: " + errorMsg));
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error processing subtitle", e);
            Platform.runLater(() -> log(logPrefix + "❌ Error: " + e.getMessage()));
            return false;
        }
    }
    
    @FXML
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
                // Show enhanced dialog for manual download in single-file mode
                if (subtitle.isManualDownloadOnly()) {
                    Platform.runLater(() -> {
                        showWarning("Manual Download Required",
                            "The subtitle from " + subtitle.getProvider() + " requires manual download.\n\n" +
                            "Steps:\n" +
                            "1. Visit: " + subtitle.getDownloadUrl() + "\n" +
                            "2. Download the subtitle file\n" +
                            "3. Use 'External File' option in the Subtitles tab\n" +
                            "4. Browse and select the downloaded file\n\n" +
                            "We couldn't get a direct download link for this subtitle.");
                    });
                }
                
                // Use consolidated download and apply method
                boolean success = downloadAndApplySubtitle(subtitle, finalVideoPath, finalMode, "");
                if (success) {
                    successCount++;
                } else {
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
    }
    
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
                    // Use consolidated download and apply method (with "  " prefix for batch operations)
                    boolean success = downloadAndApplySubtitle(subtitle, videoPath, finalMode, "  ");
                    if (success) {
                        fileSuccessCount++;
                    } else {
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
    
    @FXML
    private void handleConfigureSubtitleProviders() {
        handleSettings(); // Open settings dialog to API Keys section
    }
    
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
            
            // If saved, log success and re-format existing metadata
            if (controller.isSaved()) {
                log("Format patterns updated successfully");
                // Re-format existing metadata if we have it
                if (lastMetadataArray != null && lastProviderMetadata != null) {
                    log("Re-formatting filenames with new pattern...");
                    reformatMetadataWithNewPattern();
                }
            }
            
        } catch (Exception e) {
            logger.error("Error opening pattern editor", e);
            showError("Error", "Could not open pattern editor: " + e.getMessage());
        }
    }
    
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
        
        // Get selected provider (no media type - always auto-detect)
        String selectedProvider = (quickRenameProviderCombo != null) ? 
            quickRenameProviderCombo.getValue() : "Automatic";
        
        log("Provider: " + selectedProvider + " | Searching all available providers...");
        
        // Build request
        com.google.gson.JsonObject request = new com.google.gson.JsonObject();
        request.addProperty("action", "preview_rename");
        
        // Add file paths
        com.google.gson.JsonArray filePaths = new com.google.gson.JsonArray();
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
        }
        request.add("file_paths", filePaths);
        
        // Add settings including selected provider and type
        com.google.gson.JsonObject settingsDict = new com.google.gson.JsonObject();
        ConversionSettings settings = ConversionSettings.load();
        settingsDict.addProperty("tmdb_api_key", settings.getTmdbApiKey());
        settingsDict.addProperty("tvdb_api_key", settings.getTvdbApiKey());
        settingsDict.addProperty("omdb_api_key", settings.getOmdbApiKey());
        settingsDict.addProperty("trakt_api_key", settings.getTraktApiKey());
        settingsDict.addProperty("selected_provider", selectedProvider.toLowerCase());
        request.add("settings", settingsDict);
        
        // Send to Python backend asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject jsonResponse = pythonBridge.sendCommand(request);
                String status = jsonResponse.get("status").getAsString();
                
                if ("success".equals(status)) {
                // Backend now returns raw metadata instead of formatted names
                com.google.gson.JsonArray metadataArray = jsonResponse.getAsJsonArray("metadata");
                com.google.gson.JsonArray providers = jsonResponse.getAsJsonArray("providers");
                com.google.gson.JsonArray errors = jsonResponse.getAsJsonArray("errors");
                
                // Get provider metadata for comparison view
                com.google.gson.JsonObject providerMetadataJson = jsonResponse.has("provider_metadata") ? 
                    jsonResponse.getAsJsonObject("provider_metadata") : null;
                
                // Store raw metadata for future re-formatting
                lastMetadataArray = metadataArray;
                lastProviderMetadata = providerMetadataJson;
                    
                    // Populate lists
                    Platform.runLater(() -> {
                        ObservableList<String> originalNames = FXCollections.observableArrayList();
                        ObservableList<String> newNames = FXCollections.observableArrayList();
                        
                        int successCount = 0;
                        int errorCount = 0;
                        
                        // Clear and populate provider results map
                        providerResults.clear();
                        String mainProvider = null;  // Track which provider gave us the main result
                        
                        // Format metadata from each provider using user's pattern
                        if (providerMetadataJson != null) {
                            for (String providerName : providerMetadataJson.keySet()) {
                                com.google.gson.JsonArray metadataArrayForProvider = providerMetadataJson.getAsJsonArray(providerName);
                                java.util.List<String> formattedList = new java.util.ArrayList<>();
                                
                                for (int i = 0; i < metadataArrayForProvider.size(); i++) {
                                    com.google.gson.JsonObject meta = metadataArrayForProvider.get(i).getAsJsonObject();
                                    ConversionJob job = i < queuedFiles.size() ? queuedFiles.get(i) : null;
                                    String formatted = formatMetadata(meta, job);
                                    formattedList.add(formatted);
                                }
                                
                                providerResults.put(providerName, formattedList);
                            }
                            
                            // Populate provider comparison dropdown with providers that have results
                            if (previewProviderCombo != null && !providerResults.isEmpty()) {
                                ObservableList<String> availableProviders = FXCollections.observableArrayList(providerResults.keySet());
                                previewProviderCombo.setItems(availableProviders);
                                log("Provider comparison available: " + String.join(", ", availableProviders));
                            }
                        }
                        
                        for (int i = 0; i < queuedFiles.size() && i < metadataArray.size(); i++) {
                            ConversionJob job = queuedFiles.get(i);
                            originalNames.add(job.getFileName());
                            
                            com.google.gson.JsonElement metadataElement = metadataArray.get(i);
                            String provider = providers.get(i).getAsString();
                            String error = errors.get(i).getAsString();
                            
                            // Format metadata using user's pattern from settings
                            String suggested = "";
                            if (!metadataElement.isJsonNull() && metadataElement.isJsonObject()) {
                                com.google.gson.JsonObject metadata = metadataElement.getAsJsonObject();
                                suggested = formatMetadata(metadata, job);
                            }
                            
                            // Track the first successful provider (this is the one selected as best match)
                            if (mainProvider == null && !provider.equals("None") && !suggested.isEmpty()) {
                                mainProvider = provider;
                            }
                            
                            // Only show error if it truly errored (not just "metadata not found")
                            if (!error.isEmpty() && error.startsWith("❌ ERROR:")) {
                                // For actual errors, show them in red/warning style
                                newNames.add(error);
                                errorCount++;
                            } else if (suggested.isEmpty()) {
                                // No metadata found, leave blank
                                newNames.add("");
                                errorCount++;
                            } else {
                                // Valid suggestion - formatted using user's pattern
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
                        
                        // Auto-select the provider that gave us the main result
                        if (mainProvider != null && previewProviderCombo != null && providerResults.containsKey(mainProvider)) {
                            previewProviderCombo.setValue(mainProvider);
                            if (activeProviderLabel != null) {
                                activeProviderLabel.setText("[" + mainProvider + "]");
                            }
                            log("Auto-selected provider: " + mainProvider);
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
            
            // Get file paths from the queued files (suggested names correspond to queued files in order)
            List<String> filePaths = new ArrayList<>();
            for (ConversionJob job : queuedFiles) {
                filePaths.add(job.getInputPath());
            }
            
            if (filePaths.isEmpty()) {
                showWarning("No Files", "No files to rename.");
                return;
            }
            
            log("Found " + filePaths.size() + " files to rename");
            
            // Perform renaming directly in Java using already-fetched metadata
            performJavaRename();
        }
    }
    
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
    
    @FXML
    private void handleAdvancedSearch() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        log("Starting advanced subtitle search with multiple query variations...");
        searchAndDisplaySubtitles(true);  // true = use advanced search
    }
    
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
        
        // Use batch search API - Python handles parallel searching internally
        new Thread(() -> {
            try {
                // Update settings once
                JsonObject updateSettings = new JsonObject();
                updateSettings.addProperty("action", "update_settings");
                updateSettings.add("settings", settings.toJson());
                pythonBridge.sendCommand(updateSettings);
                
                // Track how many files have completed
                final int totalFilesToProcess = queuedFiles.size();
                final java.util.concurrent.atomic.AtomicInteger filesCompleted = new java.util.concurrent.atomic.AtomicInteger(0);
                
                // Build files array for batch request
                JsonArray filesArray = new JsonArray();
                java.util.Map<String, String> fileIdToName = new java.util.concurrent.ConcurrentHashMap<>();
                
                int fileId = 0;
                for (ConversionJob job : queuedFiles) {
                    String filePath = job.getInputPath();
                    String fileName = new File(filePath).getName();
                    
                    // Mark file as searching
                    subtitleSearchStatus.put(fileName, SubtitleSearchStatus.SEARCHING);
                    fileIdToName.put(String.valueOf(fileId), fileName);
                    
                    JsonObject fileInfo = new JsonObject();
                    fileInfo.addProperty("file_id", String.valueOf(fileId));
                    fileInfo.addProperty("file_name", fileName);
                    fileInfo.addProperty("video_path", filePath);
                    JsonArray langsArray = new JsonArray();
                    for (String lang : selectedLanguages) {
                        langsArray.add(lang);
                    }
                    fileInfo.add("languages", langsArray);
                    filesArray.add(fileInfo);
                    
                    fileId++;
                }
                
                Platform.runLater(() -> {
                    updateSubtitleFileList();
                    if (advancedSearch) {
                        log("🔍 Advanced Search: Using multiple query variations for better results...");
                    }
                    log("🔄 Searching " + totalFilesToProcess + " file(s) in parallel...");
                    
                    // Initialize progress bar
                    if (subtitleTotalProgressBar != null) {
                        subtitleTotalProgressBar.setProgress(0.0);
                    }
                    if (subtitleProgressLabel != null) {
                        subtitleProgressLabel.setText("0 / " + totalFilesToProcess + " files");
                    }
                    if (subtitleProgressStatusLabel != null) {
                        subtitleProgressStatusLabel.setText("Searching...");
                    }
                });
                
                // Create batch search request
                JsonObject request = new JsonObject();
                request.addProperty("action", "batch_search_subtitles");
                request.add("files", filesArray);
                
                // Send batch search with streaming callback
                pythonBridge.sendStreamingCommand(request, response -> {
                    Platform.runLater(() -> {
                        try {
                            // Check if this is a file-specific update (has file_id)
                            if (response.has("file_id")) {
                                String fid = response.get("file_id").getAsString();
                                String fileName = fileIdToName.get(fid);
                                
                                if (fileName == null) {
                                    logger.warn("Received response for unknown file_id: {}", fid);
                                    return;
                                }
                                
                                // Process progress/results for this specific file
                                processFileSearchResponse(fileName, response, filesCompleted, totalFilesToProcess);
                            } else if (response.has("complete") && response.get("complete").getAsBoolean()) {
                                // Final batch completion message
                                logger.info("Batch search completed");
                                showFinalSubtitleSearchSummary();
                            }
                        } catch (Exception e) {
                            logger.error("Error processing batch search response", e);
                        }
                    });
                });
                
            } catch (Exception e) {
                logger.error("Error in batch subtitle search", e);
                Platform.runLater(() -> log("❌ Search failed: " + e.getMessage()));
            }
        }, "SubtitleSearch").start();
    }
    
    /**
     * Process search response for a specific file from batch search
     */
    private void processFileSearchResponse(String fileName, JsonObject response, 
                                           java.util.concurrent.atomic.AtomicInteger filesCompleted,
                                           int totalFilesToProcess) {
        logger.debug("[processFileSearchResponse] Processing response for file: {}", fileName);
        logger.debug("[processFileSearchResponse] Response keys: {}", response.keySet());
        logger.debug("[processFileSearchResponse] Has subtitles: {}", response.has("subtitles"));
        logger.debug("[processFileSearchResponse] Has status: {}, value: {}", 
                    response.has("status"), response.has("status") ? response.get("status").getAsString() : "N/A");
        
        // Get or create subtitle list for this file
        ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.computeIfAbsent(
            fileName, k -> {
                logger.debug("Creating new subtitle list for file: {}", k);
                return FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
            }
        );
        
        // Check for progress updates
        if (response.has("progress")) {
            String provider = response.has("provider") ? response.get("provider").getAsString() : "Unknown";
            // Note: "provider_complete" means this provider finished, NOT the entire stream
            boolean isComplete = response.has("provider_complete") && response.get("provider_complete").getAsBoolean();
            
            // Check if this progress update includes subtitle results
            boolean hasSubtitles = response.has("subtitles") && 
                                  response.get("subtitles").isJsonArray() && 
                                  response.getAsJsonArray("subtitles").size() > 0;
            
            if (isComplete) {
                log("[" + fileName + "] ✅ " + provider + " - search complete");
                // If complete message has subtitles, process them
                if (!hasSubtitles) {
                    return;
                }
                logger.debug("Complete message includes {} subtitles for file: {}", 
                           response.getAsJsonArray("subtitles").size(), fileName);
            } else {
                log("[" + fileName + "] 🔍 Searching " + provider + "...");
                return;  // Return early for non-complete progress updates
            }
        }
        
        // Handle final results or incremental updates
        if (response.has("status")) {
            String status = response.get("status").getAsString();
            
            if ("success".equals(status) && response.has("subtitles")) {
                JsonArray subtitles = response.getAsJsonArray("subtitles");
                
                // Add subtitles to this file's list
                Set<String> seenFileIds = new HashSet<>();
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
                    logger.debug("[processFileSearchResponse] Added subtitle #{} for file {}: {} - {}", 
                               i+1, fileName, provider, language);
                }
                
                logger.debug("[processFileSearchResponse] File {} now has {} subtitles total", fileName, fileSubtitles.size());
                logger.debug("[processFileSearchResponse] subtitlesByFile total entries: {}", subtitlesByFile.size());
                
                // Update display if this is the currently selected file
                String baseCurrentFile = currentlySelectedFile != null ? extractBaseFileName(currentlySelectedFile) : null;
                if (fileName.equals(baseCurrentFile) && availableSubtitlesTable != null) {
                    availableSubtitlesTable.setItems(fileSubtitles);
                    int uniqueLangs = fileSubtitles.stream()
                        .map(SubtitleItem::getLanguage)
                        .collect(java.util.stream.Collectors.toSet())
                        .size();
                    if (subtitleStatsLabel != null) {
                        subtitleStatsLabel.setText(fileSubtitles.size() + " available | " + uniqueLangs + " language(s)");
                    }
                }
                
                // Check if this is the final result for this file
                // Note: "file_complete" means this file is done, but stream continues
                // "complete" means the entire batch is done
                boolean isFinal = (response.has("file_complete") && response.get("file_complete").getAsBoolean()) ||
                                 (response.has("complete") && response.get("complete").getAsBoolean());
                if (isFinal) {
                    int count = fileSubtitles.size();
                    
                    // Mark file as completed
                    subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                    updateSubtitleFileList();
                    
                    // Log completion
                    logger.debug("Search complete for file: {} ({} subtitles)", fileName, count);
                    log("✅ [" + fileName + "] " + count + " subtitle(s) found");
                    
                    // Check if this was the last file to complete
                    int completed = filesCompleted.incrementAndGet();
                    
                    // Update progress bar
                    if (subtitleTotalProgressBar != null) {
                        double progress = (double) completed / totalFilesToProcess;
                        subtitleTotalProgressBar.setProgress(progress);
                    }
                    if (subtitleProgressLabel != null) {
                        subtitleProgressLabel.setText(completed + " / " + totalFilesToProcess + " files");
                    }
                    
                    if (completed == totalFilesToProcess) {
                        showFinalSubtitleSearchSummary();
                    }
                }
            } else if ("error".equals(status)) {
                String errorMsg = response.has("message") ? response.get("message").getAsString() : "Search failed";
                log("❌ [" + fileName + "] " + errorMsg);
                
                // Mark as completed even on error
                subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                updateSubtitleFileList();
                
                int completed = filesCompleted.incrementAndGet();
                
                // Update progress bar
                if (subtitleTotalProgressBar != null) {
                    double progress = (double) completed / totalFilesToProcess;
                    subtitleTotalProgressBar.setProgress(progress);
                }
                if (subtitleProgressLabel != null) {
                    subtitleProgressLabel.setText(completed + " / " + totalFilesToProcess + " files");
                }
                
                if (completed == totalFilesToProcess) {
                    showFinalSubtitleSearchSummary();
                }
            }
        }
    }
    
    /**
     * Initialize subtitle progress bar to Ready state
     */
    private void setupSubtitleProgressBar() {
        if (subtitleTotalProgressBar != null) {
            subtitleTotalProgressBar.setProgress(0.0);
        }
        if (subtitleProgressLabel != null) {
            subtitleProgressLabel.setText("0 / 0 files");
        }
        if (subtitleProgressStatusLabel != null) {
            subtitleProgressStatusLabel.setText("Ready");
        }
    }
    
    /**
     * Search subtitles for a single file (called sequentially for each file)
     */
    private void searchSingleFile(String filePath, String fileName, List<String> selectedLanguages, 
                                   boolean advancedSearch, java.util.concurrent.atomic.AtomicInteger filesCompleted,
                                   int totalFilesToProcess) {
        try {
                
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
                    
                    // Check if AniDB URL is provided
                    if (anidbUrlField != null && anidbUrlField.getText() != null && !anidbUrlField.getText().trim().isEmpty()) {
                        final String anidbUrl = anidbUrlField.getText().trim();
                        request.addProperty("anidb_url", anidbUrl);
                        Platform.runLater(() -> log("Using AniDB URL: " + anidbUrl));
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
                
                // Initialize subtitle list for this file (thread-safe with computeIfAbsent)
                final ObservableList<SubtitleItem> fileSubtitles = subtitlesByFile.computeIfAbsent(
                    fileName, k -> {
                        logger.debug("Creating new subtitle list for file: {}", k);
                        return FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
                    }
                );
                
                logger.debug("Searching subtitles for: {} (list has {} existing items)", fileName, fileSubtitles.size());
                
                // Track results for deduplication (each thread gets its own set per file)
                final Set<String> seenFileIds = java.util.Collections.synchronizedSet(new HashSet<>());
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
                                    
                                    // Check if this progress update includes subtitle results
                                    boolean hasSubtitles = response.has("subtitles") && 
                                                          response.get("subtitles").isJsonArray() && 
                                                          response.getAsJsonArray("subtitles").size() > 0;
                                    
                                    if (isComplete) {
                                        log("✅ " + provider + " - search complete");
                                        // If complete message has subtitles, process them instead of returning early
                                        if (!hasSubtitles) {
                                            return;
                                        }
                                        logger.debug("Complete message includes {} subtitles - processing them now", 
                                                   response.getAsJsonArray("subtitles").size());
                                        // Fall through to process subtitles below
                                    } else {
                                        log("🔍 Searching " + provider + "...");
                                        if (subtitleStatsLabel != null) {
                                            subtitleStatsLabel.setText("Searching " + provider + " for " + fileName);
                                        }
                                        return;  // Return early for non-complete progress updates
                                    }
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
                                        
                                        if (newCount > 0) {
                                            logger.debug("Added {} subtitles to list for file: {} (total now: {})", 
                                                       newCount, fileName, fileSubtitles.size());
                                        }
                                        
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
                                            
                                            // Mark file as completed NOW (not before the search finishes)
                                            subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                                            
                                            // Log completion
                                            logger.debug("Stored {} subtitle(s) for file: {}", count, fileName);
                                            log("✅ Search complete for: " + fileName + " (" + count + " result(s))");
                                            
                                            if (count > 0) {
                                                log("Results displayed in table.");
                                            } else {
                                                log("⚠️ No subtitles found. Try AI generation instead.");
                                            }
                                            
                                            // Update file selector with completion status
                                            updateSubtitleFileList();
                                            
                                            // Check if this was the last file to complete
                                            int completed = filesCompleted.incrementAndGet();
                                            if (completed == totalFilesToProcess) {
                                                // ALL files have completed - show final summary
                                                showFinalSubtitleSearchSummary();
                                            }
                                        }
                                    } else if ("error".equals(status)) {
                                        String errorMsg = response.has("message") ? response.get("message").getAsString() : "Search failed";
                                        log("❌ Search failed: " + errorMsg);
                                        if (subtitleStatsLabel != null) {
                                            subtitleStatsLabel.setText("Search failed");
                                        }
                                        
                                        // Mark as completed even on error and check if last file
                                        subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                                        updateSubtitleFileList();
                                        
                                        int completed = filesCompleted.incrementAndGet();
                                        if (completed == totalFilesToProcess) {
                                            showFinalSubtitleSearchSummary();
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
                            
                            // Mark file as completed (fallback path)
                            subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                            updateSubtitleFileList();
                            log("✅ Search complete for: " + fileName + " (" + count + " result(s))");
                            
                            // Check if this was the last file to complete
                            int completed = filesCompleted.incrementAndGet();
                            if (completed == totalFilesToProcess) {
                                showFinalSubtitleSearchSummary();
                            }
                        });
                    } else {
                        String errorMsg = response.has("message") ? response.get("message").getAsString() : "Search failed";
                        Platform.runLater(() -> {
                            log("❌ Search failed: " + errorMsg);
                            if (subtitleStatsLabel != null) {
                                subtitleStatsLabel.setText("Search failed");
                            }
                            // Mark as completed even on error
                            subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
                            updateSubtitleFileList();
                            
                            // Check if this was the last file to complete
                            int completed = filesCompleted.incrementAndGet();
                            if (completed == totalFilesToProcess) {
                                showFinalSubtitleSearchSummary();
                            }
                        });
                    }
                }
                
                // NOTE: Completion status is now set inside the streaming callback when search actually finishes
                // This fixes the issue where status showed "completed" before search even started
                // Final summary is also shown in the callback when ALL files complete
                
        } catch (Exception e) {
            logger.error("Error searching file: " + fileName, e);
            Platform.runLater(() -> log("❌ Search error for " + fileName + ": " + e.getMessage()));
            
            // Mark as completed even on exception
            subtitleSearchStatus.put(fileName, SubtitleSearchStatus.COMPLETED);
            Platform.runLater(() -> updateSubtitleFileList());
            
            int completed = filesCompleted.incrementAndGet();
            if (completed == totalFilesToProcess) {
                Platform.runLater(() -> showFinalSubtitleSearchSummary());
            }
        }
    }
    
    /**
     * Show final summary when ALL subtitle searches have completed.
     * Called from the streaming callback when the last file finishes.
     */
    private void showFinalSubtitleSearchSummary() {
        Platform.runLater(() -> {
            // Update progress status
            if (subtitleProgressStatusLabel != null) {
                subtitleProgressStatusLabel.setText("Complete");
            }
            
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
            
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log("🎉 Search complete! Found " + totalSubtitles + " subtitle(s) across " + totalFilesWithResults + " file(s)");
            if (!filesWithResults.toString().isEmpty()) {
                log("Results breakdown:" + filesWithResults.toString());
            }
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Update stats label for currently selected file
            if (subtitleStatsLabel != null && currentlySelectedFile != null) {
                String baseFileName = extractBaseFileName(currentlySelectedFile);
                ObservableList<SubtitleItem> currentFileSubs = subtitlesByFile.get(baseFileName);
                if (currentFileSubs != null) {
                    int uniqueLangs = currentFileSubs.stream()
                        .map(SubtitleItem::getLanguage)
                        .collect(java.util.stream.Collectors.toSet())
                        .size();
                    subtitleStatsLabel.setText(currentFileSubs.size() + " available | " + uniqueLangs + " language(s)");
                }
            }
        });
    }
    
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
    
    private void handleGenerateAI() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        // Check if Whisper is available using StatusManager
        if (!statusManager.isWhisperAvailable()) {
            // Offer to install Whisper
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Whisper Not Installed");
            confirm.setHeaderText("AI subtitle generation requires Whisper");
            confirm.setContentText("Whisper AI is not installed. Would you like to install it now?");
            
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                handleConfigureWhisper(); // Open setup dialog
            }
                return;
            }
            
            // Check if model is installed
        try {
            JsonObject whisperStatus = pythonBridge.checkWhisper();
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
    
    // ========================================
    // WINDOW CONTROL METHODS
    // ========================================
    
    /**
     * Initialize window control icons using Ikonli FontAwesome
     */
    private void initializeWindowControlIcons() {
        try {
            // App icon
            if (appIconLabel != null) {
                try {
                    javafx.scene.image.Image appIconImage = new javafx.scene.image.Image(
                        getClass().getResourceAsStream("/icons/app-icon.png"));
                    javafx.scene.image.ImageView appIconView = new javafx.scene.image.ImageView(appIconImage);
                    appIconView.setFitWidth(16);
                    appIconView.setFitHeight(16);
                    appIconView.setPreserveRatio(true);
                    appIconView.setSmooth(true);
                    appIconLabel.setGraphic(appIconView);
                } catch (Exception e) {
                    logger.warn("Failed to load app icon, falling back to FontAwesome icon", e);
                    // Fallback to FontAwesome icon
                org.kordamp.ikonli.javafx.FontIcon appIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FILM);
                appIcon.setIconSize(16);
                appIcon.setIconColor(javafx.scene.paint.Color.web("#0078d4"));
                appIconLabel.setGraphic(appIcon);
                }
            }
            
            // Minimize icon
            org.kordamp.ikonli.javafx.FontIcon minimizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MINIMIZE);
            minimizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
            minimizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            minimizeButton.setGraphic(minimizeIcon);
            minimizeButton.setText("");
            
            // Maximize icon
            org.kordamp.ikonli.javafx.FontIcon maximizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MAXIMIZE);
            maximizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
            maximizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            maximizeButton.setGraphic(maximizeIcon);
            maximizeButton.setText("");
            
            // Close icon
            org.kordamp.ikonli.javafx.FontIcon closeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TIMES);
            closeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
            closeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            closeButton.setGraphic(closeIcon);
            closeButton.setText("");
            
            logger.info("Window control icons initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize window control icons", e);
        }
    }
    
    /**
     * Initialize toolbar and button icons using Ikonli FontAwesome
     */
    private void initializeToolbarIcons() {
        try {
            // Add Files button - Create stacked icon-above-text layout
            if (addFilesButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FILE_MEDICAL);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Add Files");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                addFilesButton.setGraphic(vbox);
                addFilesButton.setText("");  // Clear button text
            }
            
            // Add Folder button - Create stacked icon-above-text layout
            if (addFolderButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FOLDER_OPEN);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Add Folder");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                addFolderButton.setGraphic(vbox);
                addFolderButton.setText("");  // Clear button text
            }
            
            // Settings button - Create stacked icon-above-text layout
            if (settingsButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.COG);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Settings");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                settingsButton.setGraphic(vbox);
                settingsButton.setText("");  // Clear button text
            }
            
            // Encoder Mode button - Create stacked icon-above-text layout
            if (encoderModeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.VIDEO);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Encoder");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                encoderModeButton.setGraphic(vbox);
                encoderModeButton.setText("");  // Clear button text
            }
            
            // Subtitle Mode button - Create stacked icon-above-text layout
            if (subtitleModeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CLOSED_CAPTIONING);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Subtitles");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                subtitleModeButton.setGraphic(vbox);
                subtitleModeButton.setText("");  // Clear button text
            }
            
            // Metadata Mode button - Create stacked icon-above-text layout
            if (renamerModeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Metadata");
                textLabel.getStyleClass().add("mode-label");
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(2);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.getChildren().addAll(icon, textLabel);
                
                renamerModeButton.setGraphic(vbox);
                renamerModeButton.setText("");  // Clear button text
            }
            
            // Start button
            if (startButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.PLAY);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                startButton.setGraphic(icon);
                startButton.setText("Start Encoding");
            }
            
            // Pause button
            if (pauseButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.PAUSE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                pauseButton.setGraphic(icon);
                pauseButton.setText("Pause");
            }
            
            // Stop button
            if (stopButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.STOP);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                stopButton.setGraphic(icon);
                stopButton.setText("Stop");
            }
            
            // Search Metadata button (was refresh)
            if (searchMetadataButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.SEARCH);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                searchMetadataButton.setGraphic(icon);
                searchMetadataButton.setText("Search");
            }
            
            // TMDB Status button
            if (tmdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FILM);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                tmdbStatusButton.setGraphic(icon);
                tmdbStatusButton.setText("TMDB: Not Setup");
            }
            
            // Remove Queued button
            if (removeQueuedButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.MINUS);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                removeQueuedButton.setGraphic(icon);
                removeQueuedButton.setText("Remove");
            }
            
            // Clear Queued button
            if (clearQueuedButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TRASH);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                clearQueuedButton.setGraphic(icon);
                clearQueuedButton.setText("Clear");
            }
            
            // Clear Completed button
            if (clearCompletedButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TRASH);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                clearCompletedButton.setGraphic(icon);
                clearCompletedButton.setText("Clear");
            }
            
            // Config Subtitles button
            if (configSubtitlesButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CLOSED_CAPTIONING);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                configSubtitlesButton.setGraphic(icon);
                configSubtitlesButton.setText("Subtitles");
            }
            
            // Config Metadata button
            if (configRenamerButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                configRenamerButton.setGraphic(icon);
                configRenamerButton.setText("Metadata");
            }
            
            // Apply Rename button
            if (applyRenameButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                applyRenameButton.setGraphic(icon);
                applyRenameButton.setText("Apply Changes");
                applyRenameButton.setDisable(true);  // Disabled until search results load
            }
            
            // TVDB Status button
            if (tvdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TV);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                tvdbStatusButton.setGraphic(icon);
                tvdbStatusButton.setText("TVDB");
            }
            
            // OMDB Status button
            if (omdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.DATABASE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                omdbStatusButton.setGraphic(icon);
                omdbStatusButton.setText("OMDB");
            }
            
            // Trakt Status button
            if (traktStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.STREAM);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                traktStatusButton.setGraphic(icon);
                traktStatusButton.setText("Trakt");
            }
            
            // Fanart.tv Status button
            if (fanartStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.IMAGE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                fanartStatusButton.setGraphic(icon);
                fanartStatusButton.setText("Fanart");
            }
            
            // Format Pattern button
            if (formatPatternButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                formatPatternButton.setGraphic(icon);
                formatPatternButton.setText("Format Pattern");
            }
            
            // Subtitle Process button
            if (subtitleProcessButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.PLAY_CIRCLE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                subtitleProcessButton.setGraphic(icon);
                subtitleProcessButton.setText("Search Subtitles");
            }
            
            logger.info("Toolbar icons initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize toolbar icons", e);
        }
    }
    
    /**
     * Set the primary stage reference for window controls and resize functionality
     */
    public void setStage(Stage stage) {
        this.primaryStage = stage;
        if (stage != null) {
            // Set minimum size
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);
            
            // Add resize functionality via scene mouse events
            javafx.scene.Scene scene = stage.getScene();
            if (scene != null) {
                setupResizeHandlers(scene);
                logger.info("Window resize handlers installed successfully");
            }
        }
    }
    
    /**
     * Setup mouse event handlers for window resizing using event filters
     */
    private void setupResizeHandlers(javafx.scene.Scene scene) {
        // Mouse moved - update cursor based on position (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, event -> {
            if (!isResizing && !primaryStage.isMaximized()) {
                ResizeDirection direction = getResizeDirection(event.getSceneX(), event.getSceneY());
                updateCursor(direction);
            }
        });
        
        // Mouse pressed - start resize if on border (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (!primaryStage.isMaximized()) {
                resizeDirection = getResizeDirection(event.getSceneX(), event.getSceneY());
                if (resizeDirection != ResizeDirection.NONE) {
                    isResizing = true;
                    resizeStartX = event.getScreenX();
                    resizeStartY = event.getScreenY();
                    resizeStartWidth = primaryStage.getWidth();
                    resizeStartHeight = primaryStage.getHeight();
                    resizeStartStageX = primaryStage.getX();
                    resizeStartStageY = primaryStage.getY();
                    event.consume(); // Consume the event to prevent it from reaching children
                    logger.info("Started resizing: direction={}, startSize={}x{}", 
                               resizeDirection, resizeStartWidth, resizeStartHeight);
                }
            }
        });
        
        // Mouse dragged - perform resize (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, event -> {
            if (isResizing && resizeDirection != ResizeDirection.NONE) {
                performResize(event.getScreenX(), event.getScreenY());
                event.consume(); // Consume the event to prevent it from reaching children
            }
        });
        
        // Mouse released - end resize (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, event -> {
            if (isResizing) {
                logger.info("Finished resizing: finalSize={}x{}", primaryStage.getWidth(), primaryStage.getHeight());
                isResizing = false;
                resizeDirection = ResizeDirection.NONE;
                event.consume(); // Consume the event
            }
        });
    }
    
    /**
     * Determine resize direction based on mouse position
     */
    private ResizeDirection getResizeDirection(double sceneX, double sceneY) {
        double width = primaryStage.getScene().getWidth();
        double height = primaryStage.getScene().getHeight();
        
        boolean left = sceneX < RESIZE_BORDER;
        boolean right = sceneX > width - RESIZE_BORDER;
        boolean top = sceneY < RESIZE_BORDER;
        boolean bottom = sceneY > height - RESIZE_BORDER;
        
        if (top && left) return ResizeDirection.NW;
        if (top && right) return ResizeDirection.NE;
        if (bottom && left) return ResizeDirection.SW;
        if (bottom && right) return ResizeDirection.SE;
        if (top) return ResizeDirection.N;
        if (bottom) return ResizeDirection.S;
        if (left) return ResizeDirection.W;
        if (right) return ResizeDirection.E;
        
        return ResizeDirection.NONE;
    }
    
    /**
     * Update cursor based on resize direction
     */
    private void updateCursor(ResizeDirection direction) {
        javafx.scene.Cursor cursor = javafx.scene.Cursor.DEFAULT;
        
        switch (direction) {
            case N:
            case S:
                cursor = javafx.scene.Cursor.N_RESIZE;
                break;
            case E:
            case W:
                cursor = javafx.scene.Cursor.E_RESIZE;
                break;
            case NE:
            case SW:
                cursor = javafx.scene.Cursor.NE_RESIZE;
                break;
            case NW:
            case SE:
                cursor = javafx.scene.Cursor.NW_RESIZE;
                break;
        }
        
        primaryStage.getScene().setCursor(cursor);
    }
    
    /**
     * Perform window resize based on mouse drag
     */
    private void performResize(double screenX, double screenY) {
        double deltaX = screenX - resizeStartX;
        double deltaY = screenY - resizeStartY;
        
        double newX = resizeStartStageX;
        double newY = resizeStartStageY;
        double newWidth = resizeStartWidth;
        double newHeight = resizeStartHeight;
        
        switch (resizeDirection) {
            case N:
                newY = resizeStartStageY + deltaY;
                newHeight = resizeStartHeight - deltaY;
                break;
            case S:
                newHeight = resizeStartHeight + deltaY;
                break;
            case E:
                newWidth = resizeStartWidth + deltaX;
                break;
            case W:
                newX = resizeStartStageX + deltaX;
                newWidth = resizeStartWidth - deltaX;
                break;
            case NE:
                newY = resizeStartStageY + deltaY;
                newWidth = resizeStartWidth + deltaX;
                newHeight = resizeStartHeight - deltaY;
                break;
            case NW:
                newX = resizeStartStageX + deltaX;
                newY = resizeStartStageY + deltaY;
                newWidth = resizeStartWidth - deltaX;
                newHeight = resizeStartHeight - deltaY;
                break;
            case SE:
                newWidth = resizeStartWidth + deltaX;
                newHeight = resizeStartHeight + deltaY;
                break;
            case SW:
                newX = resizeStartStageX + deltaX;
                newWidth = resizeStartWidth - deltaX;
                newHeight = resizeStartHeight + deltaY;
                break;
        }
        
        // Enforce minimum size
        if (newWidth < MIN_WIDTH) {
            if (resizeDirection == ResizeDirection.W || resizeDirection == ResizeDirection.NW || resizeDirection == ResizeDirection.SW) {
                newX = resizeStartStageX + (resizeStartWidth - MIN_WIDTH);
            }
            newWidth = MIN_WIDTH;
        }
        
        if (newHeight < MIN_HEIGHT) {
            if (resizeDirection == ResizeDirection.N || resizeDirection == ResizeDirection.NE || resizeDirection == ResizeDirection.NW) {
                newY = resizeStartStageY + (resizeStartHeight - MIN_HEIGHT);
            }
            newHeight = MIN_HEIGHT;
        }
        
        // Apply new size and position
        primaryStage.setX(newX);
        primaryStage.setY(newY);
        primaryStage.setWidth(newWidth);
        primaryStage.setHeight(newHeight);
    }
    
    /**
     * Handle title bar mouse press for window dragging
     */
    @FXML
    private void handleTitleBarPressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }
    
    /**
     * Handle title bar mouse drag for window dragging
     */
    @FXML
    private void handleTitleBarDragged(MouseEvent event) {
        if (primaryStage != null) {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        }
    }
    
    /**
     * Handle minimize button click
     */
    @FXML
    private void handleMinimize() {
        if (primaryStage != null) {
            primaryStage.setIconified(true);
        }
    }
    
    /**
     * Handle maximize/restore button click
     */
    @FXML
    private void handleMaximize() {
        if (primaryStage != null) {
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
                // Update icon to maximize
                org.kordamp.ikonli.javafx.FontIcon maximizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MAXIMIZE);
                maximizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                maximizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                maximizeButton.setGraphic(maximizeIcon);
            } else {
                primaryStage.setMaximized(true);
                // Update icon to restore
                org.kordamp.ikonli.javafx.FontIcon restoreIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_RESTORE);
                restoreIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                restoreIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                maximizeButton.setGraphic(restoreIcon);
            }
        }
    }
    
    /**
     * Handle close button click
     */
    @FXML
    private void handleClose() {
        if (primaryStage != null) {
            primaryStage.fireEvent(new javafx.stage.WindowEvent(primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }
    
    /**
     * Toggle encoder logs panel visibility
     */
    @FXML
    private void handleToggleEncoderLogs() {
        encoderLogsPanelVisible = !encoderLogsPanelVisible;
        
        if (encoderRightPanel != null) {
            encoderRightPanel.setVisible(encoderLogsPanelVisible);
            encoderRightPanel.setManaged(encoderLogsPanelVisible);
        }
        
        if (encoderLogToggleButton != null) {
            encoderLogToggleButton.setText(encoderLogsPanelVisible ? "◀" : "▶");
            encoderLogToggleButton.setTooltip(new Tooltip(encoderLogsPanelVisible ? "Hide panel" : "Show panel"));
        }
        
        // Force layout update by adjusting SplitPane divider
        if (encoderModeLayout != null) {
            Platform.runLater(() -> {
                if (encoderLogsPanelVisible) {
                    encoderModeLayout.setDividerPositions(0.60);
                } else {
                    encoderModeLayout.setDividerPositions(1.0);
                }
            });
        }
    }
    
    /**
     * Toggle subtitle logs panel visibility
     */
    @FXML
    private void handleToggleSubtitleLogs() {
        subtitleLogsPanelVisible = !subtitleLogsPanelVisible;
        
        if (subtitleRightPanel != null) {
            subtitleRightPanel.setVisible(subtitleLogsPanelVisible);
            subtitleRightPanel.setManaged(subtitleLogsPanelVisible);
        }
        
        if (subtitleLogToggleButton != null) {
            subtitleLogToggleButton.setText(subtitleLogsPanelVisible ? "◀" : "▶");
            subtitleLogToggleButton.setTooltip(new Tooltip(subtitleLogsPanelVisible ? "Hide panel" : "Show panel"));
        }
        
        // Force layout update by adjusting SplitPane divider
        if (subtitleModeLayout != null) {
            Platform.runLater(() -> {
                if (subtitleLogsPanelVisible) {
                    subtitleModeLayout.setDividerPositions(0.75);
                } else {
                    subtitleModeLayout.setDividerPositions(1.0);
                }
            });
        }
    }
    
    /**
     * Toggle renamer logs panel visibility
     */
    @FXML
    private void handleToggleRenamerLogs() {
        renamerLogsPanelVisible = !renamerLogsPanelVisible;
        
        if (renamerRightPanel != null) {
            renamerRightPanel.setVisible(renamerLogsPanelVisible);
            renamerRightPanel.setManaged(renamerLogsPanelVisible);
        }
        
        if (renamerLogToggleButton != null) {
            renamerLogToggleButton.setText(renamerLogsPanelVisible ? "◀" : "▶");
            renamerLogToggleButton.setTooltip(new Tooltip(renamerLogsPanelVisible ? "Hide panel" : "Show panel"));
        }
        
        // Force layout update by adjusting SplitPane divider
        if (renamerModeLayout != null) {
            Platform.runLater(() -> {
                if (renamerLogsPanelVisible) {
                    renamerModeLayout.setDividerPositions(0.75);
                } else {
                    renamerModeLayout.setDividerPositions(1.0);
                }
            });
        }
    }
    
    /**
     * Restore the UI state for an ongoing conversion
     */
    private void restoreConversionState(String currentFile, int currentIndex, int totalFiles, 
                                       List<String> filePaths, int pid) {
        try {
            logger.info("Restoring conversion state: {} ({}/{}) PID: {}", 
                       currentFile, currentIndex, totalFiles, pid);
            
            // Switch to encoder mode if not already
            if (!"encoder".equals(currentMode)) {
                handleEncoderMode();
            }
            
            // Clear existing queues
            queuedFiles.clear();
            processingFiles.clear();
            
            // Add files to appropriate queues based on current progress
            for (int i = 0; i < filePaths.size(); i++) {
                String filePath = filePaths.get(i);
                File file = new File(filePath);
                
                if (!file.exists()) {
                    logger.warn("File no longer exists: {}", filePath);
                    continue;
                }
                
                ConversionJob job = new ConversionJob(file.getAbsolutePath());
                
                if (i < currentIndex - 1) {
                    // Files that were already processed (assume completed)
                    job.setStatus("Completed");
                    job.setProgress(100.0);
                    completedFiles.add(job);
                } else if (i == currentIndex - 1) {
                    // Currently processing file
                    job.setStatus("Processing");
                    job.setProgress(0.0); // Will be updated by progress callbacks
                    processingFiles.add(job);
                } else {
                    // Files still in queue
                    job.setStatus("Queued");
                    job.setProgress(0.0);
                    queuedFiles.add(job);
                }
            }
            
            // Update UI elements
            updateQueueCounts();
            
            // Set processing state
            isProcessing = true;
            updateControlButtons();
            
            // Update status
            if (statusLabel != null) {
                statusLabel.setText(String.format("Resumed: Processing %s (%d/%d)", 
                                                 currentFile, currentIndex, totalFiles));
            }
            
            // Show notification to user
            showNotification("Conversion Resumed", 
                           String.format("Resumed processing %s (%d/%d files)", 
                                       currentFile, currentIndex, totalFiles));
            
            logger.info("Conversion state restored successfully");
            
        } catch (Exception e) {
            logger.error("Failed to restore conversion state", e);
        }
    }
    
    
    /**
     * Update control button states based on processing status
     */
    private void updateControlButtons() {
        Platform.runLater(() -> {
            if (startButton != null) {
                startButton.setDisable(isProcessing);
            }
            if (stopButton != null) {
                stopButton.setDisable(!isProcessing);
            }
            if (pauseButton != null) {
                pauseButton.setDisable(!isProcessing);
            }
        });
    }
    
    /**
     * Show a notification to the user
     */
    private void showNotification(String title, String message) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.show();
                
                // Auto-close after 5 seconds
                Timeline timeline = new Timeline(new KeyFrame(
                    javafx.util.Duration.seconds(5),
                    e -> alert.close()
                ));
                timeline.play();
                
            } catch (Exception e) {
                logger.error("Failed to show notification", e);
            }
        });
    }
    
    /**
     * Check for ongoing conversions from previous session and recover queue
     */
    private void checkForOngoingConversions() {
        try {
            logger.info("Checking for ongoing conversions from previous session...");
            
            JsonObject response = pythonBridge.checkOngoingConversion();
            
            if (response == null || !response.has("status")) {
                logger.warn("Invalid response from check_ongoing_conversion");
                return;
            }
            
            String status = response.get("status").getAsString();
            
            if ("ongoing".equals(status)) {
                logger.info("Found ongoing conversion from previous session");
                
                // Extract queue information
                if (!response.has("queue")) {
                    logger.warn("Response missing queue information");
                    return;
                }
                
                JsonArray queue = response.getAsJsonArray("queue");
                int queuedCount = 0;
                int processingCount = 0;
                int completedCount = 0;
                int failedCount = 0;
                
                // Parse queue and populate lists
                for (int i = 0; i < queue.size(); i++) {
                    JsonObject fileEntry = queue.get(i).getAsJsonObject();
                    
                    String filePath = fileEntry.get("path").getAsString();
                    String fileStatus = fileEntry.get("status").getAsString();
                    String filename = fileEntry.get("filename").getAsString();
                    
                    ConversionJob job = new ConversionJob(filePath);
                    
                    // Set status and add to appropriate list based on status
                    switch (fileStatus) {
                        case "queued":
                            job.setStatus("⏳ Queued");
                            Platform.runLater(() -> queuedFiles.add(job));
                            queuedCount++;
                            break;
                        
                        case "processing":
                            job.setStatus("⚡ Processing");
                            if (fileEntry.has("progress")) {
                                job.setProgress(fileEntry.get("progress").getAsDouble());
                            }
                            Platform.runLater(() -> processingFiles.add(job));
                            processingCount++;
                            break;
                        
                        case "completed":
                            job.setStatus("✅ Completed");
                            job.setProgress(100.0);
                            if (fileEntry.has("output")) {
                                job.setOutputPath(fileEntry.get("output").getAsString());
                            }
                            Platform.runLater(() -> completedFiles.add(job));
                            completedCount++;
                            break;
                        
                        case "failed":
                            job.setStatus("❌ Failed");
                            String errorMsg = fileEntry.has("error") ? fileEntry.get("error").getAsString() : "Unknown error";
                            logger.warn("File {} failed: {}", filename, errorMsg);
                            // Could add failed files to a list or just skip them
                            failedCount++;
                            break;
                    }
                }
                
                final int fQueuedCount = queuedCount;
                final int fProcessingCount = processingCount;
                final int fCompletedCount = completedCount;
                final int fFailedCount = failedCount;
                
                // Update UI counts
                Platform.runLater(() -> {
                    updateQueueCounts();
                    
                    logger.info("Recovered conversion queue:");
                    logger.info("  Queued: {}", fQueuedCount);
                    logger.info("  Processing: {}", fProcessingCount);
                    logger.info("  Completed: {}", fCompletedCount);
                    logger.info("  Failed: {}", fFailedCount);
                    
                    // Show notification to user about recovered queue
                    if (fQueuedCount > 0 || fProcessingCount > 0 || fCompletedCount > 0) {
                        String message = String.format(
                            "Recovered conversion queue from previous session:\n" +
                            "• %d file(s) queued\n" +
                            "• %d file(s) processing\n" +
                            "• %d file(s) completed",
                            fQueuedCount, fProcessingCount, fCompletedCount
                        );
                        
                        if (fFailedCount > 0) {
                            message += "\n• " + fFailedCount + " file(s) failed";
                        }
                        
                        showInfo("Queue Recovered", message);
                        log("📋 Recovered conversion queue from previous session");
                        log("   Queued: " + fQueuedCount + ", Processing: " + fProcessingCount + 
                            ", Completed: " + fCompletedCount + ", Failed: " + fFailedCount);
                    }
                });
                
            } else if ("none".equals(status)) {
                logger.info("No ongoing conversions found from previous session");
            } else if ("error".equals(status)) {
                String errorMsg = response.has("message") ? response.get("message").getAsString() : "Unknown error";
                logger.error("Error checking ongoing conversions: {}", errorMsg);
            }
            
        } catch (Exception e) {
            logger.error("Failed to check for ongoing conversions", e);
        }
    }
    
    /**
     * Inner class to represent a subtitle item in the table
     */
    public static class SubtitleItem {
        private final javafx.beans.property.SimpleBooleanProperty selected;
        private final javafx.beans.property.SimpleStringProperty language;
        private final javafx.beans.property.SimpleStringProperty provider;
        private final javafx.beans.property.SimpleDoubleProperty score;
        private final javafx.beans.property.SimpleStringProperty format;
        private final String fileId;
        private final String downloadUrl;
        private final boolean manualDownloadOnly;
        
        public SubtitleItem(boolean selected, String language, String provider, double score, 
                          String format, String fileId, String downloadUrl, boolean manualDownloadOnly) {
            this.selected = new javafx.beans.property.SimpleBooleanProperty(selected);
            this.language = new javafx.beans.property.SimpleStringProperty(language);
            this.provider = new javafx.beans.property.SimpleStringProperty(provider);
            this.score = new javafx.beans.property.SimpleDoubleProperty(score);
            this.format = new javafx.beans.property.SimpleStringProperty(format);
            this.fileId = fileId;
            this.downloadUrl = downloadUrl;
            this.manualDownloadOnly = manualDownloadOnly;
        }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
        
        public String getLanguage() { return language.get(); }
        public javafx.beans.property.StringProperty languageProperty() { return language; }
        
        public String getProvider() { return provider.get(); }
        public javafx.beans.property.StringProperty providerProperty() { return provider; }
        
        public double getScore() { return score.get(); }
        public javafx.beans.property.DoubleProperty scoreProperty() { return score; }
        
        public String getFormat() { return format.get(); }
        public javafx.beans.property.StringProperty formatProperty() { return format; }
        
        public String getFileId() { return fileId; }
        public String getDownloadUrl() { return downloadUrl; }
        public boolean isManualDownloadOnly() { return manualDownloadOnly; }
    }
    
    
}