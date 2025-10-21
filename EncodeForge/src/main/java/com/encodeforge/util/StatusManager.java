package com.encodeforge.util;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Singleton manager for centralized application status information.
 * Stores status of all external services/providers (FFmpeg, Whisper, subtitle providers, metadata providers, etc.)
 * to avoid redundant checks across different controllers.
 */
public class StatusManager {
    private static final Logger logger = LoggerFactory.getLogger(StatusManager.class);
    private static StatusManager instance;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // FFmpeg Status
    private boolean ffmpegAvailable = false;
    private String ffmpegVersion = "Unknown";
    
    // Whisper Status
    private boolean whisperAvailable = false;
    private String whisperVersion = "Unknown";
    
    // OpenSubtitles Status
    private boolean openSubtitlesAvailable = false;
    private boolean openSubtitlesLoggedIn = false;
    private String openSubtitlesStatus = "Unknown";
    
    // Metadata Provider Status (configured from settings)
    private boolean tmdbConfigured = false;
    private boolean tvdbConfigured = false;
    private boolean omdbConfigured = false;
    private boolean traktConfigured = false;
    private boolean fanartConfigured = false;
    
    // Free providers (always available)
    private boolean anidbAvailable = true;
    private boolean kitsuAvailable = true;
    private boolean jikanAvailable = true;
    private boolean tvmazeAvailable = true;
    
    // Subtitle Provider Status
    private int availableSubtitleProviders = 0;
    
    // Last update timestamp
    private long lastUpdateTime = 0;
    
    private StatusManager() {
        logger.info("StatusManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized StatusManager getInstance() {
        if (instance == null) {
            instance = new StatusManager();
        }
        return instance;
    }
    
    /**
     * Update all status information from a consolidated response
     */
    public void updateFromResponse(JsonObject response) {
        lock.writeLock().lock();
        try {
            logger.debug("Updating status from consolidated response");
            
            // FFmpeg status
            if (response.has("ffmpeg")) {
                JsonObject ffmpeg = response.getAsJsonObject("ffmpeg");
                ffmpegAvailable = ffmpeg.has("available") && ffmpeg.get("available").getAsBoolean();
                ffmpegVersion = ffmpeg.has("version") ? ffmpeg.get("version").getAsString() : "Unknown";
                logger.debug("FFmpeg: available={}, version={}", ffmpegAvailable, ffmpegVersion);
            }
            
            // Whisper status
            if (response.has("whisper")) {
                JsonObject whisper = response.getAsJsonObject("whisper");
                whisperAvailable = whisper.has("available") && whisper.get("available").getAsBoolean();
                whisperVersion = whisper.has("version") ? whisper.get("version").getAsString() : "Unknown";
                logger.debug("Whisper: available={}, version={}", whisperAvailable, whisperVersion);
            }
            
            // OpenSubtitles status
            if (response.has("opensubtitles")) {
                JsonObject openSubs = response.getAsJsonObject("opensubtitles");
                openSubtitlesAvailable = openSubs.has("available") && openSubs.get("available").getAsBoolean();
                openSubtitlesLoggedIn = openSubs.has("logged_in") && openSubs.get("logged_in").getAsBoolean();
                openSubtitlesStatus = openSubs.has("status") ? openSubs.get("status").getAsString() : "Unknown";
                logger.debug("OpenSubtitles: available={}, logged_in={}, status={}", 
                    openSubtitlesAvailable, openSubtitlesLoggedIn, openSubtitlesStatus);
            }
            
            // Metadata providers status
            if (response.has("metadata_providers")) {
                JsonObject providers = response.getAsJsonObject("metadata_providers");
                tmdbConfigured = providers.has("tmdb") && providers.get("tmdb").getAsBoolean();
                tvdbConfigured = providers.has("tvdb") && providers.get("tvdb").getAsBoolean();
                omdbConfigured = providers.has("omdb") && providers.get("omdb").getAsBoolean();
                traktConfigured = providers.has("trakt") && providers.get("trakt").getAsBoolean();
                fanartConfigured = providers.has("fanart") && providers.get("fanart").getAsBoolean();
                logger.debug("Metadata providers: TMDB={}, TVDB={}, OMDB={}, Trakt={}, Fanart={}", 
                    tmdbConfigured, tvdbConfigured, omdbConfigured, traktConfigured, fanartConfigured);
            }
            
            // Subtitle providers count
            if (response.has("subtitle_providers")) {
                JsonObject subProviders = response.getAsJsonObject("subtitle_providers");
                availableSubtitleProviders = subProviders.has("count") ? 
                    subProviders.get("count").getAsInt() : 0;
                logger.debug("Subtitle providers: {} available", availableSubtitleProviders);
            }
            
            lastUpdateTime = System.currentTimeMillis();
            logger.info("Status update completed at {}", lastUpdateTime);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // FFmpeg getters
    public boolean isFFmpegAvailable() {
        lock.readLock().lock();
        try {
            return ffmpegAvailable;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public String getFFmpegVersion() {
        lock.readLock().lock();
        try {
            return ffmpegVersion;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Whisper getters
    public boolean isWhisperAvailable() {
        lock.readLock().lock();
        try {
            return whisperAvailable;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public String getWhisperVersion() {
        lock.readLock().lock();
        try {
            return whisperVersion;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // OpenSubtitles getters
    public boolean isOpenSubtitlesAvailable() {
        lock.readLock().lock();
        try {
            return openSubtitlesAvailable;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isOpenSubtitlesLoggedIn() {
        lock.readLock().lock();
        try {
            return openSubtitlesLoggedIn;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public String getOpenSubtitlesStatus() {
        lock.readLock().lock();
        try {
            return openSubtitlesStatus;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Metadata provider getters
    public boolean isTmdbConfigured() {
        lock.readLock().lock();
        try {
            return tmdbConfigured;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isTvdbConfigured() {
        lock.readLock().lock();
        try {
            return tvdbConfigured;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isOmdbConfigured() {
        lock.readLock().lock();
        try {
            return omdbConfigured;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isTraktConfigured() {
        lock.readLock().lock();
        try {
            return traktConfigured;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isFanartConfigured() {
        lock.readLock().lock();
        try {
            return fanartConfigured;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Free provider getters (always available)
    public boolean isAnidbAvailable() {
        return anidbAvailable;
    }
    
    public boolean isKitsuAvailable() {
        return kitsuAvailable;
    }
    
    public boolean isJikanAvailable() {
        return jikanAvailable;
    }
    
    public boolean isTvmazeAvailable() {
        return tvmazeAvailable;
    }
    
    // Subtitle providers
    public int getAvailableSubtitleProviders() {
        lock.readLock().lock();
        try {
            return availableSubtitleProviders;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get total count of available metadata providers
     */
    public int getTotalMetadataProviders() {
        lock.readLock().lock();
        try {
            int count = 4; // Always have: AniDB, Kitsu, Jikan, TVmaze (free)
            if (tmdbConfigured) count++;
            if (tvdbConfigured) count++;
            if (omdbConfigured) count++;
            if (traktConfigured) count++;
            if (fanartConfigured) count++;
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get last update timestamp
     */
    public long getLastUpdateTime() {
        lock.readLock().lock();
        try {
            return lastUpdateTime;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if status data is stale (older than 5 minutes)
     */
    public boolean isStale() {
        lock.readLock().lock();
        try {
            return lastUpdateTime == 0 || 
                   (System.currentTimeMillis() - lastUpdateTime) > 5 * 60 * 1000;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Reset all status to defaults (for testing or on error)
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            logger.info("Resetting status manager");
            ffmpegAvailable = false;
            ffmpegVersion = "Unknown";
            whisperAvailable = false;
            whisperVersion = "Unknown";
            openSubtitlesAvailable = false;
            openSubtitlesLoggedIn = false;
            openSubtitlesStatus = "Unknown";
            tmdbConfigured = false;
            tvdbConfigured = false;
            omdbConfigured = false;
            traktConfigured = false;
            fanartConfigured = false;
            availableSubtitleProviders = 0;
            lastUpdateTime = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
}

