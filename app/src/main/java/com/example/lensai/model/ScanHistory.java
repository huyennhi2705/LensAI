package com.example.lensai.model;
public class ScanHistory {
    public String id;
    public String labelEn;
    public String description;
    public String type;
    public float confidence;
    public long timestamp;
    public String imageBase64;

    public ScanHistory() {}

    public ScanHistory(String id, String labelEn, String description, String type, float confidence, long timestamp, String imageBase64) {
        this.id = id;
        this.labelEn = labelEn;
        this.description = description;
        this.type = type;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.imageBase64 = imageBase64;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}