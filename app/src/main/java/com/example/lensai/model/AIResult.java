package com.example.lensai.model;

public class AIResult {
    public String label;
    public float confidence;
    public String type;

    public AIResult() {}

    public AIResult(String label, float confidence, String type) {
        this.label = label;
        this.confidence = confidence;
        this.type = type;
    }
}