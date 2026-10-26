package com.example.lensai.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ScanHistory {

    @Exclude
    public String id;

    // Thông tin chính
    public String labelEn;
    public String labelVi;
    public String description;        // giữ field cũ
    public String descriptionEn;
    public String descriptionVi;
    public String category;
    public String type;
    public float confidence;
    public long timestamp;
    public String imageBase64;

    // ===== MỚI: đếm + danh sách =====
    public int objectCount;
    public String objectsListEn;
    public String objectsListVi;
    // ================================

    public ScanHistory() {
        // bắt buộc cho Firestore
    }
}