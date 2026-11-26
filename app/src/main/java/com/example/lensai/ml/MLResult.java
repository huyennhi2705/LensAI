package com.example.lensai.ml;

import android.graphics.RectF;

/**
 * Một vật thể do YoloDetector phát hiện.
 * box: toạ độ chuẩn hoá 0..1 trên ẢNH ĐẦU VÀO (đã xoay đúng chiều), không phải trên ảnh 224x224.
 */
public class MLResult {
    public final int classId;
    public final String label;
    public final float confidence;
    public final RectF box;

    public MLResult(int classId, String label, float confidence, RectF box) {
        this.classId = classId;
        this.label = label;
        this.confidence = confidence;
        this.box = box;
    }

    public float centerX() { return (box.left + box.right) / 2f; }
    public float centerY() { return (box.top + box.bottom) / 2f; }
}