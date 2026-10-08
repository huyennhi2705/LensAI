package com.example.lensai.model;

public class ScanHistoryItem {
    public String id;           // push key
    public String label;
    public String description;
    public String imageUrl;
    public long timestamp;
    public float accuracy;

    // Bắt buộc phải có constructor rỗng cho Firebase
    public ScanHistoryItem() {}

    public ScanHistoryItem(String label, String description, String imageUrl, float accuracy) {
        this.label = label;
        this.description = description;
        this.imageUrl = imageUrl;
        this.accuracy = accuracy;
        this.timestamp = System.currentTimeMillis();
    }
}