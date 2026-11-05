package com.example.lensai.model;

import java.util.ArrayList;
import java.util.List;

public class HybridResult {
    public String nameEn = "Unknown";
    public String nameVi = "Không xác định";
    public String descEn = "";
    public String descVi = "";
    public String category = "other";
    public float confidence = 0f;
    public int objectCount = 0;
    public String objectsListEn = "";
    public String objectsListVi = "";
    /** gemini | tflite | hybrid | none */
    public String source = "gemini";
    public String tfliteHint = "";
    public String rawGeminiJson = "";
    public final List<DetectedObjectInfo> objects = new ArrayList<>();
}