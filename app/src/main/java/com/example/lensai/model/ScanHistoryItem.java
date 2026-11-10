package com.example.lensai.model;

public class ScanHistoryItem {
    public String id;
    public String label;
    public String description;
    public String imageUrl;
    public long timestamp;
    public float accuracy;

    public ScanHistoryItem() {}

    public ScanHistoryItem(String label, String description, String imageUrl, float accuracy) {
        this.label = label;
        this.description = description;
        this.imageUrl = imageUrl;
        this.accuracy = accuracy;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    @Override
    public String toString() {
        return "ScanHistoryItem{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", timestamp=" + timestamp +
                ", accuracy=" + accuracy +
                '}';
    }
}