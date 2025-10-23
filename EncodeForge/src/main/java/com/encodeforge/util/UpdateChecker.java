package com.encodeforge.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/**
 * Update checker for EncodeForge that checks GitHub releases
 */
public class UpdateChecker {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/SirStig/EncodeForge/releases/latest";
    private static final String CURRENT_VERSION = "0.3.3";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    
    /**
     * Platform information for installer selection
     */
    public enum PlatformType {
        WINDOWS_X64("windows-x64", ".exe", "EncodeForge-%s-windows-x64.exe"),
        MACOS_X64("macos-x64", ".dmg", "EncodeForge-%s-macos-x64.dmg"),
        MACOS_ARM64("macos-arm64", ".dmg", "EncodeForge-%s-macos-arm64.dmg"),
        LINUX_X64("linux-x64", ".deb", "EncodeForge-%s-linux-x64.deb");
        
        private final String identifier;
        private final String extension;
        private final String filenamePattern;
        
        PlatformType(String identifier, String extension, String filenamePattern) {
            this.identifier = identifier;
            this.extension = extension;
            this.filenamePattern = filenamePattern;
        }
        
        public String getIdentifier() { return identifier; }
        public String getExtension() { return extension; }
        public String getFilenamePattern() { return filenamePattern; }
        
        public String getInstallerFilename(String version) {
            return String.format(filenamePattern, version);
        }
    }
    
    /**
     * Information about an available update
     */
    public static class UpdateInfo {
        private final String version;
        private final String changelog;
        private final String downloadUrl;
        private final String installerUrl;
        private final String installerFilename;
        private final PlatformType platform;
        private final boolean isUpdateAvailable;
        
        public UpdateInfo(String version, String changelog, String downloadUrl, String installerUrl, 
                         String installerFilename, PlatformType platform, boolean isUpdateAvailable) {
            this.version = version;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.installerUrl = installerUrl;
            this.installerFilename = installerFilename;
            this.platform = platform;
            this.isUpdateAvailable = isUpdateAvailable;
        }
        
        public String getVersion() { return version; }
        public String getChangelog() { return changelog; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getInstallerUrl() { return installerUrl; }
        public String getInstallerFilename() { return installerFilename; }
        public PlatformType getPlatform() { return platform; }
        public boolean isUpdateAvailable() { return isUpdateAvailable; }
    }
    
    /**
     * Detect the current platform
     */
    public static PlatformType detectPlatform() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        
        if (osName.contains("win")) {
            return PlatformType.WINDOWS_X64;
        } else if (osName.contains("mac")) {
            // Check for Apple Silicon (ARM64) vs Intel
            if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                return PlatformType.MACOS_ARM64;
            } else {
                return PlatformType.MACOS_X64;
            }
        } else {
            // Linux and other Unix-like systems
            return PlatformType.LINUX_X64;
        }
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
                    PlatformType currentPlatform = detectPlatform();
                    return new UpdateInfo(CURRENT_VERSION, "", "", "", "", currentPlatform, false);
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
                
                // Detect current platform and construct installer URL
                PlatformType currentPlatform = detectPlatform();
                String installerFilename = currentPlatform.getInstallerFilename(latestVersion);
                String installerUrl = String.format("https://github.com/SirStig/EncodeForge/releases/download/v%s/%s", 
                                                  latestVersion, installerFilename);
                
                logger.info("Update check complete. Current: {}, Latest: {}, Update available: {}, Platform: {}", 
                           CURRENT_VERSION, latestVersion, isUpdateAvailable, currentPlatform.getIdentifier());
                
                return new UpdateInfo(latestVersion, changelog, downloadUrl, installerUrl, 
                                    installerFilename, currentPlatform, isUpdateAvailable);
                
            } catch (Exception e) {
                logger.error("Error checking for updates", e);
                PlatformType currentPlatform = detectPlatform();
                return new UpdateInfo(CURRENT_VERSION, "", "", "", "", currentPlatform, false);
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
     * Download installer asynchronously
     */
    public static CompletableFuture<Path> downloadInstaller(UpdateInfo updateInfo, 
                                                           java.util.function.Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Downloading installer: {}", updateInfo.getInstallerUrl());
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(updateInfo.getInstallerUrl()))
                        .timeout(Duration.ofMinutes(10)) // Longer timeout for downloads
                        .header("User-Agent", "EncodeForge/" + CURRENT_VERSION)
                        .GET()
                        .build();
                
                HttpResponse<java.io.InputStream> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofInputStream());
                
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download installer: HTTP " + response.statusCode());
                }
                
                // Create temp file for installer
                Path tempDir = Files.createTempDirectory("encodeforge-update");
                Path installerFile = tempDir.resolve(updateInfo.getInstallerFilename());
                
                // Copy installer to temp file
                try (java.io.InputStream inputStream = response.body()) {
                    Files.copy(inputStream, installerFile, StandardCopyOption.REPLACE_EXISTING);
                }
                
                logger.info("Installer downloaded successfully: {}", installerFile);
                return installerFile;
                
            } catch (Exception e) {
                logger.error("Failed to download installer", e);
                throw new RuntimeException("Failed to download installer", e);
            }
        });
    }
    
    /**
     * Launch installer and terminate application
     */
    public static void launchInstaller(Path installerFile, UpdateInfo updateInfo) {
        try {
            logger.info("Launching installer: {}", installerFile);
            
            ProcessBuilder processBuilder;
            PlatformType platform = updateInfo.getPlatform();
            
            switch (platform) {
                case WINDOWS_X64:
                    // Windows: Run installer directly
                    processBuilder = new ProcessBuilder(installerFile.toString());
                    break;
                    
                case MACOS_X64:
                case MACOS_ARM64:
                    // macOS: Mount DMG and run installer
                    processBuilder = new ProcessBuilder("open", installerFile.toString());
                    break;
                    
                case LINUX_X64:
                    // Linux: Install DEB package
                    processBuilder = new ProcessBuilder("sudo", "dpkg", "-i", installerFile.toString());
                    break;
                    
                default:
                    throw new UnsupportedOperationException("Unsupported platform: " + platform);
            }
            
            // Start installer process
            processBuilder.start();
            
            // Terminate current application after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Give installer time to start
                    logger.info("Terminating application for update");
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
        } catch (Exception e) {
            logger.error("Failed to launch installer", e);
            throw new RuntimeException("Failed to launch installer", e);
        }
    }
    
    /**
     * Show update notification dialog
     */
    public static void showUpdateDialog(UpdateInfo updateInfo) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
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
            
            Text platformText = new Text("Platform: " + updateInfo.getPlatform().getIdentifier());
            platformText.setStyle("-fx-fill: #a0a0a0; -fx-font-size: 11px;");
            
            versionBox.getChildren().addAll(currentVersionText, latestVersionText, platformText);
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
            
            // Add installer info
            Text installerLabel = new Text("Installer: " + updateInfo.getInstallerFilename());
            installerLabel.setStyle("-fx-fill: #ffffff; -fx-font-size: 11px;");
            content.getChildren().add(installerLabel);
            
            alert.getDialogPane().setContent(content);
            
            // Style the dialog
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(UpdateChecker.class.getResource("/styles/application.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");
            
            // Add custom buttons
            ButtonType downloadAndInstallButton = new ButtonType("Download & Install");
            ButtonType downloadOnlyButton = new ButtonType("Download Only");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            alert.getButtonTypes().setAll(downloadAndInstallButton, downloadOnlyButton, cancelButton);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == downloadAndInstallButton) {
                    // Download and install
                    downloadAndInstallUpdate(updateInfo);
                } else if (result.get() == downloadOnlyButton) {
                    // Download only
                    downloadUpdateOnly(updateInfo);
                }
            }
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
    
    /**
     * Download and install update
     */
    private static void downloadAndInstallUpdate(UpdateInfo updateInfo) {
        Platform.runLater(() -> {
            Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Downloading Update");
            progressAlert.setHeaderText("Downloading EncodeForge " + updateInfo.getVersion());
            
            VBox content = new VBox(12);
            Text statusText = new Text("Downloading installer...");
            statusText.setStyle("-fx-fill: #ffffff; -fx-font-size: 14px;");
            content.getChildren().add(statusText);
            
            progressAlert.getDialogPane().setContent(content);
            progressAlert.getButtonTypes().clear();
            progressAlert.show();
            
            // Download installer
            downloadInstaller(updateInfo, progress -> {
                Platform.runLater(() -> {
                    statusText.setText(progress);
                });
            }).thenAccept(installerFile -> {
                Platform.runLater(() -> {
                    progressAlert.close();
                    // Launch installer
                    launchInstaller(installerFile, updateInfo);
                });
            }).exceptionally(throwable -> {
                Platform.runLater(() -> {
                    progressAlert.close();
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Download Failed");
                    errorAlert.setHeaderText("Failed to download update");
                    errorAlert.setContentText("Error: " + throwable.getMessage());
                    errorAlert.showAndWait();
                });
                return null;
            });
        });
    }
    
    /**
     * Download update only (open download folder)
     */
    private static void downloadUpdateOnly(UpdateInfo updateInfo) {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(updateInfo.getInstallerUrl()));
        } catch (Exception e) {
            logger.error("Error opening download URL", e);
            Platform.runLater(() -> {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Download Failed");
                errorAlert.setHeaderText("Failed to open download URL");
                errorAlert.setContentText("Please visit: " + updateInfo.getInstallerUrl());
                errorAlert.showAndWait();
            });
        }
    }
}
