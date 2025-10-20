package com.encodeforge.controller.components;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;


/**
 * LoggingController - Centralize logging and dialog operations
 */
public class LoggingController implements ISubController {
        
    public LoggingController() {
        // TODO: Initialize with TextArea fields
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== log() method ==========

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


    // ========== showError() method ==========

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }


    // ========== showWarning() method ==========

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }


    // ========== showInfo() method ==========

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }


    // ========== handleClearLogs() method ==========

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


    // ========== handleExportLogs() method ==========

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

    // ========== Log FXML fields ==========

    // Logs Tab (Encoder Mode)
    @FXML private ComboBox<String> logLevelComboBox;
    @FXML private TextArea logTextArea;

}

