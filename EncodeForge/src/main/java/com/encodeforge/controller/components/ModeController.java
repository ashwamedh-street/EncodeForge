package com.encodeforge.controller.components;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import java.io.File;
import java.util.List;
import java.util.Set;


/**
 * ModeController - Handle mode switching (Encoder, Subtitle, Renamer)
 */
public class ModeController implements ISubController {
    
    // TODO: Copy currentMode field from MainController line 218
    // TODO: Copy mode button FXML fields from MainController lines 107-112
    // TODO: Copy mode layout FXML fields from MainController lines 124-132
    
    public ModeController() {
        // TODO: Initialize with FXML fields
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== handleEncoderMode() ==========

    @FXML
    private void handleEncoderMode() {
        currentMode = "encoder";
        showModePanel(encoderQuickSettings);
        showModeLayout(encoderModeLayout);
        updateModeButtonSelection(encoderModeButton);
        updateSelectedFilesLabel();
        log("Switched to encoder mode");
    }


    // ========== handleSubtitleMode() ==========

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


    // ========== handleRenamerMode() ==========

    @FXML
    private void handleRenamerMode() {
        currentMode = "renamer";
        showModePanel(renamerQuickSettings);
        showModeLayout(renamerModeLayout);
        updateModeButtonSelection(renamerModeButton);
        
        // Immediately load files and show original names
        if (!queuedFiles.isEmpty()) {
            updateRenamePreview();
        }
        updateSelectedFilesLabel();
        log("Switched to renamer mode");
    }


    // ========== updateModeButtonSelection() ==========

    private void updateModeButtonSelection(Button selectedButton) {
        // Remove selected style from all mode buttons
        encoderModeButton.getStyleClass().removeAll("selected");
        subtitleModeButton.getStyleClass().removeAll("selected");
        renamerModeButton.getStyleClass().removeAll("selected");
        
        // Add selected style to the chosen button
        selectedButton.getStyleClass().add("selected");
    }


    // ========== showModeLayout() ==========

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


    // ========== showModePanel() ==========

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

    // ========== currentMode field ==========

    private String currentMode = "encoder";


    // ========== Mode button FXML fields ==========

    @FXML private Button encoderModeButton;
    @FXML private Button subtitleModeButton;
    @FXML private Button renamerModeButton;
    @FXML private Button addFilesButton;
    @FXML private Button addFolderButton;
    @FXML private Button settingsButton;


    // ========== Mode layout FXML fields ==========

    @FXML private javafx.scene.layout.VBox encoderQuickSettings;
    @FXML private javafx.scene.layout.VBox subtitleQuickSettings;
    @FXML private javafx.scene.layout.VBox renamerQuickSettings;
    
    // Mode Layouts
    @FXML private javafx.scene.control.SplitPane encoderModeLayout;
    @FXML private javafx.scene.control.SplitPane subtitleModeLayout;
    @FXML private javafx.scene.control.SplitPane renamerModeLayout;
    


    // ========== Quick settings VBox fields ==========

    // Mode-Specific Quick Settings
    @FXML private javafx.scene.layout.VBox encoderQuickSettings;
    @FXML private javafx.scene.layout.VBox subtitleQuickSettings;
    @FXML private javafx.scene.layout.VBox renamerQuickSettings;

}

