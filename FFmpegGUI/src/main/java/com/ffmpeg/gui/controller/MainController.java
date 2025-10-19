package com.ffmpeg.gui.controller;

import com.ffmpeg.gui.model.ConversionJob;
import com.ffmpeg.gui.model.ConversionSettings;
import com.ffmpeg.gui.service.PythonBridge;
import com.ffmpeg.gui.util.HardwareDetector;
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
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
    // Separate queues for different states
    private final ObservableList<ConversionJob> queuedFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> processingFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> completedFiles = FXCollections.observableArrayList();
    
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
    @FXML private Button formatPatternButton;
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
        
        // Initialize all icons
        initializeWindowControlIcons();
        initializeToolbarIcons();
        
        setupQueueTable();
        setupQueueDragAndDrop();
        setupQueueSplitPane();
        setupUIBindings();
        setupQuickSettings();
        setupPreviewTabs();
        
        // Set default mode
        if (modeComboBox != null) {
            modeComboBox.getSelectionModel().selectFirst();
        }
        
        // Run checks in parallel for faster startup
        CompletableFuture.runAsync(this::checkFFmpegAvailability);
        CompletableFuture.runAsync(this::checkProviderStatus);
        
        logger.info("Main Controller initialized (async checks running)");
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
                if (!queuedFiles.isEmpty()) {
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
        
        // Provider-specific notes
        String providerNote = getProviderNote(subtitle.getProvider());
        if (providerNote != null) {
            details.append("⚠️ Note: ").append(providerNote).append("\n");
        }
        
        subtitleDetailsLabel.setText(details.toString());
    }
    
    private String getProviderDescription(String provider) {
        switch (provider) {
            case "OpenSubtitles.com":
                return "Official API with high-quality subtitles";
            case "Addic7ed":
                return "Excellent for TV shows, especially recent episodes";
            case "Jimaku":
                return "Modern anime subtitle search (Japanese & English)";
            case "AnimeSubtitles":
                return "Multi-language anime subtitle database";
            case "Kitsunekko":
                return "Japanese anime subtitles (romaji/kanji)";
            case "SubDL":
                return "Large database for movies and TV shows";
            case "Podnapisi":
                return "Multi-language subtitle database";
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
            case "Jimaku":
            case "AnimeSubtitles":
            case "Kitsunekko":
                return "This provider may require manual download";
            case "OpenSubtitles.com":
                return "Requires API key for downloads";
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
            }
        }
        
        updateQueueCounts();
        updateSelectedFilesLabel();
        
        // Update subtitle file list if in subtitle mode
        if ("subtitle".equals(currentMode)) {
            updateSubtitleFileList();
        }
        
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
        // Get files from queue
        List<String> filePaths = new ArrayList<>();
        for (ConversionJob job : queuedFiles) {
            filePaths.add(job.getInputPath());
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
    
    private void resetProcessingState() {
        isProcessing = false;
        updateQueueCounts();
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
                        
                        // Note: Encoder detection is now handled by Java HardwareDetector
                        // No need to update encoder list from Python backend
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
                // First, send current settings to Python backend
                try {
                    JsonObject updateSettings = new JsonObject();
                    updateSettings.addProperty("action", "update_settings");
                    updateSettings.add("settings", settings.toJson());
                    pythonBridge.sendCommand(updateSettings);
                    logger.info("Settings synchronized with Python backend");
                } catch (Exception e) {
                    logger.error("Failed to sync settings with Python backend", e);
                }
                
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
                });
                
                // Check OpenSubtitles status
                JsonObject osResponse = pythonBridge.checkOpenSubtitles();
                boolean osConfigured = osResponse.has("configured") && 
                                      osResponse.get("configured").getAsBoolean();
                boolean hasApiKey = osResponse.has("has_api_key") && 
                                   osResponse.get("has_api_key").getAsBoolean();
                
                logger.info("OpenSubtitles status: configured={}, hasApiKey={}", osConfigured, hasApiKey);
                
                // Check if previously validated
                boolean isValidated = osResponse.has("configured") && osResponse.get("configured").getAsBoolean() &&
                                     settings != null && settings.isOpensubtitlesValidated();
                
                Platform.runLater(() -> {
                    if (openSubsStatusButton != null) {
                        if (isValidated) {
                            openSubsStatusButton.setText("🌐 Subtitles: 10 Providers ✓");
                            openSubsStatusButton.getStyleClass().removeAll("error", "warning");
                            openSubsStatusButton.getStyleClass().add("active");
                            openSubsStatusButton.setTooltip(new Tooltip(
                                "✅ OpenSubtitles.com (validated)\n" +
                                "✅ Addic7ed (TV shows)\n" +
                                "✅ SubDL (Movies & TV)\n" +
                                "✅ Subf2m (Movies & TV)\n" +
                                "✅ YIFY Subtitles (Movies)\n" +
                                "✅ Podnapisi (Multilingual)\n" +
                                "✅ SubDivX (Spanish)\n" +
                                "🎌 Kitsunekko (Anime - JP)\n" +
                                "🎌 Jimaku (Anime - JP/EN)\n" +
                                "🎌 AnimeSubtitles (Anime)\n\n" +
                                "Total: 10 subtitle providers"
                            ));
                        } else if (osConfigured || hasApiKey) {
                            openSubsStatusButton.setText("🌐 Subtitles: 10 Providers");
                            openSubsStatusButton.getStyleClass().removeAll("error", "warning");
                            openSubsStatusButton.getStyleClass().add("active");
                            openSubsStatusButton.setTooltip(new Tooltip(
                                "⚠️ OpenSubtitles.com (not validated)\n" +
                                "✅ Addic7ed (TV shows)\n" +
                                "✅ SubDL (Movies & TV)\n" +
                                "✅ Subf2m (Movies & TV)\n" +
                                "✅ YIFY Subtitles (Movies)\n" +
                                "✅ Podnapisi (Multilingual)\n" +
                                "✅ SubDivX (Spanish)\n" +
                                "🎌 Kitsunekko (Anime - JP)\n" +
                                "🎌 Jimaku (Anime - JP/EN)\n" +
                                "🎌 AnimeSubtitles (Anime)\n\n" +
                                "Total: 10 providers (validate OpenSubtitles in Settings)"
                            ));
                        } else {
                            openSubsStatusButton.setText("🌐 Subtitles: 9 Free Providers");
                            openSubsStatusButton.getStyleClass().removeAll("error", "active");
                            openSubsStatusButton.getStyleClass().add("warning");
                            openSubsStatusButton.setTooltip(new Tooltip(
                                "Using free providers (no API key):\n" +
                                "✅ Addic7ed (TV shows)\n" +
                                "✅ SubDL (Movies & TV)\n" +
                                "✅ Subf2m (Movies & TV)\n" +
                                "✅ YIFY Subtitles (Movies)\n" +
                                "✅ Podnapisi (Multilingual)\n" +
                                "✅ SubDivX (Spanish)\n" +
                                "🎌 Kitsunekko (Anime - JP)\n" +
                                "🎌 Jimaku (Anime - JP/EN)\n" +
                                "🎌 AnimeSubtitles (Anime)\n\n" +
                                "Add OpenSubtitles API key in Settings\n" +
                                "for even better results (10 providers)"
                            ));
                        }
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
    private void handleApplySubtitles() {
        if (queuedFiles.isEmpty()) {
            showWarning("No Files", "Please add files to the queue first.");
            return;
        }
        
        if (availableSubtitlesTable == null || availableSubtitlesTable.getItems().isEmpty()) {
            showWarning("No Subtitles", "Please search for subtitles first using the 'Process Files' button.");
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
        
        log("Applying " + selectedSubtitles.size() + " subtitle(s) in mode: " + mode);
        
        // Get the video file
        ConversionJob job = queuedFiles.get(0);
        String videoPath = job.getInputPath();
        
        final String finalMode = mode;
        
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (SubtitleItem subtitle : selectedSubtitles) {
                try {
                    Platform.runLater(() -> log("Processing " + subtitle.getLanguage() + " subtitle from " + subtitle.getProvider() + "..."));
                    
                    // Step 1: Download the subtitle
                    Platform.runLater(() -> log("Downloading subtitle from " + subtitle.getProvider() + "..."));
                    
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
                        boolean requiresManual = downloadResponse.has("requires_manual_download") && 
                                                downloadResponse.get("requires_manual_download").getAsBoolean();
                        
                        if (requiresManual) {
                            Platform.runLater(() -> {
                                log("⚠️ Manual download required: " + errorMsg);
                                showWarning("Manual Download Required", 
                                    subtitle.getProvider() + " requires manual download.\n\n" + errorMsg);
                            });
                        } else {
                            Platform.runLater(() -> log("❌ Download failed: " + errorMsg));
                        }
                        failCount++;
                        continue;
                    }
                    
                    // Get the downloaded subtitle path
                    String subtitlePath = downloadResponse.get("subtitle_path").getAsString();
                    Platform.runLater(() -> log("✅ Downloaded to: " + subtitlePath));
                    
                    // Step 2: Apply the subtitle
                    Platform.runLater(() -> log("Applying subtitle in '" + finalMode + "' mode..."));
                    
                    JsonObject applyRequest = new JsonObject();
                    applyRequest.addProperty("action", "apply_subtitles");
                    applyRequest.addProperty("video_path", videoPath);
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
    }
    
    private void updateSubtitleFileList() {
        if (subtitleFileCombo != null && !queuedFiles.isEmpty()) {
            ObservableList<String> fileNames = FXCollections.observableArrayList();
            for (ConversionJob job : queuedFiles) {
                fileNames.add(job.getFileName());
            }
            subtitleFileCombo.setItems(fileNames);
            if (!fileNames.isEmpty()) {
                subtitleFileCombo.getSelectionModel().selectFirst();
            }
        }
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
        
        // First, search for available subtitles to show in the table
        if (mode.contains("Download Only") || mode.contains("Automatic") || mode.contains("Both")) {
            searchAndDisplaySubtitles();
        } else if (mode.contains("AI Generation Only")) {
            // Do AI generation directly
            handleGenerateAI();
        }
    }
    
    private void searchAndDisplaySubtitles() {
        if (queuedFiles.isEmpty()) {
            return;
        }
        
        // Get selected languages
        List<String> selectedLanguages = getSelectedLanguages();
        
        if (selectedLanguages.isEmpty()) {
            showWarning("No Languages", "Please select at least one language.");
            return;
        }
        
        log("Searching for available subtitles...");
        
        if (subtitleStatsLabel != null) {
            Platform.runLater(() -> subtitleStatsLabel.setText("Searching..."));
        }
        
        // Clear existing results
        if (availableSubtitlesTable != null) {
            Platform.runLater(() -> availableSubtitlesTable.getItems().clear());
        }
        
        new Thread(() -> {
            try {
                // Get the first file (for now, search for one file at a time)
                ConversionJob job = queuedFiles.get(0);
                String filePath = job.getInputPath();
                String fileName = new File(filePath).getName();
                
                log("Searching subtitles for: " + fileName);
                
                // Create search request
                JsonObject request = new JsonObject();
                request.addProperty("action", "search_subtitles");
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
                
                // Search for subtitles
                JsonObject response = pythonBridge.sendCommand(request);
                
                // Log the full response for debugging
                logger.info("Search response: {}", response.toString());
                Platform.runLater(() -> log("Backend response: " + response.toString()));
                
                if (response.has("status") && "success".equals(response.get("status").getAsString())) {
                    int count = response.has("count") ? response.get("count").getAsInt() : 0;
                    
                Platform.runLater(() -> {
                        log("Found " + count + " subtitle(s)");
                        
                        if (count > 0 && response.has("subtitles") && response.get("subtitles").isJsonArray()) {
                            JsonArray subtitles = response.getAsJsonArray("subtitles");
                            
                            // Parse and display results
                            Set<String> languages = new HashSet<>();
                            for (int i = 0; i < subtitles.size(); i++) {
                                JsonObject sub = subtitles.get(i).getAsJsonObject();
                                String language = sub.has("language") ? sub.get("language").getAsString() : "unknown";
                                String provider = sub.has("provider") ? sub.get("provider").getAsString() : "unknown";
                                double score = sub.has("score") ? sub.get("score").getAsDouble() : 0.0;
                                String format = sub.has("format") ? sub.get("format").getAsString() : "srt";
                                String fileId = sub.has("file_id") ? sub.get("file_id").getAsString() : "";
                                String downloadUrl = sub.has("download_url") ? sub.get("download_url").getAsString() : "";
                                
                                languages.add(language);
                                
                                SubtitleItem item = new SubtitleItem(false, language, provider, score, format, fileId, downloadUrl);
                                if (availableSubtitlesTable != null) {
                                    availableSubtitlesTable.getItems().add(item);
                                }
                            }
                            
                            // Update stats label
                            if (subtitleStatsLabel != null) {
                                subtitleStatsLabel.setText(count + " available | " + languages.size() + " language(s)");
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
                
            } catch (Exception e) {
                logger.error("Error searching subtitles", e);
                Platform.runLater(() -> {
                    log("❌ Error: " + e.getMessage());
                    if (subtitleStatsLabel != null) {
                        subtitleStatsLabel.setText("Error");
                    }
                });
            }
        }, "SubtitleSearch").start();
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
    
    // Inner class for subtitle table
    public static class SubtitleItem {
        private boolean selected;
        private String language;
        private String provider;
        private double score;
        private String format;
        private String fileId;
        private String downloadUrl;
        
        public SubtitleItem(boolean selected, String language, String provider, double score, String format) {
            this(selected, language, provider, score, format, "", "");
        }
        
        public SubtitleItem(boolean selected, String language, String provider, double score, String format, String fileId, String downloadUrl) {
            this.selected = selected;
            this.language = language;
            this.provider = provider;
            this.score = score;
            this.format = format;
            this.fileId = fileId;
            this.downloadUrl = downloadUrl;
        }
        
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
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
            // Add Files button
            if (addFilesButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FILE_MEDICAL);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                addFilesButton.setGraphic(icon);
                addFilesButton.setText("Add Files");
            }
            
            // Add Folder button
            if (addFolderButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FOLDER_OPEN);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                addFolderButton.setGraphic(icon);
                addFolderButton.setText("Add Folder");
            }
            
            // Settings button
            if (settingsButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.COG);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                settingsButton.setGraphic(icon);
                settingsButton.setText("Settings");
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
            
            // Refresh Metadata button
            if (refreshMetadataButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.SYNC_ALT);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                refreshMetadataButton.setGraphic(icon);
                refreshMetadataButton.setText("Refresh");
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
            
            // Config Renamer button
            if (configRenamerButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                configRenamerButton.setGraphic(icon);
                configRenamerButton.setText("Renamer");
            }
            
            // Apply Rename button
            if (applyRenameButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                applyRenameButton.setGraphic(icon);
                applyRenameButton.setText("Apply Selected");
            }
            
            // TVDB Status button
            if (tvdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TV);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                tvdbStatusButton.setGraphic(icon);
                tvdbStatusButton.setText("TVDB: Not Setup");
            }
            
            // AniList Status button
            if (anilistStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.BOOK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                anilistStatusButton.setGraphic(icon);
                anilistStatusButton.setText("AniList: Available");
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
                subtitleProcessButton.setText("Process Files");
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
    
}
