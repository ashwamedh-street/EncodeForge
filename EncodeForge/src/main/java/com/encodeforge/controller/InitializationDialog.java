package com.encodeforge.controller;

import com.encodeforge.model.ProgressUpdate;
import com.encodeforge.service.DependencyManager;
import com.encodeforge.util.PathManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog for showing initialization progress and installing dependencies
 */
public class InitializationDialog {
    private static final Logger logger = LoggerFactory.getLogger(InitializationDialog.class);
    
    @FXML private Label stageLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TitledPane logPane;
    @FXML private TextArea logArea;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;
    
    private Stage dialogStage;
    private DependencyManager dependencyManager;
    private boolean isComplete = false;
    private boolean isCancelled = false;
    
    public InitializationDialog(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }
    
    /**
     * Show the initialization dialog and wait for completion
     */
    public void showAndWait() {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/InitializationDialog.fxml"));
            loader.setController(this);
            
            // Create scene
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            // Create stage
            dialogStage = new Stage();
            dialogStage.setTitle("Initializing EncodeForge");
            dialogStage.setScene(scene);
            dialogStage.initStyle(StageStyle.UTILITY);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setResizable(false);
            
            // Prevent closing via X button during required operations
            dialogStage.setOnCloseRequest(event -> {
                if (!isComplete && !cancelButton.isDisable()) {
                    handleCancel();
                }
                event.consume();
            });
            
            // Start initialization process
            startInitialization();
            
            // Show and wait
            dialogStage.showAndWait();
            
        } catch (IOException e) {
            logger.error("Failed to load initialization dialog", e);
            throw new RuntimeException("Could not show initialization dialog", e);
        }
    }
    
    /**
     * Start the initialization process
     */
    private void startInitialization() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Check required libraries (ALWAYS - even if previously initialized)
                updateProgress(new ProgressUpdate("detecting", 10, 
                    "Checking Python libraries...", ""));
                
                Map<String, Boolean> libStatus = dependencyManager.checkRequiredLibraries().get();
                boolean allInstalled = libStatus.values().stream().allMatch(v -> v);
                
                appendLog("Required libraries check:");
                libStatus.forEach((lib, installed) -> 
                    appendLog("  " + lib + ": " + (installed ? "✓" : "✗")));
                
                // Step 2: Check FFmpeg
                updateProgress(new ProgressUpdate("detecting", 20, 
                    "Checking for FFmpeg...", ""));
                
                boolean ffmpegInstalled = dependencyManager.checkFFmpeg().get();
                appendLog("FFmpeg: " + (ffmpegInstalled ? "✓ Found" : "✗ Not found"));
                
                // Step 3: Install missing libraries (ALWAYS if not all installed)
                // This ensures new libraries like beautifulsoup4/lxml get installed
                // even if initialization was previously completed
                if (!allInstalled) {
                    updateProgress(new ProgressUpdate("installing", 30, 
                        "Installing required Python libraries...", ""));
                    appendLog("\nInstalling missing Python libraries...");
                    
                    try {
                        dependencyManager.installRequiredLibraries(this::updateProgress).get();
                        appendLog("✓ Python libraries installed successfully");
                    } catch (Exception libEx) {
                        logger.error("Failed to install required Python libraries", libEx);
                        appendLog("\n❌ Failed to install required Python libraries");
                        
                        // Show error dialog with retry/quit options for required libraries
                        boolean[] userChoice = new boolean[1];
                        Platform.runLater(() -> {
                            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                            errorAlert.setTitle("Required Dependencies Missing");
                            errorAlert.setHeaderText("Failed to Install Required Python Libraries");
                            errorAlert.setContentText(
                                "EncodeForge requires Python libraries to function properly.\n\n" +
                                "Error: " + libEx.getMessage() + "\n\n" +
                                "Would you like to retry the installation or quit the application?"
                            );
                            
                            ButtonType retryButton = new ButtonType("Retry");
                            ButtonType quitButton = new ButtonType("Quit", ButtonBar.ButtonData.CANCEL_CLOSE);
                            errorAlert.getButtonTypes().setAll(retryButton, quitButton);
                            
                            errorAlert.showAndWait().ifPresent(response -> {
                                if (response == retryButton) {
                                    userChoice[0] = true;
                                    // Restart installation
                                    startInitialization();
                                } else {
                                    isCancelled = true;
                                    dialogStage.close();
                                }
                            });
                        });
                        return;
                    }
                } else {
                    appendLog("\n✓ All required Python libraries are installed");
                }
                
                // Step 4: Install FFmpeg if missing
                if (!ffmpegInstalled) {
                    updateProgress(new ProgressUpdate("installing", 60, 
                        "Installing FFmpeg...", ""));
                    appendLog("\nInstalling FFmpeg...");
                    
                    try {
                        dependencyManager.installFFmpeg(this::updateProgress).get();
                        appendLog("✓ FFmpeg installed successfully");
                    } catch (Exception ffmpegEx) {
                        logger.warn("Failed to install FFmpeg, but continuing", ffmpegEx);
                        appendLog("\n⚠️  FFmpeg installation failed - you can install it manually later");
                    }
                }
                
                // Complete
                updateProgress(new ProgressUpdate("complete", 100, 
                    "Initialization complete!", ""));
                appendLog("\n✓ All dependencies ready!");
                
                Platform.runLater(() -> {
                    isComplete = true;
                    cancelButton.setVisible(false);
                    closeButton.setVisible(true);
                    
                    // Mark initialization as complete
                    markInitializationComplete();
                    
                    // Auto-close after a short delay to show completion
                    Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                        dialogStage.close();
                    }));
                    timeline.play();
                });
                
            } catch (Exception e) {
                logger.error("Initialization failed", e);
                appendLog("\n❌ Error: " + e.getMessage());
                updateProgress(new ProgressUpdate("error", 0, 
                    "Initialization failed: " + e.getMessage(), ""));
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Initialization Failed");
                    alert.setHeaderText("Failed to initialize dependencies");
                    alert.setContentText(
                        "An unexpected error occurred during initialization.\n\n" +
                        "Error: " + e.getMessage() + "\n\n" +
                        "Please check the logs for more details."
                    );
                    alert.showAndWait();
                    
                    cancelButton.setDisable(false);
                    cancelButton.setText("Close");
                });
            }
        });
    }
    
    /**
     * Update progress UI
     */
    private void updateProgress(ProgressUpdate progress) {
        Platform.runLater(() -> {
            stageLabel.setText(capitalizeFirst(progress.getStage()));
            statusLabel.setText(progress.getMessage());
            progressBar.setProgress(progress.getProgress() / 100.0);
            progressLabel.setText(progress.getProgress() + "%");
            
            if (!progress.getDetail().isEmpty()) {
                appendLog(progress.getDetail());
            }
        });
    }
    
    /**
     * Append text to log area
     */
    private void appendLog(String text) {
        Platform.runLater(() -> {
            logArea.appendText(text + "\n");
        });
    }
    
    /**
     * Handle cancel button
     */
    @FXML
    private void handleCancel() {
        if (!isComplete) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Cancel Initialization");
            confirm.setHeaderText("Cancel initialization?");
            confirm.setContentText("Some dependencies may not be installed. The application may not function correctly.");
            
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                isCancelled = true;
                dialogStage.close();
            }
        } else {
            dialogStage.close();
        }
    }
    
    /**
     * Handle close button
     */
    @FXML
    private void handleClose() {
        dialogStage.close();
    }
    
    /**
     * Capitalize first letter
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public boolean isCancelled() {
        return isCancelled;
    }
    
    /**
     * Mark initialization as complete
     */
    private void markInitializationComplete() {
        try {
            Path initFlagFile = PathManager.getSettingsDir().resolve("initialization_complete.flag");
            Files.write(initFlagFile, ("Initialization completed on " + java.time.Instant.now()).getBytes());
            logger.info("Initialization marked as complete");
        } catch (Exception e) {
            logger.warn("Could not mark initialization as complete", e);
        }
    }
}

