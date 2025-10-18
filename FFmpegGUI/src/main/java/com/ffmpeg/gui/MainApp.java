package com.ffmpeg.gui;

import com.ffmpeg.gui.controller.MainController;
import com.ffmpeg.gui.service.PythonBridge;
import com.ffmpeg.gui.util.ResourceExtractor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main Application Entry Point
 * EncodeForge - Beautiful cross-platform video conversion GUI
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static final String APP_TITLE = "EncodeForge";
    private static final String VERSION = "1.0.0";
    
    private PythonBridge pythonBridge;
    private MainController controller;

    @Override
    public void init() throws Exception {
        logger.info("Initializing EncodeForge v{}", VERSION);
        
        // Extract Python runtime and scripts to temporary directory
        try {
            ResourceExtractor.extractPythonRuntime();
            logger.info("Python runtime extracted successfully");
        } catch (IOException e) {
            logger.error("Failed to extract Python runtime", e);
            throw new RuntimeException("Could not initialize Python backend", e);
        }
        
        // Initialize Python bridge
        pythonBridge = new PythonBridge();
        pythonBridge.start();
        logger.info("Python bridge initialized");
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            
            // Create controller and inject dependencies
            controller = new MainController(pythonBridge);
            loader.setController(controller);
            
            // Load scene
            Scene scene = new Scene(loader.load(), 1400, 900);
            
            // Load CSS styles
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            // Set up stage
            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);
            
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
            
        } catch (IOException e) {
            logger.error("Failed to load application UI", e);
            showErrorAndExit("Failed to load application interface", e);
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Shutting down application");
        
        if (controller != null) {
            controller.shutdown();
        }
        
        if (pythonBridge != null) {
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

    public static void main(String[] args) {
        launch(args);
    }
}

