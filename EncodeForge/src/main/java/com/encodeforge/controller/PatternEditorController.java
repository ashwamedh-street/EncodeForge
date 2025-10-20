package com.encodeforge.controller;

import com.encodeforge.model.ConversionSettings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Pattern Editor Dialog
 */
public class PatternEditorController {
    private static final Logger logger = LoggerFactory.getLogger(PatternEditorController.class);
    
    private Stage dialogStage;
    private ConversionSettings settings;
    private boolean saved = false;
    private String initialPattern = "";
    private String currentPatternType = "TV Show";
    
    // Window dragging
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Window Controls
    @FXML private Label appIconLabel;
    @FXML private Button minimizeButton;
    @FXML private Button closeButton;
    
    // Pattern Type & Presets
    @FXML private ComboBox<String> patternTypeCombo;
    @FXML private ListView<String> presetsList;
    @FXML private Button loadPresetButton;
    
    // Pattern Editor
    @FXML private TextField patternField;
    
    // Token Buttons
    @FXML private Button titleTokenButton;
    @FXML private Button yearTokenButton;
    @FXML private Button seasonTokenButton;
    @FXML private Button episodeTokenButton;
    @FXML private Button episodeTitleTokenButton;
    @FXML private Button sTokenButton;
    @FXML private Button eTokenButton;
    
    // Action Buttons
    @FXML private Button resetButton;
    @FXML private Button cancelButton;
    @FXML private Button applyButton;
    @FXML private Button saveButton;
    
    // Preview Labels
    @FXML private Label tvExample1Input;
    @FXML private Label tvExample1Output;
    @FXML private Label movieExample1Input;
    @FXML private Label movieExample1Output;
    @FXML private Label animeExample1Input;
    @FXML private Label animeExample1Output;
    
    // Preset patterns
    private final Map<String, String[]> presetPatterns = new HashMap<>();
    
    @FXML
    public void initialize() {
        logger.info("Initializing Pattern Editor");
        
        // Initialize pattern types
        patternTypeCombo.getItems().addAll("TV Show", "Movie", "Anime");
        patternTypeCombo.setValue("TV Show");
        
        // Initialize presets
        initializePresets();
        loadPresetsForType("TV Show");
        
        // Set initial examples
        updatePreviewExamples();
        
        // Initialize icons (after UI is ready)
        Platform.runLater(this::initializeIcons);
        
        // Ensure pattern field has a default value if settings aren't loaded yet
        Platform.runLater(() -> {
            if (patternField != null && (patternField.getText() == null || patternField.getText().isEmpty())) {
                patternField.setText("{title} - S{season}E{episode} - {episodeTitle}");
                updatePreview();
                logger.info("Set default pattern in pattern field");
            }
        });
    }
    
    private void initializePresets() {
        // TV Show presets
        presetPatterns.put("TV Show", new String[]{
            "{title} - S{season}E{episode} - {episodeTitle}",
            "{title} - {S}{E} - {episodeTitle}",
            "{title} ({year}) - S{season}E{episode}",
            "{title} - Season {season} Episode {episode}",
            "{title}.S{season}E{episode}.{episodeTitle}",
            "{title}/{S}/{title}.{S}{E}.{episodeTitle}",
            "{title} {year}/Season {season}/{title} - {S}{E}",
            "[{title}] S{season}E{episode} - {episodeTitle}"
        });
        
        // Movie presets
        presetPatterns.put("Movie", new String[]{
            "{title} ({year})",
            "{title}.{year}",
            "{title} - {year}",
            "{title} [{year}]",
            "{year} - {title}",
            "{title} ({year})/Movies/{title} ({year})",
            "Movies/{title} ({year})",
            "{title}"
        });
        
        // Anime presets
        presetPatterns.put("Anime", new String[]{
            "{title} - {episode} - {episodeTitle}",
            "[{title}] - {episode} - {episodeTitle}",
            "{title} - Episode {episode}",
            "{title} ({year}) - {episode}",
            "{title}/{title} - {episode}",
            "{title} S{season}E{episode}",
            "{title} - {S}{E} - {episodeTitle}",
            "[{title}] {episode}"
        });
    }
    
    private void loadPresetsForType(String type) {
        presetsList.getItems().clear();
        String[] presets = presetPatterns.get(type);
        if (presets != null) {
            presetsList.getItems().addAll(presets);
        }
    }
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        if (dialogStage != null) {
            dialogStage.setResizable(false);
        }
    }
    
    public void setSettings(ConversionSettings settings) {
        this.settings = settings;
        // Load pattern after UI is ready
        Platform.runLater(this::loadCurrentPattern);
    }
    
    public void setPatternType(String type) {
        if (patternTypeCombo != null) {
            patternTypeCombo.setValue(type);
            handlePatternTypeChange();
        } else {
            // If combo is not ready yet, just update the current type
            currentPatternType = type;
            // Load pattern after UI is ready
            Platform.runLater(this::loadCurrentPattern);
        }
    }
    
    private void loadCurrentPattern() {
        if (settings == null) {
            logger.warn("Settings is null, cannot load pattern");
            return;
        }
        
        if (patternField == null) {
            logger.warn("Pattern field is null, cannot load pattern");
            return;
        }
        
        String pattern = "";
        switch (currentPatternType) {
            case "TV Show":
                pattern = settings.getTvShowPattern();
                break;
            case "Movie":
                pattern = settings.getMoviePattern();
                break;
            case "Anime":
                pattern = settings.getAnimePattern();
                break;
        }
        
        // Use default pattern if none is set
        if (pattern == null || pattern.isEmpty()) {
            switch (currentPatternType) {
                case "TV Show":
                    pattern = "{title} - S{season}E{episode} - {episodeTitle}";
                    break;
                case "Movie":
                    pattern = "{title} ({year})";
                    break;
                case "Anime":
                    pattern = "{title} - {episode} - {episodeTitle}";
                    break;
            }
        }
        
        logger.info("Loading pattern for {}: {}", currentPatternType, pattern);
        initialPattern = pattern;
        patternField.setText(pattern);
        updatePreview();
    }
    
    public boolean isSaved() {
        return saved;
    }
    
    @FXML
    private void handlePatternTypeChange() {
        currentPatternType = patternTypeCombo.getValue();
        loadPresetsForType(currentPatternType);
        loadCurrentPattern();
    }
    
    @FXML
    private void handlePresetSelect(MouseEvent event) {
        if (event.getClickCount() == 2) {
            handleLoadPreset();
        }
    }
    
    @FXML
    private void handleLoadPreset() {
        String selectedPreset = presetsList.getSelectionModel().getSelectedItem();
        if (selectedPreset != null) {
            patternField.setText(selectedPreset);
            updatePreview();
        }
    }
    
    @FXML
    private void handleInsertToken(javafx.event.ActionEvent event) {
        Button button = (Button) event.getSource();
        String token = button.getText();
        
        // Insert at cursor position
        int caretPosition = patternField.getCaretPosition();
        String currentText = patternField.getText();
        String newText = currentText.substring(0, caretPosition) + token + 
                        currentText.substring(caretPosition);
        patternField.setText(newText);
        patternField.positionCaret(caretPosition + token.length());
        
        updatePreview();
    }
    
    @FXML
    private void handlePatternChange() {
        updatePreview();
    }
    
    private void updatePreview() {
        String pattern = patternField.getText();
        if (pattern == null || pattern.isEmpty()) {
            clearPreviews();
            return;
        }
        
        // TV Show example
        Map<String, String> tvInfo = new HashMap<>();
        tvInfo.put("title", "Breaking Bad");
        tvInfo.put("show_title", "Breaking Bad");
        tvInfo.put("year", "2008");
        tvInfo.put("show_year", "2008");
        tvInfo.put("season", "01");
        tvInfo.put("episode", "01");
        tvInfo.put("episodeTitle", "Pilot");
        tvExample1Output.setText(formatPattern(pattern, tvInfo));
        
        // Movie example
        Map<String, String> movieInfo = new HashMap<>();
        movieInfo.put("title", "Inception");
        movieInfo.put("year", "2010");
        movieExample1Output.setText(formatPattern(pattern, movieInfo));
        
        // Anime example
        Map<String, String> animeInfo = new HashMap<>();
        animeInfo.put("title", "Naruto");
        animeInfo.put("show_title", "Naruto");
        animeInfo.put("year", "2002");
        animeInfo.put("season", "01");
        animeInfo.put("episode", "001");
        animeInfo.put("episodeTitle", "Enter: Naruto Uzumaki!");
        animeExample1Output.setText(formatPattern(pattern, animeInfo));
    }
    
    private String formatPattern(String pattern, Map<String, String> info) {
        String result = pattern;
        
        // Replace tokens
        result = result.replace("{title}", info.getOrDefault("title", info.getOrDefault("show_title", "Title")));
        result = result.replace("{year}", info.getOrDefault("year", info.getOrDefault("show_year", "2020")));
        result = result.replace("{season}", info.getOrDefault("season", "01"));
        result = result.replace("{episode}", info.getOrDefault("episode", "01"));
        result = result.replace("{episodeTitle}", info.getOrDefault("episodeTitle", "Episode Title"));
        result = result.replace("{S}", "S" + info.getOrDefault("season", "01"));
        result = result.replace("{E}", "E" + info.getOrDefault("episode", "01"));
        
        // Clean up multiple spaces
        result = result.replaceAll("\\s+", " ").trim();
        
        // Remove invalid filename characters
        result = result.replaceAll("[<>:\"/\\\\|?*]", "");
        
        return result;
    }
    
    private void clearPreviews() {
        tvExample1Output.setText("");
        movieExample1Output.setText("");
        animeExample1Output.setText("");
    }
    
    private void updatePreviewExamples() {
        // This is called on init to set up the example inputs
        tvExample1Input.setText("Breaking.Bad.S01E01.Pilot.mkv");
        movieExample1Input.setText("Inception.2010.1080p.mkv");
        animeExample1Input.setText("[SubGroup]Naruto.E001.mkv");
    }
    
    @FXML
    private void handleReset() {
        // Reset to default pattern for current type
        String defaultPattern = "";
        switch (currentPatternType) {
            case "TV Show":
                defaultPattern = "{title} - S{season}E{episode} - {episodeTitle}";
                break;
            case "Movie":
                defaultPattern = "{title} ({year})";
                break;
            case "Anime":
                defaultPattern = "{title} - {episode} - {episodeTitle}";
                break;
        }
        
        patternField.setText(defaultPattern);
        updatePreview();
    }
    
    @FXML
    private void handleCancel() {
        saved = false;
        dialogStage.close();
    }
    
    @FXML
    private void handleApply() {
        savePattern();
        saved = true;
    }
    
    @FXML
    private void handleSave() {
        handleApply();
        dialogStage.close();
    }
    
    private void savePattern() {
        if (settings == null) return;
        
        String pattern = patternField.getText();
        
        switch (currentPatternType) {
            case "TV Show":
                settings.setTvShowPattern(pattern);
                break;
            case "Movie":
                settings.setMoviePattern(pattern);
                break;
            case "Anime":
                settings.setAnimePattern(pattern);
                break;
        }
        
        settings.save();
        logger.info("Saved {} pattern: {}", currentPatternType, pattern);
    }
    
    // Window control methods
    @FXML
    private void handleTitleBarPressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }
    
    @FXML
    private void handleTitleBarDragged(MouseEvent event) {
        if (dialogStage != null) {
            dialogStage.setX(event.getScreenX() - xOffset);
            dialogStage.setY(event.getScreenY() - yOffset);
        }
    }
    
    @FXML
    private void handleMinimize() {
        if (dialogStage != null) {
            dialogStage.setIconified(true);
        }
    }
    
    @FXML
    private void handleClose() {
        handleCancel();
    }
    
    private void initializeIcons() {
        try {
            int iconSize = 14;
            
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
                    logger.warn("Failed to load app icon", e);
                    org.kordamp.ikonli.javafx.FontIcon appIcon = new org.kordamp.ikonli.javafx.FontIcon(
                        org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                    appIcon.setIconSize(16);
                    appIcon.setIconColor(javafx.scene.paint.Color.web("#0078d4"));
                    appIconLabel.setGraphic(appIcon);
                }
            }
            
            // Minimize icon
            if (minimizeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon minimizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MINIMIZE);
                minimizeIcon.setIconSize(14);
                minimizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                minimizeButton.setGraphic(minimizeIcon);
                minimizeButton.setText("");
            }
            
            // Close icon
            if (closeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon closeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TIMES);
                closeIcon.setIconSize(14);
                closeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                closeButton.setGraphic(closeIcon);
                closeButton.setText("");
            }
            
            // Load Preset button
            if (loadPresetButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.DOWNLOAD);
                icon.setIconSize(iconSize);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                loadPresetButton.setGraphic(icon);
            }
            
            // Token Buttons with icons
            if (titleTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.HEADING);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                titleTokenButton.setGraphic(icon);
            }
            
            if (yearTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CALENDAR);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                yearTokenButton.setGraphic(icon);
            }
            
            if (seasonTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.LIST_OL);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                seasonTokenButton.setGraphic(icon);
            }
            
            if (episodeTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.PLAY_CIRCLE);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                episodeTokenButton.setGraphic(icon);
            }
            
            if (episodeTitleTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TEXT_HEIGHT);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                episodeTitleTokenButton.setGraphic(icon);
            }
            
            if (sTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.SQUARE);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                sTokenButton.setGraphic(icon);
            }
            
            if (eTokenButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.ELLIPSIS_H);
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web("#4ec9b0"));
                eTokenButton.setGraphic(icon);
            }
            
            // Action buttons
            if (resetButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.UNDO);
                icon.setIconSize(iconSize);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                resetButton.setGraphic(icon);
            }
            
            if (cancelButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TIMES);
                icon.setIconSize(iconSize);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                cancelButton.setGraphic(icon);
            }
            
            if (applyButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CHECK);
                icon.setIconSize(iconSize);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                applyButton.setGraphic(icon);
            }
            
            if (saveButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.SAVE);
                icon.setIconSize(iconSize);
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                saveButton.setGraphic(icon);
            }
            
            logger.info("Pattern editor icons initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize pattern editor icons", e);
        }
    }
}

