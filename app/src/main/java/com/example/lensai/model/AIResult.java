package com.example.lensai.model;

public class AIResult {
    public String label;
    public float confidence;
    public String type;          // "object", "label", "text", "barcode"
    public String description;   // Mô tả tiếng Anh (Gemini)
    public float xmin, ymin, xmax, ymax; // Tọa độ chuẩn hóa 0..1

    public AIResult() {}

    public AIResult(String label, float confidence, String type) {
        this.label = label;
        this.confidence = confidence;
        this.type = type;
    }

    public AIResult(String label, float confidence, String type,
                    String description,
                    float xmin, float ymin, float xmax, float ymax) {
        this.label = label;
        this.confidence = confidence;
        this.type = type;
        this.description = description;
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
    }
}