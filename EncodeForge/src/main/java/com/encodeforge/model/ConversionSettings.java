package com.encodeforge.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Model for conversion settings that will be passed to Python backend
 * Settings are automatically persisted to ~/.encodeforge/settings.json
 */
public class ConversionSettings {
    private static final Logger logger = LoggerFactory.getLogger(ConversionSettings.class);
    private static final String SETTINGS_DIR = System.getProperty("user.home") + File.separator + ".encodeforge";
    private static final String SETTINGS_FILE = SETTINGS_DIR + File.separator + "settings.json";
    
    // FFmpeg paths
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    
    // General settings
    private String outputDirectory = "";
    private boolean deleteOriginal = false;
    private boolean overwriteExisting = false;
    private boolean preserveDate = true;
    private int concurrentConversions = 1;
    
    // Video settings
    private String outputFormat = "MP4";
    private String videoCodec = "H.264 NVENC (GPU)";
    private boolean hardwareDecoding = true;
    private String qualityPreset = "Medium";
    private int crfValue = 23;
    private boolean useNvenc = true;
    private String nvencPreset = "p4";
    private int nvencCq = 23;
    
    // Audio settings
    private String audioCodec = "copy";
    private String audioTrackSelection = "all";
    private String audioLanguages = "eng";
    private String audioBitrate = "Auto";
    
    // Subtitle settings
    private boolean convertSubtitles = true;
    private String subtitleFormat = "auto";
    private boolean enableWhisper = false;
    private String whisperModel = "base";
    private String whisperLanguages = "eng";
    private boolean downloadSubtitles = false;
    
    // Output settings
    private String namingPattern = "{title} - S{season}E{episode} - {episodeTitle}";
    private boolean createSubfolders = false;
    private boolean copyMetadata = true;
    private boolean stripMetadata = false;
    
    // API Keys for Subtitles
    private String opensubtitlesApiKey = "";  // DEPRECATED: Consumer API is now hardcoded in Python
    private String opensubtitlesUsername = "";  // User's OpenSubtitles username (for higher quotas)
    private String opensubtitlesPassword = "";  // User's OpenSubtitles password  (for higher quotas)
    private boolean opensubtitlesValidated = false;
    
    // API Keys for Metadata/Renamer
    private String tmdbApiKey = "";
    private String tvdbApiKey = "";
    private String omdbApiKey = "";
    private String traktApiKey = "";
    private String fanartApiKey = "";
    private String malClientId = "";
    
    // Validation status (persisted so we don't revalidate every time)
    private boolean tmdbValidated = false;
    private boolean tvdbValidated = false;
    private boolean omdbValidated = false;
    private boolean traktValidated = false;
    
    // Format patterns for renamer (saved presets)
    private String tvShowPattern = "{title} - S{season}E{episode} - {episodeTitle}";
    private String moviePattern = "{title} ({year})";
    private String animePattern = "{title} - {episode} - {episodeTitle}";
    private String customPattern = "";
    
    // Advanced settings
    private String customFFmpegArgs = "";
    private boolean twoPassEncoding = false;
    private boolean fastStart = true;
    private int threadCount = 0;
    
    // Getters and Setters
    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String ffmpegPath) { this.ffmpegPath = ffmpegPath; }
    
    public String getFfprobePath() { return ffprobePath; }
    public void setFfprobePath(String ffprobePath) { this.ffprobePath = ffprobePath; }
    
    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
    
    public boolean isDeleteOriginal() { return deleteOriginal; }
    public void setDeleteOriginal(boolean deleteOriginal) { this.deleteOriginal = deleteOriginal; }
    
    public boolean isOverwriteExisting() { return overwriteExisting; }
    public void setOverwriteExisting(boolean overwriteExisting) { this.overwriteExisting = overwriteExisting; }
    
    public boolean isPreserveDate() { return preserveDate; }
    public void setPreserveDate(boolean preserveDate) { this.preserveDate = preserveDate; }
    
    public int getConcurrentConversions() { return concurrentConversions; }
    public void setConcurrentConversions(int concurrentConversions) { this.concurrentConversions = concurrentConversions; }
    
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    
    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    
    public boolean isHardwareDecoding() { return hardwareDecoding; }
    public void setHardwareDecoding(boolean hardwareDecoding) { this.hardwareDecoding = hardwareDecoding; }
    
    public String getQualityPreset() { return qualityPreset; }
    public void setQualityPreset(String qualityPreset) { this.qualityPreset = qualityPreset; }
    
    public int getCrfValue() { return crfValue; }
    public void setCrfValue(int crfValue) { this.crfValue = crfValue; }
    
    public boolean isUseNvenc() { return useNvenc; }
    public void setUseNvenc(boolean useNvenc) { this.useNvenc = useNvenc; }
    
    public String getNvencPreset() { return nvencPreset; }
    public void setNvencPreset(String nvencPreset) { this.nvencPreset = nvencPreset; }
    
    public int getNvencCq() { return nvencCq; }
    public void setNvencCq(int nvencCq) { this.nvencCq = nvencCq; }
    
    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    
    public String getAudioTrackSelection() { return audioTrackSelection; }
    public void setAudioTrackSelection(String audioTrackSelection) { this.audioTrackSelection = audioTrackSelection; }
    
    public String getAudioLanguages() { return audioLanguages; }
    public void setAudioLanguages(String audioLanguages) { this.audioLanguages = audioLanguages; }
    
    public String getAudioBitrate() { return audioBitrate; }
    public void setAudioBitrate(String audioBitrate) { this.audioBitrate = audioBitrate; }
    
    public boolean isConvertSubtitles() { return convertSubtitles; }
    public void setConvertSubtitles(boolean convertSubtitles) { this.convertSubtitles = convertSubtitles; }
    
    public String getSubtitleFormat() { return subtitleFormat; }
    public void setSubtitleFormat(String subtitleFormat) { this.subtitleFormat = subtitleFormat; }
    
    public boolean isEnableWhisper() { return enableWhisper; }
    public void setEnableWhisper(boolean enableWhisper) { this.enableWhisper = enableWhisper; }
    
    public String getWhisperModel() { return whisperModel; }
    public void setWhisperModel(String whisperModel) { this.whisperModel = whisperModel; }
    
    public String getWhisperLanguages() { return whisperLanguages; }
    public void setWhisperLanguages(String whisperLanguages) { this.whisperLanguages = whisperLanguages; }
    
    public boolean isDownloadSubtitles() { return downloadSubtitles; }
    public void setDownloadSubtitles(boolean downloadSubtitles) { this.downloadSubtitles = downloadSubtitles; }
    
    public String getNamingPattern() { return namingPattern; }
    public void setNamingPattern(String namingPattern) { this.namingPattern = namingPattern; }
    
    public boolean isCreateSubfolders() { return createSubfolders; }
    public void setCreateSubfolders(boolean createSubfolders) { this.createSubfolders = createSubfolders; }
    
    public boolean isCopyMetadata() { return copyMetadata; }
    public void setCopyMetadata(boolean copyMetadata) { this.copyMetadata = copyMetadata; }
    
    public boolean isStripMetadata() { return stripMetadata; }
    public void setStripMetadata(boolean stripMetadata) { this.stripMetadata = stripMetadata; }
    
    public String getCustomFFmpegArgs() { return customFFmpegArgs; }
    public void setCustomFFmpegArgs(String customFFmpegArgs) { this.customFFmpegArgs = customFFmpegArgs; }
    
    public boolean isTwoPassEncoding() { return twoPassEncoding; }
    public void setTwoPassEncoding(boolean twoPassEncoding) { this.twoPassEncoding = twoPassEncoding; }
    
    public boolean isFastStart() { return fastStart; }
    public void setFastStart(boolean fastStart) { this.fastStart = fastStart; }
    
    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
    
    public String getTmdbApiKey() { return tmdbApiKey; }
    public void setTmdbApiKey(String tmdbApiKey) { this.tmdbApiKey = tmdbApiKey; }
    
    public String getTvdbApiKey() { return tvdbApiKey; }
    public void setTvdbApiKey(String tvdbApiKey) { this.tvdbApiKey = tvdbApiKey; }
    
    public String getOpensubtitlesApiKey() { return opensubtitlesApiKey; }
    public void setOpensubtitlesApiKey(String opensubtitlesApiKey) { this.opensubtitlesApiKey = opensubtitlesApiKey; }
    
    public String getOpensubtitlesUsername() { return opensubtitlesUsername; }
    public void setOpensubtitlesUsername(String opensubtitlesUsername) { this.opensubtitlesUsername = opensubtitlesUsername; }
    
    public String getOpensubtitlesPassword() { return opensubtitlesPassword; }
    public void setOpensubtitlesPassword(String opensubtitlesPassword) { this.opensubtitlesPassword = opensubtitlesPassword; }
    
    public boolean isOpensubtitlesValidated() { return opensubtitlesValidated; }
    public void setOpensubtitlesValidated(boolean opensubtitlesValidated) { this.opensubtitlesValidated = opensubtitlesValidated; }
    
    public String getOmdbApiKey() { return omdbApiKey; }
    public void setOmdbApiKey(String omdbApiKey) { this.omdbApiKey = omdbApiKey; }
    
    public String getTraktApiKey() { return traktApiKey; }
    public void setTraktApiKey(String traktApiKey) { this.traktApiKey = traktApiKey; }
    
    public String getFanartApiKey() { return fanartApiKey; }
    public void setFanartApiKey(String fanartApiKey) { this.fanartApiKey = fanartApiKey; }
    
    public String getMalClientId() { return malClientId; }
    public void setMalClientId(String malClientId) { this.malClientId = malClientId; }
    
    public boolean isTmdbValidated() { return tmdbValidated; }
    public void setTmdbValidated(boolean tmdbValidated) { this.tmdbValidated = tmdbValidated; }
    
    public boolean isTvdbValidated() { return tvdbValidated; }
    public void setTvdbValidated(boolean tvdbValidated) { this.tvdbValidated = tvdbValidated; }
    
    public boolean isOmdbValidated() { return omdbValidated; }
    public void setOmdbValidated(boolean omdbValidated) { this.omdbValidated = omdbValidated; }
    
    public boolean isTraktValidated() { return traktValidated; }
    public void setTraktValidated(boolean traktValidated) { this.traktValidated = traktValidated; }
    
    public String getTvShowPattern() { return tvShowPattern; }
    public void setTvShowPattern(String tvShowPattern) { this.tvShowPattern = tvShowPattern; }
    
    public String getMoviePattern() { return moviePattern; }
    public void setMoviePattern(String moviePattern) { this.moviePattern = moviePattern; }
    
    public String getAnimePattern() { return animePattern; }
    public void setAnimePattern(String animePattern) { this.animePattern = animePattern; }
    
    public String getCustomPattern() { return customPattern; }
    public void setCustomPattern(String customPattern) { this.customPattern = customPattern; }
    
    /**
     * Restore default settings
     */
    public void restoreDefaults() {
        ffmpegPath = "ffmpeg";
        ffprobePath = "ffprobe";
        outputDirectory = "";
        deleteOriginal = false;
        overwriteExisting = false;
        preserveDate = true;
        concurrentConversions = 1;
        outputFormat = "MP4";
        videoCodec = "H.264 NVENC (GPU)";
        hardwareDecoding = true;
        qualityPreset = "Medium";
        crfValue = 23;
        useNvenc = true;
        nvencPreset = "p4";
        nvencCq = 23;
        audioCodec = "copy";
        audioTrackSelection = "all";
        audioLanguages = "eng";
        audioBitrate = "Auto";
        convertSubtitles = true;
        subtitleFormat = "auto";
        enableWhisper = false;
        whisperModel = "base";
        whisperLanguages = "eng";
        downloadSubtitles = false;
        namingPattern = "{title} - S{season}E{episode} - {episodeTitle}";
        createSubfolders = false;
        copyMetadata = true;
        stripMetadata = false;
        customFFmpegArgs = "";
        twoPassEncoding = false;
        fastStart = true;
        threadCount = 0;
        // API Keys
        tmdbApiKey = "";
        tvdbApiKey = "";
        omdbApiKey = "";
        traktApiKey = "";
        fanartApiKey = "";
        malClientId = "";
        opensubtitlesApiKey = "";
        opensubtitlesUsername = "";
        opensubtitlesPassword = "";
        
        // Validation status
        opensubtitlesValidated = false;
        tmdbValidated = false;
        tvdbValidated = false;
        omdbValidated = false;
        traktValidated = false;
        
        // Format patterns
        tvShowPattern = "{title} - S{season}E{episode} - {episodeTitle}";
        moviePattern = "{title} ({year})";
        animePattern = "{title} - {episode} - {episodeTitle}";
        customPattern = "";
    }
    
    /**
     * Load settings from disk
     * @return ConversionSettings instance, either loaded from file or with defaults
     */
    public static ConversionSettings load() {
        File settingsFile = new File(SETTINGS_FILE);
        
        if (settingsFile.exists()) {
            try (FileReader reader = new FileReader(settingsFile)) {
                Gson gson = new Gson();
                ConversionSettings settings = gson.fromJson(reader, ConversionSettings.class);
                logger.info("Settings loaded from: {}", SETTINGS_FILE);
                return settings;
            } catch (IOException e) {
                logger.error("Failed to load settings from file", e);
            }
        } else {
            logger.info("No settings file found, using defaults");
        }
        
        return new ConversionSettings();
    }
    
    /**
     * Save settings to disk
     * @return true if successful, false otherwise
     */
    public boolean save() {
        try {
            // Create directory if it doesn't exist
            Path settingsDir = Paths.get(SETTINGS_DIR);
            if (!Files.exists(settingsDir)) {
                Files.createDirectories(settingsDir);
                logger.info("Created settings directory: {}", SETTINGS_DIR);
            }
            
            // Save settings as JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(SETTINGS_FILE)) {
                gson.toJson(this, writer);
                logger.info("Settings saved to: {}", SETTINGS_FILE);
                return true;
            }
            
        } catch (IOException e) {
            logger.error("Failed to save settings to file", e);
            return false;
        }
    }
    
    /**
     * Get the settings file path
     * @return the full path to the settings file
     */
    public static String getSettingsFilePath() {
        return SETTINGS_FILE;
    }
    
    /**
     * Convert to JSON for Python backend
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        // FFmpeg paths
        json.addProperty("ffmpeg_path", ffmpegPath);
        json.addProperty("ffprobe_path", ffprobePath);
        
        // General
        json.addProperty("output_directory", outputDirectory);
        json.addProperty("delete_original", deleteOriginal);
        json.addProperty("overwrite_existing", overwriteExisting);
        json.addProperty("preserve_date", preserveDate);
        
        // Video
        json.addProperty("output_format", outputFormat.toLowerCase());
        json.addProperty("video_codec", videoCodec);
        json.addProperty("hardware_decoding", hardwareDecoding);
        json.addProperty("use_nvenc", useNvenc);
        json.addProperty("nvenc_preset", nvencPreset);
        json.addProperty("nvenc_cq", nvencCq);
        json.addProperty("quality_preset", qualityPreset.toLowerCase());
        json.addProperty("crf", crfValue);
        
        // Audio
        json.addProperty("audio_codec", audioCodec);
        json.addProperty("audio_track_selection", audioTrackSelection);
        json.addProperty("audio_languages", audioLanguages);
        json.addProperty("audio_bitrate", audioBitrate);
        
        // Subtitles
        json.addProperty("convert_subtitles", convertSubtitles);
        json.addProperty("subtitle_format", subtitleFormat);
        json.addProperty("enable_whisper", enableWhisper);
        json.addProperty("whisper_model", whisperModel);
        json.addProperty("whisper_languages", whisperLanguages);
        json.addProperty("download_subtitles", downloadSubtitles);
        
        // Output
        json.addProperty("naming_pattern", namingPattern);
        json.addProperty("create_subfolders", createSubfolders);
        json.addProperty("copy_metadata", copyMetadata);
        json.addProperty("strip_metadata", stripMetadata);
        
        // API Keys - Subtitles (Consumer API is hardcoded in Python now)
        json.addProperty("opensubtitles_api_key", opensubtitlesApiKey);  // Deprecated
        json.addProperty("opensubtitles_username", opensubtitlesUsername);  // For user login
        json.addProperty("opensubtitles_password", opensubtitlesPassword);  // For user login
        json.addProperty("opensubtitles_validated", opensubtitlesValidated);
        
        // API Keys - Metadata
        json.addProperty("tmdb_api_key", tmdbApiKey);
        json.addProperty("tvdb_api_key", tvdbApiKey);
        json.addProperty("omdb_api_key", omdbApiKey);
        json.addProperty("trakt_api_key", traktApiKey);
        json.addProperty("fanart_api_key", fanartApiKey);
        json.addProperty("mal_client_id", malClientId);
        
        // Validation status
        json.addProperty("tmdb_validated", tmdbValidated);
        json.addProperty("tvdb_validated", tvdbValidated);
        json.addProperty("omdb_validated", omdbValidated);
        json.addProperty("trakt_validated", traktValidated);
        
        // Format patterns
        json.addProperty("tv_show_pattern", tvShowPattern);
        json.addProperty("movie_pattern", moviePattern);
        json.addProperty("anime_pattern", animePattern);
        json.addProperty("custom_pattern", customPattern);
        
        // Advanced
        json.addProperty("custom_args", customFFmpegArgs);
        json.addProperty("two_pass", twoPassEncoding);
        json.addProperty("use_faststart", fastStart);
        json.addProperty("threads", threadCount);
        
        return json;
    }
}
