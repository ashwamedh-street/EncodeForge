package com.ffmpeg.gui.controller;

import com.ffmpeg.gui.model.ConversionSettings;
import com.ffmpeg.gui.service.PythonBridge;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private boolean applied = false;
    
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
    @FXML private TextField outputDirField;
    @FXML private CheckBox deleteOriginalCheck;
    @FXML private CheckBox overwriteCheck;
    @FXML private CheckBox preserveDateCheck;
    @FXML private Spinner<Integer> concurrentSpinner;
    
    // FFmpeg Settings
    @FXML private TextField ffmpegPathField;
    @FXML private TextField ffprobePathField;
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
    @FXML private CheckBox enableWhisperCheck;
    @FXML private VBox whisperOptions;
    @FXML private ComboBox<String> whisperModelCombo;
    @FXML private TextField whisperLanguagesField;
    @FXML private CheckBox downloadSubsCheck;
    
    // Subtitle Settings (continued)
    @FXML private TextField opensubtitlesApiKeyField;
    @FXML private TextField opensubtitlesUsernameField;
    @FXML private PasswordField opensubtitlesPasswordField;
    @FXML private Label openSubsValidationLabel;
    
    // Metadata Settings
    @FXML private TextField namingPatternField;
    @FXML private CheckBox createSubfoldersCheck;
    @FXML private TextField tmdbApiKeyField;
    @FXML private TextField tvdbApiKeyField;
    @FXML private Label tmdbValidationLabel;
    @FXML private Label tvdbValidationLabel;
    
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
            settingsLocationLabel.setText(ConversionSettings.getSettingsFilePath());
        }
        
        // Select first category by default
        categoryList.getSelectionModel().selectFirst();
        
        // Setup Whisper options visibility
        enableWhisperCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            whisperOptions.setVisible(newVal);
            whisperOptions.setManaged(newVal);
        });
        
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
    }
    
    private void initializeComboBoxes() {
        // Output format
        if (outputFormatCombo != null) {
            outputFormatCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "MP4", "MKV", "AVI", "MOV", "WebM"));
            outputFormatCombo.setValue("MP4");
        }
        
        // Video codec
        if (videoCodecCombo != null) {
            videoCodecCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                "Copy (no re-encode)", "H.264 (CPU)", "H.264 NVENC (GPU)", 
                "H.265 (CPU)", "H.265 NVENC (GPU)", "VP9", "AV1"));
            videoCodecCombo.setValue("H.264 NVENC (GPU)");
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
    }
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    public void setSettings(ConversionSettings settings) {
        this.settings = settings;
        loadSettings();
    }
    
    public void setPythonBridge(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
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
        
        hwDecodeCheck.setSelected(settings.isHardwareDecoding());
        
        // Load API keys
        if (tmdbApiKeyField != null) tmdbApiKeyField.setText(settings.getTmdbApiKey());
        if (tvdbApiKeyField != null) tvdbApiKeyField.setText(settings.getTvdbApiKey());
        if (opensubtitlesApiKeyField != null) opensubtitlesApiKeyField.setText(settings.getOpensubtitlesApiKey());
        if (opensubtitlesUsernameField != null) opensubtitlesUsernameField.setText(settings.getOpensubtitlesUsername());
        if (opensubtitlesPasswordField != null) opensubtitlesPasswordField.setText(settings.getOpensubtitlesPassword());
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
        
        settings.setHardwareDecoding(hwDecodeCheck.isSelected());
        
        // Save API keys
        if (tmdbApiKeyField != null) settings.setTmdbApiKey(tmdbApiKeyField.getText());
        if (tvdbApiKeyField != null) settings.setTvdbApiKey(tvdbApiKeyField.getText());
        if (opensubtitlesApiKeyField != null) settings.setOpensubtitlesApiKey(opensubtitlesApiKeyField.getText());
        if (opensubtitlesUsernameField != null) settings.setOpensubtitlesUsername(opensubtitlesUsernameField.getText());
        if (opensubtitlesPasswordField != null) settings.setOpensubtitlesPassword(opensubtitlesPasswordField.getText());
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
        if (pythonBridge == null) {
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject response = pythonBridge.checkFFmpeg();
                
                Platform.runLater(() -> {
                    if (response.has("status") && response.get("status").getAsString().equals("error")) {
                        ffmpegVersionLabel.setText("❌ FFmpeg: Not Found");
                        ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
                        return;
                    }
                    
                    if (response.has("ffmpeg_available") && response.get("ffmpeg_available").getAsBoolean()) {
                        String version = response.get("ffmpeg_version").getAsString();
                        String path = response.get("ffmpeg_path").getAsString();
                        
                        ffmpegVersionLabel.setText("✅ FFmpeg: " + version);
                        ffmpegVersionLabel.setStyle("-fx-text-fill: #4ec9b0;");
                        
                        // Update path fields if empty
                        if (ffmpegPathField.getText().isEmpty()) {
                            ffmpegPathField.setText(path);
                        }
                        if (ffprobePathField.getText().isEmpty()) {
                            ffprobePathField.setText(path.replace("ffmpeg", "ffprobe"));
                        }
                        
                        logger.info("FFmpeg detected: " + version + " at " + path);
                    } else {
                        ffmpegVersionLabel.setText("⚠️ FFmpeg: Not Detected");
                        ffmpegVersionLabel.setStyle("-fx-text-fill: #ce9178;");
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error checking FFmpeg status", e);
                Platform.runLater(() -> {
                    ffmpegVersionLabel.setText("❌ Error checking FFmpeg");
                    ffmpegVersionLabel.setStyle("-fx-text-fill: #f48771;");
                });
            }
        }).start();
    }
    
    @FXML
    private void handleDownloadFFmpeg() {
        // This will be handled by MainController via callback
        logger.info("Download FFmpeg requested");
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
        
        // TODO: Implement actual API validation via Python backend
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate API call
                Platform.runLater(() -> {
                    if (openSubsValidationLabel != null) {
                        if (opensubtitlesUsernameField.getText().isEmpty() || 
                            opensubtitlesPasswordField.getText().isEmpty()) {
                            openSubsValidationLabel.setText("❌ Please enter username and password");
                            openSubsValidationLabel.setStyle("-fx-text-fill: #f48771;");
                        } else {
                            openSubsValidationLabel.setText("✅ Login successful!");
                            openSubsValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                        }
                    }
                });
            } catch (InterruptedException e) {
                logger.error("Validation interrupted", e);
            }
        }).start();
    }
    
    @FXML
    private void handleValidateTMDB() {
        if (tmdbValidationLabel != null) {
            tmdbValidationLabel.setText("⏳ Validating...");
        }
        
        // TODO: Implement actual API validation via Python backend
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate API call
                Platform.runLater(() -> {
                    if (tmdbValidationLabel != null) {
                        if (tmdbApiKeyField.getText().isEmpty()) {
                            tmdbValidationLabel.setText("❌ Please enter an API key");
                            tmdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                        } else {
                            tmdbValidationLabel.setText("✅ API key is valid!");
                            tmdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                        }
                    }
                });
            } catch (InterruptedException e) {
                logger.error("Validation interrupted", e);
            }
        }).start();
    }
    
    @FXML
    private void handleValidateTVDB() {
        if (tvdbValidationLabel != null) {
            tvdbValidationLabel.setText("⏳ Validating...");
        }
        
        // TODO: Implement actual API validation via Python backend
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate API call
                Platform.runLater(() -> {
                    if (tvdbValidationLabel != null) {
                        if (tvdbApiKeyField.getText().isEmpty()) {
                            tvdbValidationLabel.setText("❌ Please enter an API key");
                            tvdbValidationLabel.setStyle("-fx-text-fill: #f48771;");
                        } else {
                            tvdbValidationLabel.setText("✅ API key is valid!");
                            tvdbValidationLabel.setStyle("-fx-text-fill: #4ec9b0;");
                        }
                    }
                });
            } catch (InterruptedException e) {
                logger.error("Validation interrupted", e);
            }
        }).start();
    }
}

