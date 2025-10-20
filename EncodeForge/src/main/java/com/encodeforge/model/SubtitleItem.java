package com.encodeforge.model;

import javafx.beans.property.*;

/**
 * SubtitleItem - Represents a subtitle item in the table
 * Extracted from MainController inner class
 */
public class SubtitleItem {
    private final SimpleBooleanProperty selected;
    private final SimpleStringProperty language;
    private final SimpleStringProperty provider;
    private final SimpleDoubleProperty score;
    private final SimpleStringProperty format;
    private final String fileId;
    private final String downloadUrl;
    private final boolean manualDownloadOnly;
    
    public SubtitleItem(boolean selected, String language, String provider, double score, 
                      String format, String fileId, String downloadUrl, boolean manualDownloadOnly) {
        this.selected = new SimpleBooleanProperty(selected);
        this.language = new SimpleStringProperty(language);
        this.provider = new SimpleStringProperty(provider);
        this.score = new SimpleDoubleProperty(score);
        this.format = new SimpleStringProperty(format);
        this.fileId = fileId;
        this.downloadUrl = downloadUrl;
        this.manualDownloadOnly = manualDownloadOnly;
    }
    
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean value) { selected.set(value); }
    public BooleanProperty selectedProperty() { return selected; }
    
    public String getLanguage() { return language.get(); }
    public StringProperty languageProperty() { return language; }
    
    public String getProvider() { return provider.get(); }
    public StringProperty providerProperty() { return provider; }
    
    public double getScore() { return score.get(); }
    public DoubleProperty scoreProperty() { return score; }
    
    public String getFormat() { return format.get(); }
    public StringProperty formatProperty() { return format; }
    
    public String getFileId() { return fileId; }
    public String getDownloadUrl() { return downloadUrl; }
    public boolean isManualDownloadOnly() { return manualDownloadOnly; }
}
