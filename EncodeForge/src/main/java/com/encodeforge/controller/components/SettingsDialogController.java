package com.encodeforge.controller.components;

import com.encodeforge.service.PythonBridge;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.List;
import java.util.Set;


/**
 * SettingsDialogController - Handle settings dialog operations
 */
public class SettingsDialogController implements ISubController {
    
    public SettingsDialogController() {
        // TODO: Initialize with settings, pythonBridge
    }
    
    @Override
    public void initialize() {
        // TODO: Implement initialization
    }
    
    @Override
    public void shutdown() {
        // TODO: Implement cleanup
    }
    

    // ========== handleSettings() ==========

    @FXML
    private void handleSettings() {
        openSettings(null);
    }


    // ========== openSettings() ==========

    private void openSettings(String category) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsDialog.fxml"));
            SettingsController controller = new SettingsController();
            loader.setController(controller);
            
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
            
            Stage dialogStage = new Stage();
            dialogStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            dialogStage.setTitle("Settings - EncodeForge");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(settingsButton.getScene().getWindow());
            dialogStage.setScene(scene);
            
            controller.setDialogStage(dialogStage);
            controller.setSettings(settings);
            controller.setPythonBridge(pythonBridge);
            
            // Navigate to specific category if provided
            if (category != null) {
                controller.navigateToCategory(category);
            }
            
            dialogStage.showAndWait();
            
            if (controller.isApplied()) {
                // Save settings to disk
                if (settings.save()) {
                    log("Settings updated and saved successfully");
                } else {
                    log("ERROR: Failed to save settings");
                }
                checkProviderStatus(); // Refresh provider status after settings change
            }
            
        } catch (IOException e) {
            logger.error("Error opening settings dialog", e);
            showError("Error", "Failed to open settings: " + e.getMessage());
        }
    }


    // ========== checkProviderStatus() ==========

    private void checkProviderStatus() {
        new Thread(() -> {
            try {
                // First, send current settings to Python backend
                try {
                    JsonObject updateSettings = new JsonObject();
                    updateSettings.addProperty("action", "update_settings");
                    updateSettings.add("settings", settings.toJson());
                    pythonBridge.sendCommand(updateSettings);
                    logger.info("Settings synchronized with Python backend");
                } catch (Exception e) {
                    logger.error("Failed to sync settings with Python backend", e);
                }
                
                // Check FFmpeg status
                JsonObject ffmpegResponse = pythonBridge.checkFFmpeg();
                boolean ffmpegAvailable = ffmpegResponse.has("ffmpeg_available") && 
                                         ffmpegResponse.get("ffmpeg_available").getAsBoolean();
                
                // Update sidebar FFmpeg status
                Platform.runLater(() -> {
                    if (ffmpegAvailable) {
                        String version = ffmpegResponse.has("ffmpeg_version") ? 
                            ffmpegResponse.get("ffmpeg_version").getAsString() : "Unknown";
                        updateFFmpegStatus(true, version);
                    } else {
                        updateFFmpegStatus(false, "Not Found");
                    }
                });
                
                // Check Whisper status (for subtitle mode)
                JsonObject whisperResponse = pythonBridge.checkWhisper();
                boolean whisperAvailable = whisperResponse.has("whisper_available") && 
                                          whisperResponse.get("whisper_available").getAsBoolean();
                
                logger.info("Whisper status: available={}", whisperAvailable);
                
                Platform.runLater(() -> {
                    if (whisperStatusButton != null) {
                        if (whisperAvailable) {
                            whisperStatusButton.setText("🤖 Whisper: Ready ✓");
                            whisperStatusButton.getStyleClass().removeAll("error", "warning");
                            whisperStatusButton.getStyleClass().add("active");
                            whisperStatusButton.setTooltip(new Tooltip(
                                "✅ Whisper AI Available\n\n" +
                                "Generate subtitles in 90+ languages using AI.\n" +
                                "No API key or internet required!"
                            ));
                        } else {
                            whisperStatusButton.setText("🤖 Whisper: Not Installed");
                            whisperStatusButton.getStyleClass().removeAll("active");
                            whisperStatusButton.getStyleClass().add("warning");
                            whisperStatusButton.setTooltip(new Tooltip(
                                "⚠️ Whisper Not Available\n\n" +
                                "Install Whisper to generate subtitles using AI.\n" +
                                "Click to see installation instructions in Settings."
                            ));
                        }
                    }
                });
                
                // Check OpenSubtitles status
                JsonObject osResponse = pythonBridge.checkOpenSubtitles();
                boolean osConfigured = osResponse.has("configured") && 
                                      osResponse.get("configured").getAsBoolean();
                boolean hasApiKey = osResponse.has("has_api_key") && 
                                   osResponse.get("has_api_key").getAsBoolean();
                
                logger.info("OpenSubtitles status: configured={}, hasApiKey={}", osConfigured, hasApiKey);
                
                Platform.runLater(() -> {
                    if (openSubsStatusButton != null) {
                        if (osConfigured || hasApiKey) {
                            openSubsStatusButton.setText("🌐 Subtitles: 9 Providers ✓");
                            openSubsStatusButton.getStyleClass().removeAll("error", "warning");
                            openSubsStatusButton.getStyleClass().add("active");
                            openSubsStatusButton.setTooltip(new Tooltip(
                                "✅ OpenSubtitles.com (search: unlimited, download: " + (hasApiKey ? "5/day" : "0/day") + ")\n" +
                                "✅ Addic7ed (Movies, TV, Anime)\n" +
                                "✅ SubDL (Movies & TV)\n" +
                                "✅ Subf2m (Movies & TV)\n" +
                                "✅ YIFY Subtitles (Movies)\n" +
                                "✅ Podnapisi (All content, multilingual)\n" +
                                "✅ SubDivX (Spanish only)\n" +
                                "🎌 Kitsunekko (Anime - EN/JP)\n" +
                                "🎌 Jimaku (Anime only, multilingual)\n\n" +
                                "Ready to search! ✨"
                            ));
                        } else {
                            openSubsStatusButton.setText("🌐 Subtitles: 8 Free Providers");
                            openSubsStatusButton.getStyleClass().removeAll("error", "active");
                            openSubsStatusButton.getStyleClass().add("warning");
                            openSubsStatusButton.setTooltip(new Tooltip(
                                "Using free providers (no OpenSubtitles API key):\n" +
                                "✅ Addic7ed (Movies, TV, Anime)\n" +
                                "✅ SubDL (Movies & TV)\n" +
                                "✅ Subf2m (Movies & TV)\n" +
                                "✅ YIFY Subtitles (Movies)\n" +
                                "✅ Podnapisi (All content, multilingual)\n" +
                                "✅ SubDivX (Spanish only)\n" +
                                "🎌 Kitsunekko (Anime - EN/JP)\n" +
                                "🎌 Jimaku (Anime only, multilingual)\n\n" +
                                "💡 Get a FREE OpenSubtitles API key:\n" +
                                "https://www.opensubtitles.com/en/consumers\n" +
                                "Add it in Settings for 9 providers!"
                            ));
                        }
                    }
                });
                
                // Check metadata provider status from settings
                boolean tmdbConfigured = settings.getTmdbApiKey() != null && !settings.getTmdbApiKey().isEmpty();
                boolean tvdbConfigured = settings.getTvdbApiKey() != null && !settings.getTvdbApiKey().isEmpty();
                boolean omdbConfigured = settings.getOmdbApiKey() != null && !settings.getOmdbApiKey().isEmpty();
                boolean traktConfigured = settings.getTraktApiKey() != null && !settings.getTraktApiKey().isEmpty();
                boolean fanartConfigured = settings.getFanartApiKey() != null && !settings.getFanartApiKey().isEmpty();
                
                // Count available providers (4 free + any configured with keys)
                int availableProviders = 4;  // Always have: AniList, Kitsu, Jikan, TVmaze (free)
                if (tmdbConfigured) availableProviders++;
                if (tvdbConfigured) availableProviders++;
                if (omdbConfigured) availableProviders++;
                if (traktConfigured) availableProviders++;
                if (fanartConfigured) availableProviders++;
                
                final int totalProviders = availableProviders;
                
                Platform.runLater(() -> {
                    // Update TMDB button
                    if (tmdbStatusButton != null) {
                        if (tmdbConfigured) {
                            tmdbStatusButton.setText("🎬 TMDB: Ready" + (settings.isTmdbValidated() ? " ✓" : ""));
                            tmdbStatusButton.getStyleClass().removeAll("error", "warning");
                            tmdbStatusButton.getStyleClass().add("active");
                        } else {
                            tmdbStatusButton.setText("🎬 TMDB: Not Setup");
                            tmdbStatusButton.getStyleClass().removeAll("active");
                            tmdbStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // Update TVDB button
                    if (tvdbStatusButton != null) {
                        if (tvdbConfigured) {
                            tvdbStatusButton.setText("📺 TVDB ✓");
                            tvdbStatusButton.getStyleClass().removeAll("error", "warning");
                            tvdbStatusButton.getStyleClass().add("active");
                        } else {
                            tvdbStatusButton.setText("📺 TVDB ⚠️");
                            tvdbStatusButton.getStyleClass().removeAll("active");
                            tvdbStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // Update OMDB button
                    if (omdbStatusButton != null) {
                        if (omdbConfigured) {
                            omdbStatusButton.setText("🎥 OMDB ✓");
                            omdbStatusButton.getStyleClass().removeAll("error", "warning");
                            omdbStatusButton.getStyleClass().add("active");
                        } else {
                            omdbStatusButton.setText("🎥 OMDB ⚠️");
                            omdbStatusButton.getStyleClass().removeAll("active");
                            omdbStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // Update Trakt button
                    if (traktStatusButton != null) {
                        if (traktConfigured) {
                            traktStatusButton.setText("📊 Trakt ✓");
                            traktStatusButton.getStyleClass().removeAll("error", "warning");
                            traktStatusButton.getStyleClass().add("active");
                        } else {
                            traktStatusButton.setText("📊 Trakt ⚠️");
                            traktStatusButton.getStyleClass().removeAll("active");
                            traktStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // Update Fanart.tv button
                    if (fanartStatusButton != null) {
                        if (fanartConfigured) {
                            fanartStatusButton.setText("🎨 Fanart ✓");
                            fanartStatusButton.getStyleClass().removeAll("error", "warning");
                            fanartStatusButton.getStyleClass().add("active");
                        } else {
                            fanartStatusButton.setText("🎨 Fanart ⚠️");
                            fanartStatusButton.getStyleClass().removeAll("active");
                            fanartStatusButton.getStyleClass().add("warning");
                        }
                    }
                    
                    // Update status label with provider count
                    if (activeProviderLabel != null) {
                        activeProviderLabel.setText("✅ " + totalProviders + "/10 Providers Available");
                        activeProviderLabel.setStyle("-fx-text-fill: #4ec9b0; -fx-font-weight: bold;");
                    }
                    
                    // Search button is always enabled since we have free providers (AniList)
                    if (searchMetadataButton != null) {
                        searchMetadataButton.setDisable(false);
                    }
                    
                    // Apply button stays disabled until search results are loaded
                    if (applyRenameButton != null) {
                        applyRenameButton.setDisable(true);
                    }
                    
                    // Log provider status
                    log("=== Metadata Providers: " + totalProviders + "/10 available ===");
                    log("API Key Providers (Free Keys Available):");
                    if (tmdbConfigured) log("  ✓ TMDB (Movies & TV)" + (settings.isTmdbValidated() ? " - validated" : ""));
                    else log("  ⚠️ TMDB (Movies & TV) - API key needed");
                    if (tvdbConfigured) log("  ✓ TVDB (TV Shows)" + (settings.isTvdbValidated() ? " - validated" : ""));
                    else log("  ⚠️ TVDB (TV Shows) - API key needed");
                    if (omdbConfigured) log("  ✓ OMDB (Movies & TV)" + (settings.isOmdbValidated() ? " - validated" : ""));
                    else log("  ⚠️ OMDB (Movies & TV) - API key needed");
                    if (traktConfigured) log("  ✓ Trakt (Movies & TV)" + (settings.isTraktValidated() ? " - validated" : ""));
                    else log("  ⚠️ Trakt (Movies & TV) - API key needed");
                    if (fanartConfigured) log("  ✓ Fanart.tv (Artwork)");
                    else log("  ⚠️ Fanart.tv (Artwork) - API key needed");
                    log("Free Providers (No API Key Required):");
                    log("  ✅ AniList (Anime) - always available");
                    log("  ✅ Kitsu (Anime) - always available");
                    log("  ✅ Jikan/MAL (Anime) - always available");
                    log("  ✅ TVmaze (TV Shows) - always available");
                });
                
            } catch (Exception e) {
                logger.error("Error checking provider status", e);
            }
        }).start();


    // ========== handleConfigureSubtitles() ==========

    @FXML
    private void handleConfigureSubtitles() {
        openSettings("Subtitles");


    // ========== handleConfigureRenamer() ==========

    @FXML
    private void handleConfigureRenamer() {
        openSettings("Metadata");


    // ========== handleConfigureWhisper() ==========

    @FXML
    private void handleConfigureWhisper() {
        openSettings("Subtitles");


    // ========== handleConfigureOpenSubtitles() ==========

    @FXML
    private void handleConfigureOpenSubtitles() {
        openSettings("Subtitles");


    // ========== handleConfigureTMDB() ==========

    @FXML
    private void handleConfigureTMDB() {
        openSettings("Metadata");


    // ========== handleConfigureTVDB() ==========

    @FXML
    private void handleConfigureTVDB() {
        openSettings("Metadata");


    // ========== handleConfigureAniList() ==========

    @FXML
    private void handleConfigureAniList() {
        showInfo("AniList", "AniList does not require any configuration. It's ready to use!");

}

