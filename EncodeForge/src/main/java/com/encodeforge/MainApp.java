package com.encodeforge;

import com.encodeforge.controller.InitializationDialog;
import com.encodeforge.controller.MainController;
import com.encodeforge.service.DependencyManager;
import com.encodeforge.service.PythonBridge;
import com.encodeforge.service.PythonProcessPool;
import com.encodeforge.util.PathManager;
import com.encodeforge.util.ResourceExtractor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Main Application Entry Point
 * Encode Forge - Beautiful cross-platform video conversion GUI
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static final String APP_TITLE = "Encode Forge";
    private static final String VERSION = "0.3.3";
    
    private DependencyManager dependencyManager;
    private PythonBridge pythonBridge; // Legacy - will be replaced by processPool
    private PythonProcessPool processPool;
    private MainController controller;
    private boolean pendingDependencyInstall = false;
    private boolean useProcessPool = true; // Feature flag for gradual rollout

    @Override
    public void init() throws Exception {
        logger.info("Initializing Encode Forge v{}", VERSION);
        
        // Check if initialization has already been completed
        if (isInitializationComplete()) {
            logger.info("Initialization already completed, skipping dependency checks");
            
            // Still need to extract Python scripts and create DependencyManager for runtime
            try {
                ResourceExtractor.extractPythonScripts();
                logger.info("Python scripts extracted successfully (cached)");
            } catch (IOException e) {
                logger.error("Failed to extract Python scripts", e);
                throw new RuntimeException("Could not extract Python scripts", e);
            }
            
            try {
                dependencyManager = new DependencyManager();
                logger.info("DependencyManager initialized (cached)");
            } catch (IOException e) {
                logger.error("Failed to initialize DependencyManager", e);
                throw new RuntimeException("Could not initialize dependency manager", e);
            }
            initializeRuntime();
            return;
        }
        
        // Extract Python scripts from JAR to app directory
        try {
            ResourceExtractor.extractPythonScripts();
            logger.info("Python scripts extracted successfully");
        } catch (IOException e) {
            logger.error("Failed to extract Python scripts", e);
            throw new RuntimeException("Could not extract Python scripts", e);
        }
        
        // Create DependencyManager
        try {
            dependencyManager = new DependencyManager();
            logger.info("DependencyManager initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize DependencyManager", e);
            throw new RuntimeException("Could not initialize dependency manager", e);
        }
        
        // Check if dependencies are installed
        Map<String, Boolean> libStatus = dependencyManager.checkRequiredLibraries().get();
        boolean allLibsInstalled = libStatus.values().stream().allMatch(v -> v);
        boolean ffmpegInstalled = dependencyManager.checkFFmpeg().get();
        
        logger.info("Dependency status:");
        logger.info("  Python libraries: {}", allLibsInstalled ? "✓" : "✗");
        logger.info("  FFmpeg: {}", ffmpegInstalled ? "✓" : "✗");
        
        // If anything is missing, show initialization dialog on startup
        if (!allLibsInstalled || !ffmpegInstalled) {
            logger.info("Some dependencies missing, will show initialization dialog");
            pendingDependencyInstall = true;
        } else {
            // All dependencies are installed, mark initialization as complete
            markInitializationComplete();
        }
        
        initializeRuntime();
    }
    
    /**
     * Initialize runtime components (Python bridge or process pool)
     */
    private void initializeRuntime() throws IOException {
        try {
            if (useProcessPool) {
                // NEW: Multi-process pool for concurrent operations
                logger.info("Initializing Python process pool (multi-worker mode)");
                processPool = new PythonProcessPool(dependencyManager, 4); // 4 workers for good balance
                
                // Start asynchronously - UI won't be blocked
                processPool.startAsync()
                    .thenRun(() -> logger.info("Python process pool started successfully"))
                    .exceptionally(error -> {
                        logger.error("Failed to start process pool", error);
                        return null;
                    });
                
                logger.info("Python process pool initialization started (async)");
            } else {
                // LEGACY: Single Python bridge (backwards compatibility)
                logger.info("Initializing Python bridge (legacy single-worker mode)");
                pythonBridge = new PythonBridge(dependencyManager);
                pythonBridge.start();
                logger.info("Python bridge initialized");
            }
        } catch (IOException e) {
            // Check if this is a Python not found error
            if (e.getMessage() != null && e.getMessage().contains("Python not found")) {
                logger.error("Python not found", e);
                throw new RuntimeException("Python not found. Please install Python 3.8 or higher.", e);
            }
            throw e;
        }
    }
    
    /**
     * Check if initialization has been completed previously
     */
    private boolean isInitializationComplete() {
        try {
            Path initFlagFile = PathManager.getSettingsDir().resolve("initialization_complete.flag");
            return Files.exists(initFlagFile);
        } catch (Exception e) {
            logger.debug("Could not check initialization flag", e);
            return false;
        }
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
    
    /**
     * Reset initialization flag (for debugging or manual reset)
     */
    public static void resetInitializationFlag() {
        try {
            Path initFlagFile = PathManager.getSettingsDir().resolve("initialization_complete.flag");
            Files.deleteIfExists(initFlagFile);
            logger.info("Initialization flag reset");
        } catch (Exception e) {
            logger.warn("Could not reset initialization flag", e);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Show initialization dialog if dependencies need to be installed
            if (pendingDependencyInstall) {
                logger.info("Showing initialization dialog");
                InitializationDialog initDialog = new InitializationDialog(dependencyManager);
                initDialog.showAndWait();
                
                if (initDialog.isCancelled() && !initDialog.isComplete()) {
                    logger.warn("User cancelled initialization, some dependencies may be missing");
                }
            }
            
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            
            // Create controller and inject dependencies
            if (useProcessPool) {
                // NEW: Pass process pool to controller
                controller = new MainController(processPool);
            } else {
                // LEGACY: Pass Python bridge to controller
                controller = new MainController(pythonBridge);
            }
            loader.setController(controller);
            
            // Load scene
            Scene scene = new Scene(loader.load(), 1400, 900);
            
            // Load CSS styles
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            // Set up stage with custom decorations
            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);
            
            // Pass stage reference to controller for window controls
            controller.setStage(primaryStage);
            
            // Load application icon
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/app-icon.png")));
            } catch (Exception e) {
                logger.warn("Could not load application icon", e);
            }
            
            // Handle window close
            primaryStage.setOnCloseRequest(event -> {
                controller.shutdown();
                Platform.exit();
            });
            
            primaryStage.show();
            logger.info("Application started successfully");
            
            // Check for updates on startup (background, non-blocking)
            checkForUpdatesOnStartup();
            
        } catch (RuntimeException e) {
            // Check if this is a Python not found error
            if (e.getMessage() != null && e.getMessage().contains("Python not found")) {
                logger.error("Python not found", e);
                showPythonNotFoundDialog();
            } else {
                logger.error("Failed to start application", e);
                showErrorAndExit("Failed to start application", e);
            }
        } catch (IOException e) {
            logger.error("Failed to load application UI", e);
            showErrorAndExit("Failed to load application interface", e);
        }
    }

    /**
     * Check for updates on startup (background, non-blocking)
     */
    private void checkForUpdatesOnStartup() {
        // Run update check in background thread to avoid blocking startup
        new Thread(() -> {
            try {
                // Wait a bit for the UI to fully load
                Thread.sleep(2000);
                
                com.encodeforge.util.UpdateChecker.checkForUpdates().thenAccept(updateInfo -> {
                    if (updateInfo.isUpdateAvailable()) {
                        // Show update notification on JavaFX thread
                        Platform.runLater(() -> {
                            com.encodeforge.util.UpdateChecker.showUpdateDialog(updateInfo);
                        });
                    }
                }).exceptionally(throwable -> {
                    logger.debug("Background update check failed", throwable);
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Update check interrupted");
            }
        }).start();
    }

    @Override
    public void stop() throws Exception {
        logger.info("Shutting down application");
        
        if (controller != null) {
            controller.shutdown();
        }
        
        if (useProcessPool && processPool != null) {
            logger.info("Shutting down Python process pool");
            processPool.shutdown();
        } else if (pythonBridge != null) {
            logger.info("Shutting down Python bridge");
            pythonBridge.shutdown();
        }
        
        // Clean up extracted resources
        ResourceExtractor.cleanup();
        
        logger.info("Application shutdown complete");
    }

    private void showErrorAndExit(String message, Exception e) {
        logger.error(message, e);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle("Fatal Error");
        alert.setHeaderText(message);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
        Platform.exit();
    }
    
    /**
     * Show dialog when Python is not found
     */
    private void showPythonNotFoundDialog() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle("Python Not Found");
        alert.setHeaderText("Python Interpreter Required");
        
        // Create styled content
        javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(10);
        
        javafx.scene.text.Text warningText = new javafx.scene.text.Text(
            "EncodeForge requires Python 3.8 or higher to run.\n\n" +
            "Please install Python and try again."
        );
        warningText.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px;");
        warningText.setWrappingWidth(450);
        
        // Add platform-specific download links
        String os = System.getProperty("os.name").toLowerCase();
        String downloadText = "";
        if (os.contains("win")) {
            downloadText = "Download Python:\nhttps://www.python.org/downloads/\n\n" +
                          "Recommended: Python 3.12 (latest stable)";
        } else if (os.contains("mac")) {
            downloadText = "Install Python:\nbrew install python@3.12\n\n" +
                          "Or download from: https://www.python.org/downloads/";
        } else {
            downloadText = "Install Python:\nsudo apt-get install python3.12\n\n" +
                          "Or: https://www.python.org/downloads/";
        }
        
        javafx.scene.text.Text downloadInfo = new javafx.scene.text.Text(downloadText);
        downloadInfo.setStyle("-fx-fill: #a0a0a0; -fx-font-size: 11px;");
        downloadInfo.setWrappingWidth(450);
        
        contentBox.getChildren().addAll(warningText, downloadInfo);
        alert.getDialogPane().setContent(contentBox);
        
        // Style the dialog
        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane");
        
        javafx.scene.control.ButtonType openWebsiteButton = new javafx.scene.control.ButtonType("Open Download Page");
        javafx.scene.control.ButtonType closeButton = new javafx.scene.control.ButtonType("Close", 
            javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(openWebsiteButton, closeButton);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == openWebsiteButton) {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.python.org/downloads/"));
                } catch (Exception ex) {
                    logger.error("Failed to open browser", ex);
                }
            }
        });
        
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

