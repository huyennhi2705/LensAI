package com.example.lensai.model;

public class AIResult {

    // Trường cũ (Gemini đang dùng)
    public String label;
    public float  confidence;
    public String source;
    public String description;      // có thể null với YOLO
    public float  x1, y1, x2, y2;   // bbox – = 0 nếu không có

    // Trường mới cho người khiếm thị
    public String  labelVi;
    public boolean speakable = true;

    // Constructor 3 tham số (dùng cho YOLO / AIService)
    public AIResult(String label, float confidence, String source) {
        this.label      = label;
        this.labelVi    = label;
        this.confidence = confidence;
        this.source     = source;
    }

    // Constructor 8 tham số (dùng cho Gemini) – KHÔI PHỤC ĐÚNG CHỮ KÝ ĐANG THIẾU
    public AIResult(String label,
                    float confidence,
                    String source,
                    String description,
                    float x1, float y1, float x2, float y2) {
        this.label       = label;
        this.labelVi     = label;
        this.confidence  = confidence;
        this.source      = source;
        this.description = description;
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }
}