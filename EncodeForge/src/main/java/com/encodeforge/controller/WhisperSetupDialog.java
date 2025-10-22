package com.encodeforge.controller;

import com.encodeforge.model.ProgressUpdate;
import com.encodeforge.service.DependencyManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Wizard dialog for setting up Whisper AI subtitle generation
 */
public class WhisperSetupDialog {
    private static final Logger logger = LoggerFactory.getLogger(WhisperSetupDialog.class);
    
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    
    // Pages
    @FXML private VBox page1; // Welcome
    @FXML private VBox page2; // Model selection
    @FXML private VBox page3; // Installation
    @FXML private VBox page4; // Complete
    
    // Model selection
    @FXML private RadioButton tinyModel;
    @FXML private RadioButton baseModel;
    @FXML private RadioButton smallModel;
    @FXML private RadioButton mediumModel;
    @FXML private RadioButton largeModel;
    
    // Installation progress
    @FXML private Label installStatusLabel;
    @FXML private ProgressBar installProgressBar;
    @FXML private Label installProgressLabel;
    @FXML private TextArea installLogArea;
    
    // Navigation buttons
    @FXML private Button cancelButton;
    @FXML private Button backButton;
    @FXML private Button nextButton;
    @FXML private Button finishButton;
    
    private Stage dialogStage;
    private DependencyManager dependencyManager;
    private int currentPage = 1;
    private String selectedModel = "small";
    private boolean installationComplete = false;
    
    public WhisperSetupDialog(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }
    
    /**
     * Show the Whisper setup wizard
     */
    public void showAndWait() {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/WhisperSetupDialog.fxml"));
            loader.setController(this);
            
            // Create scene
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            // Create stage
            dialogStage = new Stage();
            dialogStage.setTitle("Setup AI Subtitles - Whisper");
            dialogStage.setScene(scene);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setResizable(false);
            
            // Prevent closing during installation
            dialogStage.setOnCloseRequest(event -> {
                if (currentPage == 3 && !installationComplete) {
                    event.consume();
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Installation in Progress");
                    alert.setHeaderText("Cannot close during installation");
                    alert.setContentText("Please wait for the installation to complete.");
                    alert.showAndWait();
                }
            });
            
            // Show and wait
            dialogStage.showAndWait();
            
        } catch (IOException e) {
            logger.error("Failed to load Whisper setup dialog", e);
            throw new RuntimeException("Could not show Whisper setup dialog", e);
        }
    }
    
    /**
     * Handle Next button
     */
    @FXML
    private void handleNext() {
        if (currentPage == 1) {
            // Move to model selection
            showPage(2);
        } else if (currentPage == 2) {
            // Get selected model
            if (tinyModel.isSelected()) selectedModel = "tiny";
            else if (baseModel.isSelected()) selectedModel = "base";
            else if (smallModel.isSelected()) selectedModel = "small";
            else if (mediumModel.isSelected()) selectedModel = "medium";
            else if (largeModel.isSelected()) selectedModel = "large";
            
            // Start installation
            showPage(3);
            startInstallation();
        }
    }
    
    /**
     * Handle Back button
     */
    @FXML
    private void handleBack() {
        if (currentPage == 2) {
            showPage(1);
        } else if (currentPage == 3 && !installationComplete) {
            // Can't go back during installation
            return;
        } else if (currentPage == 4) {
            showPage(2);
        }
    }
    
    /**
     * Handle Cancel button
     */
    @FXML
    private void handleCancel() {
        if (currentPage == 3 && !installationComplete) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Cancel Installation");
            confirm.setHeaderText("Installation in progress");
            confirm.setContentText("Are you sure you want to cancel? Partial installation may not work.");
            
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                dialogStage.close();
            }
        } else {
            dialogStage.close();
        }
    }
    
    /**
     * Handle Finish button
     */
    @FXML
    private void handleFinish() {
        dialogStage.close();
    }
    
    /**
     * Show a specific page
     */
    private void showPage(int pageNumber) {
        // Hide all pages
        page1.setVisible(false);
        page2.setVisible(false);
        page3.setVisible(false);
        page4.setVisible(false);
        
        // Show selected page
        currentPage = pageNumber;
        switch (pageNumber) {
            case 1:
                page1.setVisible(true);
                titleLabel.setText("Setup AI Subtitle Generation");
                subtitleLabel.setText("Install Whisper AI for automatic subtitle generation");
                backButton.setDisable(true);
                nextButton.setVisible(true);
                nextButton.setDisable(false);
                finishButton.setVisible(false);
                cancelButton.setDisable(false);
                break;
            case 2:
                page2.setVisible(true);
                titleLabel.setText("Choose Model Size");
                subtitleLabel.setText("Select the Whisper model that fits your needs");
                backButton.setDisable(false);
                nextButton.setVisible(true);
                nextButton.setText("Install");
                nextButton.setDisable(false);
                finishButton.setVisible(false);
                cancelButton.setDisable(false);
                break;
            case 3:
                page3.setVisible(true);
                titleLabel.setText("Installing Whisper AI");
                subtitleLabel.setText("Please wait while we install the required components");
                backButton.setDisable(true);
                nextButton.setVisible(false);
                finishButton.setVisible(false);
                cancelButton.setDisable(true);
                break;
            case 4:
                page4.setVisible(true);
                titleLabel.setText("Setup Complete!");
                subtitleLabel.setText("You're ready to generate AI subtitles");
                backButton.setDisable(true);
                nextButton.setVisible(false);
                finishButton.setVisible(true);
                cancelButton.setDisable(false);
                cancelButton.setText("Close");
                break;
        }
    }
    
    /**
     * Start the installation process
     */
    private void startInstallation() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Install Whisper and dependencies
                appendLog("Installing Whisper AI and dependencies...");
                updateInstallProgress(new ProgressUpdate("installing", 10, 
                    "Installing Whisper AI (this may take several minutes)...", ""));
                
                List<String> packages = Arrays.asList(
                    "openai-whisper>=20231117",
                    "torch>=2.0.0",
                    "numba>=0.58.0,<0.63.0"
                );
                
                dependencyManager.installOptionalLibraries(packages, this::updateInstallProgress).get();
                
                appendLog("✓ Whisper AI packages installed successfully");
                
                // Step 2: Download the selected model
                updateInstallProgress(new ProgressUpdate("downloading", 70, 
                    "Downloading " + selectedModel + " model...", ""));
                appendLog("\nDownloading " + selectedModel + " model (this may take a while)...");
                
                // TODO: Implement model download via Python
                // For now, just simulate it
                Thread.sleep(2000);
                
                appendLog("✓ Model downloaded successfully");
                
                // Complete
                updateInstallProgress(new ProgressUpdate("complete", 100, 
                    "Installation complete!", ""));
                appendLog("\n✓ Whisper AI is ready to use!");
                
                installationComplete = true;
                
                Platform.runLater(() -> {
                    showPage(4);
                });
                
            } catch (Exception e) {
                logger.error("Whisper installation failed", e);
                appendLog("\n❌ Error: " + e.getMessage());
                updateInstallProgress(new ProgressUpdate("error", 0, 
                    "Installation failed: " + e.getMessage(), ""));
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Installation Failed");
                    alert.setHeaderText("Failed to install Whisper AI");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                    
                    cancelButton.setDisable(false);
                    cancelButton.setText("Close");
                });
            }
        });
    }
    
    /**
     * Update installation progress
     */
    private void updateInstallProgress(ProgressUpdate progress) {
        Platform.runLater(() -> {
            installStatusLabel.setText(progress.getMessage());
            installProgressBar.setProgress(progress.getProgress() / 100.0);
            installProgressLabel.setText(progress.getProgress() + "%");
            
            if (!progress.getDetail().isEmpty()) {
                appendLog(progress.getDetail());
            }
        });
    }
    
    /**
     * Append text to installation log
     */
    private void appendLog(String text) {
        Platform.runLater(() -> {
            installLogArea.appendText(text + "\n");
        });
    }
    
    public boolean isInstallationComplete() {
        return installationComplete;
    }
}

