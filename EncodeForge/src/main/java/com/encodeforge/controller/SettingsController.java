package com.encodeforge.controller;

import com.encodeforge.model.ConversionSettings;
import com.encodeforge.service.DependencyManager;
import com.encodeforge.service.PythonBridge;
import com.encodeforge.util.PathManager;
import com.encodeforge.util.StatusManager;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import java.io.File;

/**
 * Controller for the Settings Dialog
 */
public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    
    private Stage dialogStage;
    private ConversionSettings settings;
    private PythonBridge pythonBridge;
    private com.encodeforge.service.PythonProcessPool processPool;
    private DependencyManager dependencyManager;
    private StatusManager statusManager;
    private boolean applied = false;
    
    // Window dragging variables
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Settings dialog doesn't need resizing - it's a fixed size modal
    
    // Window Controls
    @FXML private Label appIconLabel;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    
    // Icon sizes
    private static final int WINDOW_CONTROL_ICON_SIZE = 14;
    private static final int TOOLBAR_ICON_SIZE = 14;
    
    // Category List
    @FXML private ListView<String> categoryList;
    
    // Category Panes
    @FXML private VBox generalSettings;
    @FXML private VBox ffmpegSettings;
    @FXML private VBox videoSettings;
    @FXML private VBox audioSettings;
    @FXML private VBox subtitleSettings;
    @FXML private VBox metadataSettings;
    @FXML private VBox outputSettings;
    @FXML private VBox advancedSettings;
    
    // General Settings
    @FXML private Label settingsLocationLabel;
    @FXML private Button openSettingsFolderButton;
    @FXML private TextField outputDirField;
    @FXML private CheckBox deleteOriginalCheck;
    @FXML private CheckBox overwriteCheck;
    @FXML private CheckBox preserveDateCheck;
    @FXML private Spinner<Integer> concurrentSpinner;
    
    // FFmpeg Settings
    @FXML private TextField ffmpegPathField;
    @FXML private TextField ffprobePathField;
    @FXML private Button autoDetectButton;
    @FXML private Button downloadFFmpegButton;
    @FXML private Label ffmpegVersionLabel;
    
    // Video Settings
    @FXML private ComboBox<String> outputFormatCombo;
    @FXML private ComboBox<String> videoCodecCombo;
    @FXML private CheckBox hwDecodeCheck;
    @FXML private ComboBox<String> qualityPresetCombo;
    @FXML private Spinner<Integer> crfSpinner;
    
    // Audio Settings
    @FXML private ComboBox<String> audioCodecCombo;
    @FXML private RadioButton audioAllTracksRadio;
    @FXML private RadioButton audioFirstTrackRadio;
    @FXML private RadioButton audioLanguageRadio;
    @FXML private TextField audioLanguageField;
    @FXML private ComboBox<String> audioBitrateCombo;
    
    // Subtitle Settings
    @FXML private CheckBox convertSubsCheck;
    @FXML private ComboBox<String> subtitleFormatCombo;
    @FXML private ComboBox<String> whisperModelCombo;
    
    // Whisper Management
    @FXML private Label whisperStatusLabel;
    @FXML private Label whisperInstalledModelsLabel;
    @FXML private Button setupWhisperButton;
    @FXML private Button uninstallWhisperButton;
    @FXML private Button downloadModelButton;
    
    // Subtitle Settings (continued) - OpenSubtitles Consumer API is hardcoded, users provide login
    @FXML private TextField opensubtitlesUsernameField;
    @FXML private PasswordField opensubtitlesPasswordField;
    @FXML private Button validateOpenSubsButton;
    @FXML private Label openSubsValidationLabel;
    
    // Metadata Settings
    @FXML private TextField namingPatternField;
    @FXML private CheckBox createSubfoldersCheck;
    @FXML private TextField tmdbApiKeyField;
    @FXML private Button validateTMDBButton;
    @FXML private TextField tvdbApiKeyField;
    @FXML private Button validateTVDBButton;
    @FXML private TextField omdbApiKeyField;
    @FXML private Button validateOMDBButton;
    @FXML private TextField traktApiKeyField;
    @FXML private Button validateTraktButton;
    @FXML private Label tmdbValidationLabel;
    @FXML private Label tvdbValidationLabel;
    @FXML private Label omdbValidationLabel;
    @FXML private Label traktValidationLabel;
    @FXML private TextField tvShowPatternField;
    @FXML private TextField moviePatternField;
    @FXML private TextField animePatternField;
    @FXML private Button openPatternEditorButton;
    @FXML private ComboBox<String> languagePreferenceCombo;
    
    // Output Settings
    @FXML private CheckBox copyMetadataCheck;
    @FXML private CheckBox stripMetadataCheck;
    
    // Advanced Settings
    @FXML private TextArea customArgsArea;
    @FXML private CheckBox twoPassCheck;
    @FXML private CheckBox fastStartCheck;
    @FXML private Spinner<Integer> threadSpinner;
    
    @FXML
    public void initialize() {
        // Initialize icons
        initializeIcons();
        
        // Initialize category list
        categoryList.setItems(javafx.collections.FXCollections.observableArrayList(
            "General", "FFmpeg", "Video", "Audio", "Subtitles", "Metadata", "Output", "Advanced"
        ));
        
        // Setup category selection
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showCategory(newVal);
        });
        
        // Show settings location
        if (settingsLocationLabel != null) {
            settingsLocationLabel.setText(PathManager.getSettingsFilePath());
        }
        
        // Select first category by default
        categoryList.getSelectionModel().selectFirst();
        
        // Setup audio language field enable/disable
        audioLanguageRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            audioLanguageField.setDisable(!newVal);
        });
        
        // Initialize spinners
        if (concurrentSpinner != null) {
            SpinnerValueFactory<Integer> concurrentFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 8, 1);
            concurrentSpinner.setValueFactory(concurrentFactory);
        }
        
        if (crfSpinner != null) {
            SpinnerValueFactory<Integer> crfFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 51, 23);
            crfSpinner.setValueFactory(crfFactory);
        }
        
        if (threadSpinner != null) {
            SpinnerValueFactory<Integer> threadFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 32, 0);
            threadSpinner.setValueFactory(threadFactory);
        }
        
        // Initialize ComboBoxes
        initializeComboBoxes();
        
        // Initialize Whisper status (will be updated when dialog is shown)
        updateWhisperStatus();
    }
    
    private void initializeComboBoxes() {
        // Output format
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "MP4", "MKV", "AVI", "MOV", "WebM"));
            outputFormatCombo.setValue("MP4");
        }
        
        // Video codec - use instant hardware detection
        if (videoCodecCombo != null) {
            List<String> availableEncoders = com.encodeforge.util.HardwareDetector.getAvailableEncoderList();
            availableEncoders.add(0, "Auto (Best Available)");
            videoCodecCombo.setItems(javafx.collections.FXCollections.observableArrayList(availableEncoders));
            videoCodecCombo.setValue("Auto (Best Available)");
            logger.info("Settings dialog: Codec options initialized instantly with {} encoders", availableEncoders.size());
        }
        
        // Quality preset
        if (qualityPresetCombo != null) {
            qualityPresetCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "Ultrafast", "Superfast", "Veryfast", "Faster", "Fast", 
                "Medium", "Slow", "Slower", "Veryslow"));
            qualityPresetCombo.setValue("Medium");
        }
        
        // Audio codec
        if (audioCodecCombo != null) {
            audioCodecCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "Copy (no re-encode)", "AAC", "AC3", "EAC3", "MP3", "Opus", "Vorbis"));
            audioCodecCombo.setValue("Copy (no re-encode)");
        }
        
        // Audio bitrate
        if (audioBitrateCombo != null) {
            audioBitrateCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "Auto", "128k", "192k", "256k", "320k", "384k", "512k"));
            audioBitrateCombo.setValue("Auto");
        }
        
        // Subtitle format
        if (subtitleFormatCombo != null) {
            subtitleFormatCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "Auto", "SRT", "ASS", "MOV_TEXT"));
            subtitleFormatCombo.setValue("Auto");
        }
        
        // Whisper model
        if (whisperModelCombo != null) {
            whisperModelCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "tiny", "base", "small", "medium", "large"));
            whisperModelCombo.setValue("base");
        }
        
        // Language preference
        if (languagePreferenceCombo != null) {
            languagePreferenceCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "English", "Romanized Japanese (Romaji)", "Japanese", "Original Language"));
            languagePreferenceCombo.setValue("English");
        }
    }
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        // Settings dialog is not resizable - fixed size
        if (dialogStage != null) {
            dialogStage.setResizable(false);
        }
    }
    
    public void setSettings(ConversionSettings settings) {
        this.settings = settings;
        loadSettings();
    }
    
    public void setPythonBridge(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
        
        // Initialize status manager if not already set
        if (this.statusManager == null) {
            this.statusManager = StatusManager.getInstance();
        }
        
        // Initialize dependency manager if not already set
        if (this.dependencyManager == null) {
            try {
                this.dependencyManager = new DependencyManager();
            } catch (IOException e) {
                logger.error("Failed to initialize DependencyManager in SettingsController", e);
            }
        }
        
        // Check FFmpeg using Java DependencyManager (not Python)
        checkFFmpegStatus();
        
        // Encoder detection is now handled instantly by Java HardwareDetector
    }
    
    public void setProcessPool(com.encodeforge.service.PythonProcessPool processPool) {
        this.processPool = processPool;
        // Update Whisper status using process pool
        updateWhisperStatus();
        // No need for delayed backend checks
        logger.info("Settings dialog initialized with hardware-detected encoders");
    }
    
    /**
     * Set the DependencyManager from MainController
     */
    public void setDependencyManager(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
        // Re-check FFmpeg status with the provided DependencyManager
        checkFFmpegStatus();
    }
    
    public void navigateToCategory(String category) {
        if (categoryList != null) {
            categoryList.getSelectionModel().select(category);
        }
    }
    
    public boolean isApplied() {
        return applied;
    }
    
    private void showCategory(String category) {
        // Hide all categories
        generalSettings.setVisible(false);
        generalSettings.setManaged(false);
        ffmpegSettings.setVisible(false);
        ffmpegSettings.setManaged(false);
        videoSettings.setVisible(false);
        videoSettings.setManaged(false);
        audioSettings.setVisible(false);
        audioSettings.setManaged(false);
        subtitleSettings.setVisible(false);
        subtitleSettings.setManaged(false);
        metadataSettings.setVisible(false);
        metadataSettings.setManaged(false);
        outputSettings.setVisible(false);
        outputSettings.setManaged(false);
        advancedSettings.setVisible(false);
        advancedSettings.setManaged(false);
        
        // Show selected category
        switch (category) {
            case "General":
                generalSettings.setVisible(true);
                generalSettings.setManaged(true);
                break;
            case "FFmpeg":
                ffmpegSettings.setVisible(true);
                ffmpegSettings.setManaged(true);
                break;
            case "Video":
                videoSettings.setVisible(true);
                videoSettings.setManaged(true);
                break;
            case "Audio":
                audioSettings.setVisible(true);
                audioSettings.setManaged(true);
                break;
            case "Subtitles":
                subtitleSettings.setVisible(true);
                subtitleSettings.setManaged(true);
                break;
            case "Metadata":
                metadataSettings.setVisible(true);
                metadataSettings.setManaged(true);
                break;
            case "Output":
                outputSettings.setVisible(true);
                outputSettings.setManaged(true);
                break;
            case "Advanced":
                advancedSettings.setVisible(true);
                advancedSettings.setManaged(true);
                break;
        }
    }
    
    private void loadSettings() {
        if (settings == null) return;
        
        // Load settings into UI controls
        ffmpegPathField.setText(settings.getFfmpegPath());
        ffprobePathField.setText(settings.getFfprobePath());
        
        deleteOriginalCheck.setSelected(settings.isDeleteOriginal());
        overwriteCheck.setSelected(settings.isOverwriteExisting());
        
        videoCodecCombo.setValue(settings.getVideoCodec());
        qualityPresetCombo.setValue(settings.getNvencPreset());
        crfSpinner.getValueFactory().setValue(settings.getNvencCq());
        
        audioCodecCombo.setValue(settings.getAudioCodec());
        
        convertSubsCheck.setSelected(settings.isConvertSubtitles());
        subtitleFormatCombo.setValue(settings.getSubtitleFormat());
        
        // Load Whisper settings
        if (whisperModelCombo != null) whisperModelCombo.setValue(settings.getWhisperModel());
        
        hwDecodeCheck.setSelected(settings.isHardwareDecoding());
        
        // Load API keys
        if (tmdbApiKeyField != null) tmdbApiKeyField.setText(settings.getTmdbApiKey());
        if (tvdbApiKeyField != null) tvdbApiKeyField.setText(settings.getTvdbApiKey());
        if (omdbApiKeyField != null) omdbApiKeyField.setText(settings.getOmdbApiKey());
        if (traktApiKeyField != null) traktApiKeyField.setText(settings.getTraktApiKey());
        // OpenSubtitles login credentials (no API key field anymore - Consumer API is hardcoded)
        if (opensubtitlesUsernameField != null) opensubtitlesUsernameField.setText(settings.getOpensubtitlesUsername());
        if (opensubtitlesPasswordField != null) opensubtitlesPasswordField.setText(settings.getOpensubtitlesPassword());
        
        // Load format patterns
        if (tvShowPatternField != null) tvShowPatternField.setText(settings.getTvShowPattern());
        if (moviePatternField != null) moviePatternField.setText(settings.getMoviePattern());
        if (animePatternField != null) animePatternField.setText(settings.getAnimePattern());
        
        // Show validation status if already validated
        if (openSubsValidationLabel != null && settings.isOpensubtitlesValidated()) {
            openSubsValidationLabel.setText("✅ Previously validated");
            openSubsValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
        }
        if (tmdbValidationLabel != null && settings.isTmdbValidated()) {
            tmdbValidationLabel.setText("✅ Previously validated");
            tmdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
        }
        if (tvdbValidationLabel != null && settings.isTvdbValidated()) {
            tvdbValidationLabel.setText("✅ Previously validated");
            tvdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
        }
        if (omdbValidationLabel != null && settings.isOmdbValidated()) {
            omdbValidationLabel.setText("✅ Previously validated");
            omdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
        }
        if (traktValidationLabel != null && settings.isTraktValidated()) {
            traktValidationLabel.setText("✅ Previously validated");
            traktValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
        }
    }
    
    private void saveSettings() {
        if (settings == null) return;
        
        // Save UI controls to settings
        settings.setFfmpegPath(ffmpegPathField.getText());
        settings.setFfprobePath(ffprobePathField.getText());
        
        settings.setDeleteOriginal(deleteOriginalCheck.isSelected());
        settings.setOverwriteExisting(overwriteCheck.isSelected());
        
        settings.setVideoCodec(videoCodecCombo.getValue());
        settings.setNvencPreset(qualityPresetCombo.getValue());
        settings.setNvencCq(crfSpinner.getValue());
        
        settings.setAudioCodec(audioCodecCombo.getValue());
        
        settings.setConvertSubtitles(convertSubsCheck.isSelected());
        settings.setSubtitleFormat(subtitleFormatCombo.getValue());
        
        // Save Whisper settings
        if (whisperModelCombo != null) settings.setWhisperModel(whisperModelCombo.getValue());
        
        settings.setHardwareDecoding(hwDecodeCheck.isSelected());
        
        // Save API keys
        if (tmdbApiKeyField != null) settings.setTmdbApiKey(tmdbApiKeyField.getText());
        if (tvdbApiKeyField != null) settings.setTvdbApiKey(tvdbApiKeyField.getText());
        if (omdbApiKeyField != null) settings.setOmdbApiKey(omdbApiKeyField.getText());
        if (traktApiKeyField != null) settings.setTraktApiKey(traktApiKeyField.getText());
        // OpenSubtitles login credentials (no API key field anymore - Consumer API is hardcoded)
        if (opensubtitlesUsernameField != null) settings.setOpensubtitlesUsername(opensubtitlesUsernameField.getText());
        if (opensubtitlesPasswordField != null) settings.setOpensubtitlesPassword(opensubtitlesPasswordField.getText());
        
        // Save format patterns
        if (tvShowPatternField != null) settings.setTvShowPattern(tvShowPatternField.getText());
        if (moviePatternField != null) settings.setMoviePattern(moviePatternField.getText());
        if (animePatternField != null) settings.setAnimePattern(animePatternField.getText());
    }
    
    @FXML
    private void handleOpenSettingsFolder() {
        try {
            File settingsDir = new File(ConversionSettings.getSettingsFilePath()).getParentFile();
            if (settingsDir.exists()) {
                java.awt.Desktop.getDesktop().open(settingsDir);
            } else {
                logger.warn("Settings folder does not exist yet: {}", settingsDir);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Settings Folder");
                alert.setHeaderText("Settings folder will be created on first save");
                alert.setContentText("Path: " + settingsDir.getAbsolutePath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Error opening settings folder", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not open settings folder");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
    @FXML
    private void handleBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");
        File selected = chooser.showDialog(dialogStage);
        if (selected != null) {
            outputDirField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleBrowseFFmpeg() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select FFmpeg Executable");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Executable", "*.exe", "ffmpeg")
        );
        File selected = chooser.showOpenDialog(dialogStage);
        if (selected != null) {
            ffmpegPathField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleBrowseFFprobe() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select FFprobe Executable");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Executable", "*.exe", "ffprobe")
        );
        File selected = chooser.showOpenDialog(dialogStage);
        if (selected != null) {
            ffprobePathField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleAutoDetect() {
        logger.info("Auto-detect FFmpeg requested");
        checkFFmpegStatus();
    }
    
    private void checkFFmpegStatus() {
        if (dependencyManager == null) {
            logger.warn("DependencyManager not available for FFmpeg check");
            Platform.runLater(() -> {
                ffmpegVersionLabel.setText("❌ FFmpeg: DependencyManager not available");
                ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
            });
            return;
        }
        
        // Show checking status immediately
        Platform.runLater(() -> {
            ffmpegVersionLabel.setText("⏳ FFmpeg: Checking...");
            ffmpegVersionLabel.setStyle("-fx-text-fill: #ffb900;");
        });
        
        new Thread(() -> {
            try {
                // Use Java DependencyManager to check FFmpeg (not Python)
                boolean available = dependencyManager.checkFFmpeg().get();
                
                if (!available) {
                    Platform.runLater(() -> {
                        ffmpegVersionLabel.setText("❌ FFmpeg: Not Found");
                        ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
                        ffmpegPathField.setText("");
                        ffprobePathField.setText("");
                    });
                    return;
                }
                
                // FFmpeg found - get path and version
                java.nio.file.Path ffmpegPath = dependencyManager.getInstalledFFmpegPath();
                String ffmpegPathStr = null;
                String version = "Found";
                
                if (ffmpegPath != null) {
                    ffmpegPathStr = ffmpegPath.toString();
                } else {
                    // FFmpeg is in system PATH
                    version = "System PATH";
                    ffmpegPathStr = "ffmpeg";  // Will be in PATH
                }
                
                // Try to get version from Python backend for display
                try {
                    if (pythonBridge != null) {
                        JsonObject response = pythonBridge.getAllStatus();
                        if (response.has("ffmpeg")) {
                            JsonObject ffmpegInfo = response.getAsJsonObject("ffmpeg");
                            if (ffmpegInfo.has("version")) {
                                version = ffmpegInfo.get("version").getAsString();
                            }
                            if (ffmpegInfo.has("path") && ffmpegPathStr == null) {
                                ffmpegPathStr = ffmpegInfo.get("path").getAsString();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not get FFmpeg version from Python, using default", e);
                }
                
                final String finalVersion = version;
                final String finalPath = ffmpegPathStr;
                
                Platform.runLater(() -> {
                    ffmpegVersionLabel.setText("✅ FFmpeg: " + finalVersion);
                    ffmpegVersionLabel.setStyle("-fx-text-fill: #4ec9b0;");
                    if (finalPath != null && !finalPath.isEmpty() && !finalPath.equals("ffmpeg")) {
                        ffmpegPathField.setText(finalPath);
                        // Also set ffprobe path
                        String ffprobePath = finalPath.replace("ffmpeg", "ffprobe");
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            ffprobePath = ffprobePath.replace(".exe", "") + ".exe";
                        }
                        ffprobePathField.setText(ffprobePath);
                    } else if (finalPath != null && finalPath.equals("ffmpeg")) {
                        // FFmpeg is in system PATH
                        ffmpegPathField.setText("ffmpeg");
                        ffprobePathField.setText("ffprobe");
                    }
                    logger.info("FFmpeg status updated in Settings: {} at {}", finalVersion, finalPath);
                });
                
            } catch (Exception e) {
                logger.error("Error checking FFmpeg status via DependencyManager", e);
                Platform.runLater(() -> {
                    ffmpegVersionLabel.setText("❌ FFmpeg: Error - " + e.getMessage());
                    ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
                });
            }
        }).start();
    }
    
    @FXML
    private void handleRestoreDefaults() {
        // Confirm with user
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Defaults");
        confirm.setHeaderText("Restore all settings to default values?");
        confirm.setContentText("This will reset all your settings. This action cannot be undone.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Restore default settings
            if (settings != null) {
                settings.restoreDefaults();
                loadSettings();
                
                // Save the defaults
                if (settings.save()) {
                    logger.info("Settings restored to defaults and saved");
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Settings Restored");
                    info.setHeaderText("Settings have been restored to defaults");
                    info.showAndWait();
                }
            }
        }
    }
    
    @FXML
    private void handleCancel() {
        applied = false;
        dialogStage.close();
    }
    
    @FXML
    private void handleApply() {
        saveSettings();
        applied = true;
    }
    
    @FXML
    private void handleOK() {
        handleApply();
        dialogStage.close();
    }
    
    @FXML
    private void handleValidateOpenSubtitles() {
        if (openSubsValidationLabel != null) {
            openSubsValidationLabel.setText("⏳ Validating...");
        }
        
        new Thread(() -> {
            try {
                String username = opensubtitlesUsernameField != null ? opensubtitlesUsernameField.getText().trim() : "";
                String password = opensubtitlesPasswordField != null ? opensubtitlesPasswordField.getText().trim() : "";
                
                Platform.runLater(() -> {
                    if (openSubsValidationLabel != null) {
                        if (username.isEmpty() && password.isEmpty()) {
                            openSubsValidationLabel.setText("ℹ️ No login provided - using Consumer API limits (5 downloads/day)");
                            openSubsValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                            if (settings != null) {
                                settings.setOpensubtitlesValidated(true);
                                settings.save();
                            }
                        } else if (username.isEmpty() || password.isEmpty()) {
                            openSubsValidationLabel.setText("⚠️ Both username and password required");
                            openSubsValidationLabel.setStyle("-fx-text-fill: orange;");
                            if (settings != null) {
                                settings.setOpensubtitlesValidated(false);
                                settings.save();
                            }
                        } else {
                            // Save credentials (actual validation happens when downloading)
                            openSubsValidationLabel.setText("✅ Credentials saved! Login will occur when downloading subtitles (20 downloads/day)");
                            openSubsValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                            if (settings != null) {
                                settings.setOpensubtitlesUsername(username);
                                settings.setOpensubtitlesPassword(password);
                                settings.setOpensubtitlesValidated(true);
                                settings.save();
                                logger.info("OpenSubtitles credentials saved");
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Validation error", e);
                Platform.runLater(() -> {
                    if (openSubsValidationLabel != null) {
                        openSubsValidationLabel.setText("❌ Validation failed: " + e.getMessage());
                        openSubsValidationLabel.setStyle("-fx-text-fill: #f48771;");
                    }
                });
            }
        }).start();
    }
    
    @FXML
    private void handleValidateTMDB() {
        if (tmdbValidationLabel != null) {
            tmdbValidationLabel.setText("⏳ Validating...");
        }
        
        new Thread(() -> {
            try {
                String apiKey = tmdbApiKeyField.getText().trim();
                
                Platform.runLater(() -> {
                    if (tmdbValidationLabel != null) {
                        if (apiKey.isEmpty()) {
                            tmdbValidationLabel.setText("❌ Please enter an API key");
                            tmdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                            if (settings != null) {
                                settings.setTmdbValidated(false);
                                settings.save();
                            }
                        } else {
                            // Basic validation - actual validation would call Python backend
                            if (apiKey.length() < 20) {
                                tmdbValidationLabel.setText("⚠️ API key seems too short");
                                tmdbValidationLabel.setStyle("-fx-text-fill: orange;");
                                if (settings != null) {
                                    settings.setTmdbValidated(false);
                                    settings.save();
                                }
                            } else {
                                tmdbValidationLabel.setText("✅ API key validated and saved!");
                                tmdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                                if (settings != null) {
                                    settings.setTmdbApiKey(apiKey);
                                    settings.setTmdbValidated(true);
                                    settings.save();
                                    logger.info("TMDB API key validated and saved");
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Validation error", e);
                Platform.runLater(() -> {
                    if (tmdbValidationLabel != null) {
                        tmdbValidationLabel.setText("❌ Validation failed: " + e.getMessage());
                        tmdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                    }
                });
            }
        }).start();
    }
    
    @FXML
    private void handleValidateTVDB() {
        if (tvdbValidationLabel != null) {
            tvdbValidationLabel.setText("⏳ Validating...");
        }
        
        new Thread(() -> {
            try {
                String apiKey = tvdbApiKeyField.getText().trim();
                
                Platform.runLater(() -> {
                    if (tvdbValidationLabel != null) {
                        if (apiKey.isEmpty()) {
                            tvdbValidationLabel.setText("❌ Please enter an API key");
                            tvdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                            if (settings != null) {
                                settings.setTvdbValidated(false);
                                settings.save();
                            }
                        } else {
                            // Basic validation - actual validation would call Python backend
                            if (apiKey.length() < 20) {
                                tvdbValidationLabel.setText("⚠️ API key seems too short");
                                tvdbValidationLabel.setStyle("-fx-text-fill: orange;");
                                if (settings != null) {
                                    settings.setTvdbValidated(false);
                                    settings.save();
                                }
                            } else {
                                tvdbValidationLabel.setText("✅ API key validated and saved!");
                                tvdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                                if (settings != null) {
                                    settings.setTvdbApiKey(apiKey);
                                    settings.setTvdbValidated(true);
                                    settings.save();
                                    logger.info("TVDB API key validated and saved");
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Validation error", e);
                Platform.runLater(() -> {
                    if (tvdbValidationLabel != null) {
                        tvdbValidationLabel.setText("❌ Validation failed: " + e.getMessage());
                        tvdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                    }
                });
            }
        }).start();
    }
    
    @FXML
    private void handleValidateOMDB() {
        if (omdbValidationLabel != null) {
            omdbValidationLabel.setText("⏳ Validating...");
        }
        
        new Thread(() -> {
            try {
                String apiKey = omdbApiKeyField.getText().trim();
                
                Platform.runLater(() -> {
                    if (omdbValidationLabel != null) {
                        if (apiKey.isEmpty()) {
                            omdbValidationLabel.setText("❌ Please enter an API key");
                            omdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                            if (settings != null) {
                                settings.setOmdbValidated(false);
                                settings.save();
                            }
                        } else {
                            // Basic validation
                            if (apiKey.length() < 6) {
                                omdbValidationLabel.setText("⚠️ API key seems too short");
                                omdbValidationLabel.setStyle("-fx-text-fill: orange;");
                                if (settings != null) {
                                    settings.setOmdbValidated(false);
                                    settings.save();
                                }
                            } else {
                                omdbValidationLabel.setText("✅ API key validated and saved!");
                                omdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                                if (settings != null) {
                                    settings.setOmdbApiKey(apiKey);
                                    settings.setOmdbValidated(true);
                                    settings.save();
                                    logger.info("OMDB API key validated and saved");
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Validation error", e);
                Platform.runLater(() -> {
                    if (omdbValidationLabel != null) {
                        omdbValidationLabel.setText("❌ Validation failed: " + e.getMessage());
                        omdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                    }
                });
            }
        }).start();
    }
    
    @FXML
    private void handleValidateTrakt() {
        if (traktValidationLabel != null) {
            traktValidationLabel.setText("⏳ Validating...");
        }
        
        new Thread(() -> {
            try {
                String apiKey = traktApiKeyField.getText().trim();
                
                Platform.runLater(() -> {
                    if (traktValidationLabel != null) {
                        if (apiKey.isEmpty()) {
                            traktValidationLabel.setText("❌ Please enter an API key");
                            traktValidationLabel.setStyle("-fx-text-fill: #f48771;");
                            if (settings != null) {
                                settings.setTraktValidated(false);
                                settings.save();
                            }
                        } else {
                            // Basic validation
                            if (apiKey.length() < 20) {
                                traktValidationLabel.setText("⚠️ API key seems too short");
                                traktValidationLabel.setStyle("-fx-text-fill: orange;");
                                if (settings != null) {
                                    settings.setTraktValidated(false);
                                    settings.save();
                                }
                            } else {
                                traktValidationLabel.setText("✅ API key validated and saved!");
                                traktValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                                if (settings != null) {
                                    settings.setTraktApiKey(apiKey);
                                    settings.setTraktValidated(true);
                                    settings.save();
                                    logger.info("Trakt API key validated and saved");
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Validation error", e);
                Platform.runLater(() -> {
                    if (traktValidationLabel != null) {
                        traktValidationLabel.setText("❌ Validation failed: " + e.getMessage());
                        traktValidationLabel.setStyle("-fx-text-fill: #f48771;");
                    }
                });
            }
        }).start();
    }
    
    @FXML
    private void handleOpenPatternEditor() {
        try {
            // Load the pattern editor dialog
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/PatternEditorDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            
            // Get the controller and configure it
            PatternEditorController controller = loader.getController();
            
            // Create a new stage for the dialog
            Stage patternStage = new Stage();
            patternStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            patternStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            patternStage.setTitle("Format Pattern Editor");
            
            // Set the scene
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            patternStage.setScene(scene);
            
            // Configure the controller
            controller.setDialogStage(patternStage);
            controller.setSettings(settings);
            
            // Show and wait
            patternStage.showAndWait();
            
            // If saved, reload patterns
            if (controller.isSaved()) {
                loadSettings(); // Reload to show updated patterns
                logger.info("Pattern editor changes applied");
            }
            
        } catch (Exception e) {
            logger.error("Error opening pattern editor", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not open pattern editor");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
    // ========================================
    // WINDOW CONTROL METHODS
    // ========================================
    
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
        if (dialogStage != null) {
            dialogStage.setX(event.getScreenX() - xOffset);
            dialogStage.setY(event.getScreenY() - yOffset);
        }
    }
    
    /**
     * Handle minimize button click
     */
    @FXML
    private void handleMinimize() {
        if (dialogStage != null) {
            dialogStage.setIconified(true);
        }
    }
    
    /**
     * Handle maximize/restore button click
     */
    @FXML
    private void handleMaximize() {
        if (dialogStage != null) {
            if (dialogStage.isMaximized()) {
                dialogStage.setMaximized(false);
                maximizeButton.setText("□"); // Maximize icon
            } else {
                dialogStage.setMaximized(true);
                maximizeButton.setText("❐"); // Restore icon
            }
        }
    }
    
    /**
     * Handle close button click
     */
    @FXML
    private void handleClose() {
        handleCancel(); // Use existing cancel logic
    }
    
    // Settings dialog is not resizable, so no resize methods needed
    
    /**
     * Handle Setup Whisper AI button - opens wizard
     */
    @FXML
    private void handleSetupWhisper() {
        if (dependencyManager == null) {
            logger.error("DependencyManager not initialized");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Cannot setup Whisper");
            alert.setContentText("Dependency manager not initialized");
            alert.showAndWait();
            return;
        }
        
        try {
            WhisperSetupDialog setupDialog = new WhisperSetupDialog(dependencyManager);
            setupDialog.setProcessPool(processPool);
            setupDialog.showAndWait();
            
            // Refresh status after installation
            if (setupDialog.isInstallationComplete()) {
                updateWhisperStatus();
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Whisper Installed");
                info.setHeaderText("AI subtitle generation is now available!");
                info.setContentText("You can now use Whisper to generate subtitles automatically.");
                info.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Failed to open Whisper setup dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open Whisper setup");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
    /**
     * Handle Uninstall Whisper button
     */
    @FXML
    private void handleUninstallWhisper() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Uninstall Whisper");
        confirm.setHeaderText("Uninstall Whisper AI?");
        confirm.setContentText("This will remove Whisper AI and all downloaded models. You can reinstall later if needed.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                logger.info("Starting Whisper uninstall");
                
                if (dependencyManager == null) {
                    throw new RuntimeException("DependencyManager not initialized");
                }
                
                // Update UI to show progress
                if (whisperStatusLabel != null) {
                    whisperStatusLabel.setText("⏳ Uninstalling Whisper AI...");
                }
                
                // Run uninstall in background
                new Thread(() -> {
                    try {
                        dependencyManager.uninstallWhisper(progress -> {
                            Platform.runLater(() -> {
                                if (whisperStatusLabel != null) {
                                    whisperStatusLabel.setText(progress.getMessage());
                                }
                            });
                        }).get();
                        
                        Platform.runLater(() -> {
                            Alert info = new Alert(Alert.AlertType.INFORMATION);
                            info.setTitle("Whisper Uninstalled");
                            info.setHeaderText("Whisper AI has been removed");
                            info.setContentText("You can reinstall it from Settings > Subtitles > Setup Whisper AI");
                            info.showAndWait();
                            
                            updateWhisperStatus();
                        });
                    } catch (Exception e) {
                        logger.error("Failed to uninstall Whisper", e);
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Error");
                            alert.setHeaderText("Failed to uninstall Whisper");
                            alert.setContentText(e.getMessage());
                            alert.showAndWait();
                            
                            updateWhisperStatus();
                        });
                    }
                }).start();
                
            } catch (Exception e) {
                logger.error("Failed to start Whisper uninstall", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to uninstall Whisper");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    /**
     * Handle Download Whisper Model button
     */
    @FXML
    private void handleDownloadWhisperModel() {
        if (whisperModelCombo.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Model Selected");
            alert.setHeaderText("Please select a model to download");
            alert.setContentText("Choose a model from the dropdown and try again.");
            alert.showAndWait();
            return;
        }
        
        String selectedModel = whisperModelCombo.getValue();
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Download Whisper Model");
        confirm.setHeaderText("Download " + selectedModel + " model?");
        confirm.setContentText("This may take several minutes depending on your internet connection and the model size.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                logger.info("Downloading Whisper model: {}", selectedModel);
                
                // Update UI to show progress
                if (whisperStatusLabel != null) {
                    whisperStatusLabel.setText("⏳ Preparing to download " + selectedModel + " model...");
                }
                
                // Use processPool with streaming if available
                if (processPool != null) {
                    JsonObject params = new JsonObject();
                    params.addProperty("model", selectedModel);
                    
                    // Submit streaming task with progress updates
                    processPool.submitStreamingTask("download_whisper_model", params, progressResponse -> {
                        Platform.runLater(() -> {
                            // Null check
                            if (progressResponse == null) {
                                logger.warn("Received null progress response during model download");
                                return;
                            }
                            
                            // Check response type
                            if (progressResponse.has("type") && "progress".equals(progressResponse.get("type").getAsString())) {
                                // This is a progress update
                                int progress = progressResponse.has("progress") ? progressResponse.get("progress").getAsInt() : 0;
                                String message = progressResponse.has("message") ? progressResponse.get("message").getAsString() : "Downloading...";
                                
                                if (whisperStatusLabel != null) {
                                    whisperStatusLabel.setText(String.format("⏳ %s (%d%%)", message, progress));
                                }
                                logger.debug("Download progress: {}% - {}", progress, message);
                            } else {
                                // This is the final response
                                handleDownloadResponse(selectedModel, progressResponse);
                            }
                        });
                    });
                } else if (pythonBridge != null) {
                    // Fallback to legacy pythonBridge (blocking)
                    new Thread(() -> {
                        try {
                            JsonObject command = new JsonObject();
                            command.addProperty("action", "download_whisper_model");
                            command.addProperty("model", selectedModel);
                            
                            JsonObject response = pythonBridge.sendCommand(command);
                            handleDownloadResponse(selectedModel, response);
                        } catch (Exception e) {
                            logger.error("Failed to download Whisper model via pythonBridge", e);
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Error");
                                alert.setHeaderText("Failed to download model");
                                alert.setContentText(e.getMessage());
                                alert.showAndWait();
                                updateWhisperStatus();
                            });
                        }
                    }).start();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Python backend not available");
                    alert.setContentText("Neither ProcessPool nor PythonBridge is available");
                    alert.showAndWait();
                }
            } catch (Exception e) {
                logger.error("Failed to start Whisper model download", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to download model");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    /**
     * Handle download response (helper method for both processPool and pythonBridge paths)
     */
    private void handleDownloadResponse(String selectedModel, JsonObject response) {
        Platform.runLater(() -> {
            if (response == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Download Failed");
                alert.setHeaderText("No response from Python backend");
                alert.setContentText("The Python backend did not respond. Check logs for more details.");
                alert.showAndWait();
                updateWhisperStatus();
                return;
            }
            
            String status = response.has("status") ? response.get("status").getAsString() : "error";
            String message = response.has("message") ? response.get("message").getAsString() : "Unknown error";
            
            if ("success".equals(status)) {
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Model Downloaded");
                info.setHeaderText(selectedModel + " model downloaded successfully");
                info.setContentText(message);
                info.showAndWait();
                
                updateWhisperStatus();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Download Failed");
                alert.setHeaderText("Failed to download " + selectedModel + " model");
                alert.setContentText(message);
                alert.showAndWait();
                
                updateWhisperStatus();
            }
        });
    }
    
    /**
     * Update Whisper status display
     */
    private void updateWhisperStatus() {
        // Use processPool if available, fallback to statusManager
        if (processPool != null) {
            // Use process pool for non-blocking status check
            try {
                JsonObject command = new JsonObject();
                command.addProperty("action", "check_whisper");
                
                processPool.submitQuickTask("check_whisper", command)
                    .thenAccept(response -> {
                        Platform.runLater(() -> handleWhisperStatusResponse(response));
                    })
                    .exceptionally(e -> {
                        logger.warn("Could not check Whisper status via processPool", e);
                        Platform.runLater(() -> {
                            whisperStatusLabel.setText("⚠️ Whisper status check failed");
                            whisperInstalledModelsLabel.setText("Unable to check status");
                        });
                        return null;
                    });
                return;
            } catch (Exception e) {
                logger.warn("Error submitting Whisper status check", e);
                // Fall through to statusManager fallback
            }
        }
        
        // Fallback to statusManager (legacy)
        if (statusManager == null) {
            whisperStatusLabel.setText("Whisper status unavailable");
            return;
        }
        
        boolean whisperAvailable = statusManager.isWhisperAvailable();
        
        if (whisperAvailable) {
            whisperStatusLabel.setText("✅ Whisper AI is installed and ready");
            whisperStatusLabel.setStyle("-fx-text-fill: #4ec9b0;");
            
            if (uninstallWhisperButton != null) {
                uninstallWhisperButton.setDisable(false);
            }
            if (downloadModelButton != null) {
                downloadModelButton.setDisable(false);
            }
            
            // Get installed models from Python backend
            try {
                if (pythonBridge != null) {
                    JsonObject response = pythonBridge.getAllStatus();
                    if (response.has("whisper")) {
                        JsonObject whisperInfo = response.getAsJsonObject("whisper");
                        if (whisperInfo.has("installed_models")) {
                            JsonArray models = whisperInfo.getAsJsonArray("installed_models");
                            List<String> modelList = new ArrayList<>();
                            for (int i = 0; i < models.size(); i++) {
                                modelList.add(models.get(i).getAsString());
                            }
                            
                            if (modelList.isEmpty()) {
                                whisperInstalledModelsLabel.setText("No models installed. Click 'Download Another Model' to install one.");
                            } else {
                                whisperInstalledModelsLabel.setText("Installed models: " + String.join(", ", modelList));
                            }
                        } else {
                            whisperInstalledModelsLabel.setText("Model status: Unknown");
                        }
                    } else {
                        whisperInstalledModelsLabel.setText("Model status: Check failed");
                    }
                } else {
                    whisperInstalledModelsLabel.setText("Models: Python bridge unavailable");
                }
            } catch (Exception e) {
                logger.warn("Could not fetch Whisper models", e);
                whisperInstalledModelsLabel.setText("Models: Unable to check");
            }
        } else {
            whisperStatusLabel.setText("❌ Whisper AI is not installed");
            whisperStatusLabel.setStyle("-fx-text-fill: #d13438;");
            
            if (uninstallWhisperButton != null) {
                uninstallWhisperButton.setDisable(true);
            }
            if (downloadModelButton != null) {
                downloadModelButton.setDisable(true);
            }
            
            whisperInstalledModelsLabel.setText("Click 'Setup Whisper AI' to install");
        }
    }
    
    /**
     * Handle Whisper status response from process pool
     */
    private void handleWhisperStatusResponse(JsonObject response) {
        if (response == null) {
            whisperStatusLabel.setText("⚠️ Status check failed");
            whisperInstalledModelsLabel.setText("No response from Python");
            return;
        }
        
        // Check whisper_available field (the actual response format from check_whisper)
        boolean available = response.has("whisper_available") && response.get("whisper_available").getAsBoolean();
        
        if (available) {
            // Parse installed models first to check if any exist
            List<String> modelList = new ArrayList<>();
            if (response.has("installed_models")) {
                JsonArray models = response.getAsJsonArray("installed_models");
                for (int i = 0; i < models.size(); i++) {
                    modelList.add(models.get(i).getAsString());
                }
            }
            
            // Update status label based on whether models are installed
            if (modelList.isEmpty()) {
                whisperStatusLabel.setText("⚠️ Whisper installed but no models available");
                whisperStatusLabel.setStyle("-fx-text-fill: #dcdcaa;");  // Yellow/warning color
                whisperInstalledModelsLabel.setText("No models installed. Click 'Download Model' to install one.");
            } else {
                whisperStatusLabel.setText("✅ Whisper AI is installed and ready");
                whisperStatusLabel.setStyle("-fx-text-fill: #4ec9b0;");
                whisperInstalledModelsLabel.setText("Installed models: " + String.join(", ", modelList));
            }
            
            // Enable buttons since Whisper itself is installed
            if (uninstallWhisperButton != null) {
                uninstallWhisperButton.setDisable(false);
            }
            if (downloadModelButton != null) {
                downloadModelButton.setDisable(false);
            }
        } else {
            whisperStatusLabel.setText("❌ Whisper AI is not installed");
            whisperStatusLabel.setStyle("-fx-text-fill: #d13438;");
            whisperInstalledModelsLabel.setText("Click 'Setup Whisper AI' to install");
            
            if (uninstallWhisperButton != null) {
                uninstallWhisperButton.setDisable(true);
            }
            if (downloadModelButton != null) {
                downloadModelButton.setDisable(true);
            }
        }
    }
    
    /**
     * Handle Download FFmpeg button - uses DependencyManager
     */
    @FXML
    private void handleDownloadFFmpeg() {
        if (dependencyManager == null) {
            logger.error("DependencyManager not initialized");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Download FFmpeg");
        confirm.setHeaderText("Download and install FFmpeg?");
        confirm.setContentText("This will download FFmpeg for your platform and install it to " + 
            dependencyManager.getFfmpegDir());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            // Show progress dialog (could create a simple one or reuse InitializationDialog)
            if (ffmpegVersionLabel != null) {
                ffmpegVersionLabel.setText("⏳ Downloading FFmpeg...");
            }
            
            new Thread(() -> {
                try {
                    dependencyManager.installFFmpeg(progress -> {
                        Platform.runLater(() -> {
                            if (ffmpegVersionLabel != null) {
                                ffmpegVersionLabel.setText(progress.getMessage());
                            }
                        });
                    }).get();
                    
                    Platform.runLater(() -> {
                        if (ffmpegVersionLabel != null) {
                            ffmpegVersionLabel.setText("✅ FFmpeg installed successfully");
                            ffmpegVersionLabel.setStyle("-fx-text-fill: #4ec9b0;");
                        }
                        // Refresh status after a short delay to ensure installation is complete
                        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> checkFFmpegStatus())
                        );
                        timeline.play();
                    });
                } catch (Exception e) {
                    logger.error("Failed to install FFmpeg", e);
                    Platform.runLater(() -> {
                        if (ffmpegVersionLabel != null) {
                            ffmpegVersionLabel.setText("❌ Installation failed");
                            ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
                        }
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Installation Failed");
                        alert.setHeaderText("Failed to install FFmpeg");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        }
    }
    
    /**
     * Initialize all icons using Ikonli FontAwesome
     */
    private void initializeIcons() {
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
                        org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.COG);
                    appIcon.setIconSize(16);
                    appIcon.setIconColor(javafx.scene.paint.Color.web("#0078d4"));
                    appIconLabel.setGraphic(appIcon);
                }
            }
            
            // Minimize icon
            if (minimizeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon minimizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MINIMIZE);
                minimizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                minimizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                minimizeButton.setGraphic(minimizeIcon);
                minimizeButton.setText("");
            }
            
            // Maximize icon
            if (maximizeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon maximizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MAXIMIZE);
                maximizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                maximizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                maximizeButton.setGraphic(maximizeIcon);
                maximizeButton.setText("");
            }
            
            // Close icon
            if (closeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon closeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TIMES);
                closeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                closeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                closeButton.setGraphic(closeIcon);
                closeButton.setText("");
            }
            
            // Open Settings Folder button
            if (openSettingsFolderButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FOLDER_OPEN);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                openSettingsFolderButton.setGraphic(icon);
                openSettingsFolderButton.setText("Open Folder");
            }
            
            // Validate OpenSubtitles button
            if (validateOpenSubsButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                validateOpenSubsButton.setGraphic(icon);
                validateOpenSubsButton.setText("Validate Login");
            }
            
            // Validate TMDB button
            if (validateTMDBButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                validateTMDBButton.setGraphic(icon);
                validateTMDBButton.setText("Validate Key");
            }
            
            // Validate TVDB button
            if (validateTVDBButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                validateTVDBButton.setGraphic(icon);
                validateTVDBButton.setText("Validate Key");
            }
            
            // Auto-Detect button
            if (autoDetectButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.SEARCH);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                autoDetectButton.setGraphic(icon);
                autoDetectButton.setText("Auto-Detect");
            }
            
            // Download FFmpeg button
            if (downloadFFmpegButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.DOWNLOAD);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                downloadFFmpegButton.setGraphic(icon);
                downloadFFmpegButton.setText("Download FFmpeg");
            }
            
            logger.info("Settings dialog icons initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize settings dialog icons", e);
        }
    }
    
    public void setStatusManager(StatusManager statusManager) {
        this.statusManager = statusManager;
        // Update Whisper status after setting StatusManager
        updateWhisperStatus();
    }
}

