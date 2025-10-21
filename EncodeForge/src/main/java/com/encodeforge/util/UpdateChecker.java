package com.encodeforge.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update checker for EncodeForge that checks GitHub releases
 */
public class UpdateChecker {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/SirStig/EncodeForge/releases/latest";
    private static final String CURRENT_VERSION = "0.3";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    
    /**
     * Information about an available update
     */
    public static class UpdateInfo {
        private final String version;
        private final String changelog;
        private final String downloadUrl;
        private final boolean isUpdateAvailable;
        
        public UpdateInfo(String version, String changelog, String downloadUrl, boolean isUpdateAvailable) {
            this.version = version;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.isUpdateAvailable = isUpdateAvailable;
        }
        
        public String getVersion() { return version; }
        public String getChangelog() { return changelog; }
        public String getDownloadUrl() { return downloadUrl; }
        public boolean isUpdateAvailable() { return isUpdateAvailable; }
    }
    
    /**
     * Check for updates asynchronously
     */
    public static CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Checking for updates...");
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_URL))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "EncodeForge/" + CURRENT_VERSION)
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    logger.warn("Failed to check for updates: HTTP {}", response.statusCode());
                    return new UpdateInfo(CURRENT_VERSION, "", "", false);
                }
                
                Gson gson = new Gson();
                JsonObject release = gson.fromJson(response.body(), JsonObject.class);
                
                String latestVersion = release.get("tag_name").getAsString();
                String changelog = release.has("body") ? release.get("body").getAsString() : "";
                String downloadUrl = release.has("html_url") ? release.get("html_url").getAsString() : "";
                
                // Remove 'v' prefix if present
                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1);
                }
                
                boolean isUpdateAvailable = isNewerVersion(latestVersion, CURRENT_VERSION);
                
                logger.info("Update check complete. Current: {}, Latest: {}, Update available: {}", 
                           CURRENT_VERSION, latestVersion, isUpdateAvailable);
                
                return new UpdateInfo(latestVersion, changelog, downloadUrl, isUpdateAvailable);
                
            } catch (Exception e) {
                logger.error("Error checking for updates", e);
                return new UpdateInfo(CURRENT_VERSION, "", "", false);
            }
        });
    }
    
    /**
     * Compare two version strings using semantic versioning
     */
    private static boolean isNewerVersion(String version1, String version2) {
        try {
            // Simple semantic version comparison (major.minor.patch)
            Pattern versionPattern = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
            
            Matcher matcher1 = versionPattern.matcher(version1);
            Matcher matcher2 = versionPattern.matcher(version2);
            
            if (!matcher1.matches() || !matcher2.matches()) {
                // Fallback to string comparison if not semantic versioning
                return version1.compareTo(version2) > 0;
            }
            
            int major1 = Integer.parseInt(matcher1.group(1));
            int minor1 = Integer.parseInt(matcher1.group(2));
            int patch1 = matcher1.group(3) != null ? Integer.parseInt(matcher1.group(3)) : 0;
            
            int major2 = Integer.parseInt(matcher2.group(1));
            int minor2 = Integer.parseInt(matcher2.group(2));
            int patch2 = matcher2.group(3) != null ? Integer.parseInt(matcher2.group(3)) : 0;
            
            if (major1 != major2) return major1 > major2;
            if (minor1 != minor2) return minor1 > minor2;
            return patch1 > patch2;
            
        } catch (Exception e) {
            logger.warn("Error comparing versions: {} vs {}", version1, version2, e);
            return false;
        }
    }
    
    /**
     * Show update notification dialog
     */
    public static void showUpdateDialog(UpdateInfo updateInfo) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Update Available");
            alert.setHeaderText("EncodeForge " + updateInfo.getVersion() + " is available!");
            
            // Create styled content
            VBox content = new VBox(12);
            
            // Version comparison
            VBox versionBox = new VBox(4);
            Text currentVersionText = new Text("Current version: " + CURRENT_VERSION);
            currentVersionText.setStyle("-fx-fill: #a0a0a0; -fx-font-size: 12px;");
            
            Text latestVersionText = new Text("Latest version: " + updateInfo.getVersion());
            latestVersionText.setStyle("-fx-fill: #16c60c; -fx-font-size: 12px; -fx-font-weight: bold;");
            
            versionBox.getChildren().addAll(currentVersionText, latestVersionText);
            content.getChildren().add(versionBox);
            
            // Add changelog if available
            if (updateInfo.getChangelog() != null && !updateInfo.getChangelog().trim().isEmpty()) {
                Text changelogLabel = new Text("What's new:");
                changelogLabel.setStyle("-fx-fill: #ffffff; -fx-font-size: 12px; -fx-font-weight: bold;");
                
                Text changelogText = new Text(formatChangelog(updateInfo.getChangelog()));
                changelogText.setStyle("-fx-fill: #ffffff; -fx-font-size: 11px;");
                changelogText.setWrappingWidth(450);
                
                content.getChildren().addAll(changelogLabel, changelogText);
            }
            
            // Add download link
            if (updateInfo.getDownloadUrl() != null && !updateInfo.getDownloadUrl().isEmpty()) {
                Hyperlink downloadLink = new Hyperlink("Download from GitHub");
                downloadLink.setStyle("-fx-text-fill: #0078d4; -fx-font-size: 12px; -fx-font-weight: bold;");
                downloadLink.setOnAction(e -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(URI.create(updateInfo.getDownloadUrl()));
                    } catch (Exception ex) {
                        logger.error("Error opening download URL", ex);
                    }
                });
                content.getChildren().add(downloadLink);
            }
            
            alert.getDialogPane().setContent(content);
            
            // Style the dialog
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(UpdateChecker.class.getResource("/styles/application.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");
            
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
        });
    }
    
    /**
     * Show "no updates available" dialog
     */
    public static void showNoUpdatesDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Check for Updates");
            alert.setHeaderText("You're up to date!");
            
            VBox content = new VBox(8);
            
            Text statusText = new Text("✓ EncodeForge " + CURRENT_VERSION + " is the latest version.");
            statusText.setStyle("-fx-fill: #16c60c; -fx-font-size: 14px; -fx-font-weight: bold;");
            
            Text infoText = new Text("No updates are currently available.");
            infoText.setStyle("-fx-fill: #a0a0a0; -fx-font-size: 12px;");
            
            content.getChildren().addAll(statusText, infoText);
            alert.getDialogPane().setContent(content);
            
            // Style the dialog
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(UpdateChecker.class.getResource("/styles/application.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");
            
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
        });
    }
    
    /**
     * Format changelog text for display
     */
    private static String formatChangelog(String changelog) {
        if (changelog == null || changelog.trim().isEmpty()) {
            return "";
        }
        
        // Limit to first 500 characters and clean up formatting
        String formatted = changelog.trim();
        if (formatted.length() > 500) {
            formatted = formatted.substring(0, 500) + "...";
        }
        
        // Replace markdown-style formatting with plain text
        formatted = formatted.replaceAll("\\*\\*(.*?)\\*\\*", "$1"); // Bold
        formatted = formatted.replaceAll("\\*(.*?)\\*", "$1"); // Italic
        formatted = formatted.replaceAll("`(.*?)`", "$1"); // Code
        formatted = formatted.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1"); // Links
        
        return formatted;
    }
    
    /**
     * Check for updates and show appropriate dialog
     */
    public static void checkAndShowUpdateDialog() {
        checkForUpdates().thenAccept(updateInfo -> {
            if (updateInfo.isUpdateAvailable()) {
                showUpdateDialog(updateInfo);
            } else {
                showNoUpdatesDialog();
            }
        }).exceptionally(throwable -> {
            logger.error("Error during update check", throwable);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Update Check Failed");
                alert.setHeaderText("Unable to check for updates");
                
                VBox content = new VBox(8);
                
                Text errorText = new Text("✗ Failed to check for updates");
                errorText.setStyle("-fx-fill: #d13438; -fx-font-size: 14px; -fx-font-weight: bold;");
                
                Text infoText = new Text("Please check your internet connection and try again.");
                infoText.setStyle("-fx-fill: #a0a0a0; -fx-font-size: 12px;");
                
                content.getChildren().addAll(errorText, infoText);
                alert.getDialogPane().setContent(content);
                
                DialogPane dialogPane = alert.getDialogPane();
                dialogPane.getStylesheets().add(UpdateChecker.class.getResource("/styles/application.css").toExternalForm());
                dialogPane.getStyleClass().add("dialog-pane");
                
                alert.showAndWait();
            });
            return null;
        });
    }
}
