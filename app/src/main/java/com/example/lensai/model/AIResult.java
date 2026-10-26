package com.example.lensai.model;

public class AIResult {
    public String label;
    public float confidence;
    public String type;

    // Constructor đầy đủ
    public AIResult(String label, float confidence, String type) {
        this.label = label;
        this.confidence = confidence;
        this.type = type;
    }

    // Constructor rỗng (thêm dòng này)
    public AIResult() {
    }
}