package com.encodeforge.controller.components;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import java.io.File;
import java.util.Set;


/**
 * WindowController - Handle all window-level operations (drag, resize, controls)
 */
public class WindowController implements ISubController {
    

    // ========== Window state variables ==========

    // Window dragging variables
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Window resizing variables
    private double resizeStartX = 0;
    private double resizeStartY = 0;
    private double resizeStartWidth = 0;
    private double resizeStartHeight = 0;
    private double resizeStartStageX = 0;
    private double resizeStartStageY = 0;
    private ResizeDirection resizeDirection = ResizeDirection.NONE;
    private boolean isResizing = false;
    
    // Minimum window size
    private static final double MIN_WIDTH = 1000;
    private static final double MIN_HEIGHT = 700;
    
    // Resize border thickness
    private static final double RESIZE_BORDER = 8;
    
    // Resize direction enum
    private enum ResizeDirection {
        NONE, N, S, E, W, NE, NW, SE, SW
    }


    // ========== Window control FXML fields ==========

    @FXML private Label appIconLabel;
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    
    // Icon sizes
    private static final int WINDOW_CONTROL_ICON_SIZE = 14;
    private static final int TOOLBAR_ICON_SIZE = 14;
    private static final int SMALL_ICON_SIZE = 12;
    
    // Sidebar Controls
    @FXML private Button encoderModeButton;
    @FXML private Button subtitleModeButton;
    @FXML private Button renamerModeButton;
    @FXML private Button addFilesButton;
    @FXML private Button addFolderButton;
    @FXML private Button settingsButton;
    @FXML private Button ffmpegStatusButton;
    @FXML private javafx.scene.control.Label ffmpegStatusIcon;
    
    public WindowController() {
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


    // ========== setupResizeHandlers() ==========

    public void setStage(Stage stage) {
        this.primaryStage = stage;
        if (stage != null) {
            // Set minimum size
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);
            
            // Add resize functionality via scene mouse events
            javafx.scene.Scene scene = stage.getScene();
            if (scene != null) {
                setupResizeHandlers(scene);
                logger.info("Window resize handlers installed successfully");
            }
        }
    }
    
    /**
     * Setup mouse event handlers for window resizing using event filters
     */
    private void setupResizeHandlers(javafx.scene.Scene scene) {
        // Mouse moved - update cursor based on position (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, event -> {
            if (!isResizing && !primaryStage.isMaximized()) {
                ResizeDirection direction = getResizeDirection(event.getSceneX(), event.getSceneY());
                updateCursor(direction);
            }
        });
        
        // Mouse pressed - start resize if on border (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (!primaryStage.isMaximized()) {
                resizeDirection = getResizeDirection(event.getSceneX(), event.getSceneY());
                if (resizeDirection != ResizeDirection.NONE) {
                    isResizing = true;
                    resizeStartX = event.getScreenX();
                    resizeStartY = event.getScreenY();
                    resizeStartWidth = primaryStage.getWidth();
                    resizeStartHeight = primaryStage.getHeight();
                    resizeStartStageX = primaryStage.getX();
                    resizeStartStageY = primaryStage.getY();
                    event.consume(); // Consume the event to prevent it from reaching children
                    logger.info("Started resizing: direction={}, startSize={}x{}", 
                               resizeDirection, resizeStartWidth, resizeStartHeight);
                }
            }
        });
        
        // Mouse dragged - perform resize (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, event -> {
            if (isResizing && resizeDirection != ResizeDirection.NONE) {
                performResize(event.getScreenX(), event.getScreenY());
                event.consume(); // Consume the event to prevent it from reaching children
            }
        });
        
        // Mouse released - end resize (use filter to capture before children)
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, event -> {
            if (isResizing) {
                logger.info("Finished resizing: finalSize={}x{}", primaryStage.getWidth(), primaryStage.getHeight());
                isResizing = false;
                resizeDirection = ResizeDirection.NONE;
                event.consume(); // Consume the event
            }
        });


    // ========== getResizeDirection() ==========

    private ResizeDirection getResizeDirection(double sceneX, double sceneY) {
        double width = primaryStage.getScene().getWidth();
        double height = primaryStage.getScene().getHeight();
        
        boolean left = sceneX < RESIZE_BORDER;
        boolean right = sceneX > width - RESIZE_BORDER;
        boolean top = sceneY < RESIZE_BORDER;
        boolean bottom = sceneY > height - RESIZE_BORDER;
        
        if (top && left) return ResizeDirection.NW;
        if (top && right) return ResizeDirection.NE;
        if (bottom && left) return ResizeDirection.SW;
        if (bottom && right) return ResizeDirection.SE;
        if (top) return ResizeDirection.N;
        if (bottom) return ResizeDirection.S;
        if (left) return ResizeDirection.W;
        if (right) return ResizeDirection.E;
        
        return ResizeDirection.NONE;


    // ========== updateCursor() ==========

    private void updateCursor(ResizeDirection direction) {
        javafx.scene.Cursor cursor = javafx.scene.Cursor.DEFAULT;
        
        switch (direction) {
            case N:
            case S:
                cursor = javafx.scene.Cursor.N_RESIZE;
                break;
            case E:
            case W:
                cursor = javafx.scene.Cursor.E_RESIZE;
                break;
            case NE:
            case SW:
                cursor = javafx.scene.Cursor.NE_RESIZE;
                break;
            case NW:
            case SE:
                cursor = javafx.scene.Cursor.NW_RESIZE;
                break;
        }
        
        primaryStage.getScene().setCursor(cursor);
    }


    // ========== performResize() ==========

    private void performResize(double screenX, double screenY) {
        double deltaX = screenX - resizeStartX;
        double deltaY = screenY - resizeStartY;
        
        double newX = resizeStartStageX;
        double newY = resizeStartStageY;
        double newWidth = resizeStartWidth;
        double newHeight = resizeStartHeight;
        
        switch (resizeDirection) {
            case N:
                newY = resizeStartStageY + deltaY;
                newHeight = resizeStartHeight - deltaY;
                break;
            case S:
                newHeight = resizeStartHeight + deltaY;
                break;
            case E:
                newWidth = resizeStartWidth + deltaX;
                break;
            case W:
                newX = resizeStartStageX + deltaX;
                newWidth = resizeStartWidth - deltaX;
                break;
            case NE:
                newY = resizeStartStageY + deltaY;
                newWidth = resizeStartWidth + deltaX;
                newHeight = resizeStartHeight - deltaY;
                break;
            case NW:
                newX = resizeStartStageX + deltaX;
                newY = resizeStartStageY + deltaY;
                newWidth = resizeStartWidth - deltaX;
                newHeight = resizeStartHeight - deltaY;
                break;
            case SE:
                newWidth = resizeStartWidth + deltaX;
                newHeight = resizeStartHeight + deltaY;
                break;
            case SW:
                newX = resizeStartStageX + deltaX;
                newWidth = resizeStartWidth - deltaX;
                newHeight = resizeStartHeight + deltaY;
                break;
        }
        
        // Enforce minimum size
        if (newWidth < MIN_WIDTH) {
            if (resizeDirection == ResizeDirection.W || resizeDirection == ResizeDirection.NW || resizeDirection == ResizeDirection.SW) {
                newX = resizeStartStageX + (resizeStartWidth - MIN_WIDTH);
            }
            newWidth = MIN_WIDTH;
        }
        
        if (newHeight < MIN_HEIGHT) {
            if (resizeDirection == ResizeDirection.N || resizeDirection == ResizeDirection.NE || resizeDirection == ResizeDirection.NW) {
                newY = resizeStartStageY + (resizeStartHeight - MIN_HEIGHT);
            }
            newHeight = MIN_HEIGHT;
        }
        
        // Apply new size and position
        primaryStage.setX(newX);
        primaryStage.setY(newY);
        primaryStage.setWidth(newWidth);
        primaryStage.setHeight(newHeight);
    }


    // ========== handleTitleBarPressed() ==========

    @FXML
    private void handleTitleBarPressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }


    // ========== handleTitleBarDragged() ==========

    @FXML
    private void handleTitleBarDragged(MouseEvent event) {
        if (primaryStage != null) {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        }
    }


    // ========== handleMinimize() ==========

    @FXML
    private void handleMinimize() {
        if (primaryStage != null) {
            primaryStage.setIconified(true);
        }
    }


    // ========== handleMaximize() ==========

    @FXML
    private void handleMaximize() {
        if (primaryStage != null) {
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
                // Update icon to maximize
                org.kordamp.ikonli.javafx.FontIcon maximizeIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_MAXIMIZE);
                maximizeIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                maximizeIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                maximizeButton.setGraphic(maximizeIcon);
            } else {
                primaryStage.setMaximized(true);
                // Update icon to restore
                org.kordamp.ikonli.javafx.FontIcon restoreIcon = new org.kordamp.ikonli.javafx.FontIcon(
                    org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.WINDOW_RESTORE);
                restoreIcon.setIconSize(WINDOW_CONTROL_ICON_SIZE);
                restoreIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                maximizeButton.setGraphic(restoreIcon);
            }
        }
    }


    // ========== handleClose() ==========

    @FXML
    private void handleClose() {
        if (primaryStage != null) {
            primaryStage.fireEvent(new javafx.stage.WindowEvent(primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }

    // ========== Icon size constants ==========

    // Icon sizes
    private static final int WINDOW_CONTROL_ICON_SIZE = 14;
    private static final int TOOLBAR_ICON_SIZE = 14;
    private static final int SMALL_ICON_SIZE = 12;

}

