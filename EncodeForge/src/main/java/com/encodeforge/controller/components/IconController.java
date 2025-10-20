package com.encodeforge.controller.components;

import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import java.io.File;
import java.util.Set;


/**
 * IconController - Initialize all UI icons and button graphics
 */
public class IconController implements ISubController {
        
    public IconController() {
        // TODO: Initialize
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== initializeWindowControlIcons() ==========

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


    // ========== initializeToolbarIcons() ==========

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
            
            // Renamer Mode button - Create stacked icon-above-text layout
            if (renamerModeButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EDIT);
                icon.setIconSize(18);  // Match mode-icon size
                icon.setIconColor(javafx.scene.paint.Color.WHITE);
                icon.getStyleClass().add("mode-icon");
                
                Label textLabel = new Label("Renamer");
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
                searchMetadataButton.setText("🔍 Search");
            }
            
            // TMDB Status button
            if (tmdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.FILM);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                tmdbStatusButton.setGraphic(icon);
                tmdbStatusButton.setText("🎬 TMDB: Not Setup");
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
                applyRenameButton.setText("✅ Apply Changes");
                applyRenameButton.setDisable(true);  // Disabled until search results load
            }
            
            // TVDB Status button
            if (tvdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TV);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                tvdbStatusButton.setGraphic(icon);
                tvdbStatusButton.setText("📺 TVDB");
            }
            
            // OMDB Status button
            if (omdbStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.DATABASE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                omdbStatusButton.setGraphic(icon);
                omdbStatusButton.setText("🎥 OMDB");
            }
            
            // Trakt Status button
            if (traktStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.STREAM);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                traktStatusButton.setGraphic(icon);
                traktStatusButton.setText("📊 Trakt");
            }
            
            // Fanart.tv Status button
            if (fanartStatusButton != null) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.IMAGE);
                icon.setIconSize(TOOLBAR_ICON_SIZE);
                icon.setIconColor(javafx.scene.paint.Color.web("#a0a0a0"));
                fanartStatusButton.setGraphic(icon);
                fanartStatusButton.setText("🎨 Fanart");
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

    // ========== Icon size constants ==========

    // Icon sizes
    private static final int WINDOW_CONTROL_ICON_SIZE = 14;
    private static final int TOOLBAR_ICON_SIZE = 14;
    private static final int SMALL_ICON_SIZE = 12;

}

