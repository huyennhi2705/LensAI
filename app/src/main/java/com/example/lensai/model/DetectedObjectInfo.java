package com.example.lensai.model;

import android.graphics.RectF;

public class DetectedObjectInfo {
    public String nameEn = "";
    public String nameVi = "";
    public float confidence = 0f;
    public String category = "other";
    public RectF box;
}