package com.example.lensai.model;

import android.graphics.RectF;

public class AIResult {
    public String label;
    public float confidence;
    public String type;
    public String description;
    public RectF box; // toạ độ khung, dùng trong HybridFusion

    public AIResult() {}

    public AIResult(String label, float confidence, String type) {
        this.label = label;
        this.confidence = confidence;
        this.type = type;
    }
}