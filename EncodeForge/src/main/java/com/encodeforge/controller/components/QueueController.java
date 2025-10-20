package com.encodeforge.controller.components;

import com.encodeforge.model.ConversionJob;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;


/**
 * QueueController - Manage file queue (add, remove, reorder, drag & drop)
 */
public class QueueController implements ISubController {
    
    public QueueController() {
        // TODO: Initialize with ObservableLists and PythonBridge
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== setupQueuedTable() ==========

    private void setupQueuedTable() {
        // Setup columns
        queuedStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("⏳"));
        queuedFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        queuedOutputColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(settings.getOutputFormat().toUpperCase()));
        queuedSizeColumn.setCellValueFactory(cd -> {
            try {
                long size = java.nio.file.Files.size(java.nio.file.Paths.get(cd.getValue().getInputPath()));
                return new javafx.beans.property.SimpleStringProperty(formatFileSize(size));
            } catch (java.io.IOException e) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
        });
        
        // Set items
        queuedTable.setItems(queuedFiles);
        
        // Selection listener
        queuedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFileInfo(newVal);
            }
        });
    }


    // ========== setupProcessingTable() ==========

    private void setupProcessingTable() {
        // Setup columns
        procStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("⚡"));
        procFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        procProgressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        procFpsColumn.setCellValueFactory(new PropertyValueFactory<>("fps"));
        procSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        procEtaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        
        // Custom progress bar column
        procProgressColumn.setCellFactory(col -> new TableCell<ConversionJob, Double>() {
            private final ProgressBar progressBar = new ProgressBar();
            private final Label label = new Label();
            
            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    progressBar.setProgress(progress / 100.0);
                    progressBar.setPrefWidth(80);
                    label.setText(String.format("%.1f%%", progress));
                    VBox box = new VBox(2, progressBar, label);
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    setGraphic(box);
                }
            }
        });
        
        // Set items
        processingTable.setItems(processingFiles);
    }


    // ========== setupCompletedTable() ==========

    private void setupCompletedTable() {
        // Setup columns
        compStatusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty("✅"));
        compFileColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(new File(cd.getValue().getInputPath()).getName()));
        compOutputColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(settings.getOutputFormat().toUpperCase()));
        compSizeColumn.setCellValueFactory(cd -> {
            try {
                long size = java.nio.file.Files.size(java.nio.file.Paths.get(cd.getValue().getInputPath()));
                return new javafx.beans.property.SimpleStringProperty(formatFileSize(size));
            } catch (java.io.IOException e) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
        });
        compNewSizeColumn.setCellValueFactory(new PropertyValueFactory<>("outputSizeString"));
        compTimeColumn.setCellValueFactory(new PropertyValueFactory<>("timeTaken"));
        
        // Set items
        completedTable.setItems(completedFiles);
        
        // Selection listener
        completedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFileInfo(newVal);
            }
        });
    }


    // ========== updateQueueCounts() ==========

    private void updateQueueCounts() {
        if (queuedCountLabel != null) {
            queuedCountLabel.setText(queuedFiles.size() + " file" + (queuedFiles.size() != 1 ? "s" : ""));
        }
        if (processingCountLabel != null) {
            processingCountLabel.setText(processingFiles.size() + " file" + (processingFiles.size() != 1 ? "s" : ""));
        }
        if (completedCountLabel != null) {
            completedCountLabel.setText(completedFiles.size() + " file" + (completedFiles.size() != 1 ? "s" : ""));
        }
        
        // Update start button state
        if (startButton != null) {
            startButton.setDisable(queuedFiles.isEmpty() || isProcessing);
        }
    }


    // ========== formatFileSize() ==========

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }


    // ========== handleAddFiles() ==========

    @FXML
    private void handleAddFiles() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Video Files");
        chooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("Video Files", 
                "*.mkv", "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.webm"),
            new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        if (lastDirectory != null && lastDirectory.exists()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        
        List<File> selectedFiles = chooser.showOpenMultipleDialog(addFilesButton.getScene().getWindow());
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            lastDirectory = selectedFiles.get(0).getParentFile();
            addFilesToQueue(selectedFiles);
        }
    }


    // ========== handleAddFolder() ==========

    @FXML
    private void handleAddFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder with Video Files");
        
        if (lastDirectory != null && lastDirectory.exists()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        
        File selectedDir = chooser.showDialog(addFolderButton.getScene().getWindow());
        if (selectedDir != null) {
            lastDirectory = selectedDir;
            scanDirectory(selectedDir);
        }
    }


    // ========== addFilesToQueue() ==========

    private void addFilesToQueue(List<File> files) {
        int added = 0;
        int skipped = 0;
        for (File file : files) {
            // Check if already in any queue
            boolean alreadyInQueue = queuedFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath())) ||
                processingFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath())) ||
                completedFiles.stream()
                .anyMatch(job -> job.getInputPath().equals(file.getAbsolutePath()));
            
            if (!alreadyInQueue) {
                ConversionJob job = new ConversionJob(file.getAbsolutePath());
                job.setOutputFormat("MP4"); // Default
                job.setStatus("⏳ Queued");
                queuedFiles.add(job);
                added++;
            } else {
                skipped++;
            }
        }
        
        updateQueueCounts();
        updateSelectedFilesLabel();
        
        // Update subtitle file list if in subtitle mode
        if ("subtitle".equals(currentMode)) {
            updateSubtitleFileList();
        }
        
        if (added > 0) {
            log("Added " + added + " file(s) to queue" + (skipped > 0 ? " (" + skipped + " duplicate(s) skipped)" : ""));
        } else if (skipped > 0) {
            log("Skipped " + skipped + " duplicate file(s) - already in queue");
        }
    }


    // ========== scanDirectory() ==========

    private void scanDirectory(File directory) {
        statusLabel.setText("Scanning...");
        addFolderButton.setDisable(true);
        
        new Thread(() -> {
            try {
                JsonObject response = pythonBridge.scanDirectory(directory.getAbsolutePath(), true);
                
                Platform.runLater(() -> {
                    if (response.get("status").getAsString().equals("success")) {
                        JsonArray filesArray = response.getAsJsonArray("files");
                        List<File> files = new ArrayList<>();
                        filesArray.forEach(element -> files.add(new File(element.getAsString())));
                        
                        addFilesToQueue(files);
                        statusLabel.setText("Scan complete - " + files.size() + " files found");
                    } else {
                        String error = response.has("message") ? response.get("message").getAsString() : "Scan failed";
                        showError("Scan Error", error);
                        statusLabel.setText("Ready");
                    }
                    addFolderButton.setDisable(false);
                });
                
            } catch (IOException | TimeoutException e) {
                logger.error("Error scanning directory", e);
                Platform.runLater(() -> {
                    showError("Scan Error", "Failed to scan directory: " + e.getMessage());
                    statusLabel.setText("Ready");
                    addFolderButton.setDisable(false);
                });
            }
        }).start();
    }


    // ========== handleRemoveQueued() ==========

    private void handleRemoveQueued() {
        ObservableList<ConversionJob> selectedJobs = queuedTable.getSelectionModel().getSelectedItems();
        
        if (selectedJobs.isEmpty()) {
            showWarning("No Selection", "Please select one or more files to remove.");
            return;
        }
        
        List<ConversionJob> jobsToRemove = new ArrayList<>(selectedJobs);
        queuedFiles.removeAll(jobsToRemove);
        updateQueueCounts();
        
        log("Removed " + jobsToRemove.size() + " file(s) from queue");
    }


    // ========== handleRemoveCompleted() ==========

    @FXML
    private void handleRemoveCompleted() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            completedFiles.remove(selected);
            updateQueueCounts();
            log("Removed completed file from list");
        }
    }


    // ========== handleClearQueued() ==========

    @FXML
    private void handleClearQueued() {
        if (!isProcessing && !queuedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear Queued");
            alert.setHeaderText("Clear all queued files?");
            alert.setContentText("This will remove all queued files.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                queuedFiles.clear();
                updateQueueCounts();
                log("Queued files cleared");
            }
        }
    }


    // ========== handleClearCompleted() ==========

    @FXML
    private void handleClearCompleted() {
        if (!completedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear Completed");
            alert.setHeaderText("Clear all completed files?");
            alert.setContentText("This will remove all completed files from the list.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                completedFiles.clear();
                updateQueueCounts();
                log("Completed files cleared");
            }
        }
    }


    // ========== handleMoveUpQueued() ==========

    @FXML
    private void handleMoveUpQueued() {
        int index = queuedTable.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            ConversionJob job = queuedFiles.remove(index);
            queuedFiles.add(index - 1, job);
            queuedTable.getSelectionModel().select(index - 1);
        }
    }


    // ========== handleMoveDownQueued() ==========

    @FXML
    private void handleMoveDownQueued() {
        int index = queuedTable.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < queuedFiles.size() - 1) {
            ConversionJob job = queuedFiles.remove(index);
            queuedFiles.add(index + 1, job);
            queuedTable.getSelectionModel().select(index + 1);
        }
    }


    // ========== handleOpenFileLocationQueued() ==========

    @FXML
    private void handleOpenFileLocationQueued() {
        ConversionJob selected = queuedTable.getSelectionModel().getSelectedItem();
        openFileLocation(selected);
    }


    // ========== handleOpenFileLocationCompleted() ==========

    @FXML
    private void handleOpenFileLocationCompleted() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        openFileLocation(selected);
    }


    // ========== handleOpenOutputFile() ==========

    @FXML
    private void handleOpenOutputFile() {
        ConversionJob selected = completedTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getOutputPath() != null) {
            try {
                File file = new File(selected.getOutputPath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file);
                } else {
                    showWarning("File Not Found", "Output file does not exist: " + file.getName());
                }
            } catch (IOException e) {
                logger.error("Error opening output file", e);
                showError("Error", "Could not open output file: " + e.getMessage());
            }
        }
    }


    // ========== openFileLocation() ==========

    private void openFileLocation(ConversionJob job) {
        if (job != null) {
            try {
                File file = new File(job.getInputPath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                }
            } catch (IOException e) {
                logger.error("Error opening file location", e);
                showError("Error", "Could not open file location: " + e.getMessage());
            }
        }
    }


    // ========== setupQueueDragAndDrop() ==========

    
    private void setupQueueDragAndDrop() {
        if (queuedTable == null) {
            return;
        }
        
        queuedTable.setRowFactory(tv -> {
            TableRow<ConversionJob> row = new TableRow<>();
            
            // Drag detected
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(String.valueOf(index));
                    db.setContent(cc);
                    
                    row.getStyleClass().add("drag-source");
                    event.consume();
                }
            });
            
            // Drag over
            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    
                    if (!row.isEmpty()) {
                        row.getStyleClass().add("drop-target");
                    }
                }
                event.consume();
            });
            
            // Drag exited
            row.setOnDragExited(event -> {
                row.getStyleClass().removeAll("drop-target");
                event.consume();
            });
            
            // Drag dropped
            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    ConversionJob draggedJob = queuedFiles.get(draggedIndex);
                    
                    int dropIndex;
                    if (row.isEmpty()) {
                        dropIndex = queuedFiles.size() - 1;
                    } else {
                        dropIndex = row.getIndex();
                    }
                    
                    queuedFiles.remove(draggedIndex);
                    if (dropIndex > draggedIndex) {
                        queuedFiles.add(dropIndex - 1, draggedJob);
                    } else {
                        queuedFiles.add(dropIndex, draggedJob);
                    }
                    
                    queuedTable.getSelectionModel().clearSelection();
                    queuedTable.getSelectionModel().select(dropIndex);
                    
                    log("Reordered: moved item from position " + (draggedIndex + 1) + " to " + (dropIndex + 1));
                    
                    event.setDropCompleted(true);
                }
                event.consume();
            });
            
            // Drag done
            row.setOnDragDone(event -> {
                row.getStyleClass().removeAll("drag-source", "drop-target");
                event.consume();
            });
            
            return row;
        });
    }


    // ========== setupQueueSplitPane() ==========

    private void setupQueueSplitPane() {
        if (queueSplitPane == null) {
            logger.warn("Queue split pane not found");
            return;
        }
        
        // Set initial divider positions (40% for Queued, 20% for Processing, 40% for Completed)
        Platform.runLater(() -> {
            queueSplitPane.setDividerPositions(0.4, 0.6);
        });
        
        // Add listener to save divider positions when changed (for future persistence)
        queueSplitPane.getDividers().forEach(divider -> {
            divider.positionProperty().addListener((obs, oldPos, newPos) -> {
                // Positions could be saved to settings here if needed
            });
        });
        
        logger.info("Queue split pane configured with resizable sections");
    }

    // ========== Queue ObservableLists ==========

    private final ObservableList<ConversionJob> queuedFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> processingFiles = FXCollections.observableArrayList();
    private final ObservableList<ConversionJob> completedFiles = FXCollections.observableArrayList();


    // ========== Queue table FXML fields ==========

    // Queue Table
    // Queued Files Table
    @FXML private TableView<ConversionJob> queuedTable;
    @FXML private TableColumn<ConversionJob, String> queuedStatusColumn;
    @FXML private TableColumn<ConversionJob, String> queuedFileColumn;
    @FXML private TableColumn<ConversionJob, String> queuedOutputColumn;
    @FXML private TableColumn<ConversionJob, String> queuedSizeColumn;
    @FXML private Label queuedCountLabel;
    @FXML private Button removeQueuedButton;
    @FXML private Button clearQueuedButton;
    
    // Processing Files Table
    @FXML private TableView<ConversionJob> processingTable;
    @FXML private TableColumn<ConversionJob, String> procStatusColumn;
    @FXML private TableColumn<ConversionJob, String> procFileColumn;
    @FXML private TableColumn<ConversionJob, Double> procProgressColumn;
    @FXML private TableColumn<ConversionJob, String> procFpsColumn;
    @FXML private TableColumn<ConversionJob, String> procSpeedColumn;
    @FXML private TableColumn<ConversionJob, String> procEtaColumn;
    @FXML private Label processingCountLabel;
    
    // Completed Files Table
    @FXML private TableView<ConversionJob> completedTable;
    @FXML private TableColumn<ConversionJob, String> compStatusColumn;
    @FXML private TableColumn<ConversionJob, String> compFileColumn;
    @FXML private TableColumn<ConversionJob, String> compOutputColumn;
    @FXML private TableColumn<ConversionJob, String> compSizeColumn;
    @FXML private TableColumn<ConversionJob, String> compNewSizeColumn;
    @FXML private TableColumn<ConversionJob, String> compTimeColumn;
    @FXML private Label completedCountLabel;
    @FXML private Button clearCompletedButton;

}

