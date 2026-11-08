package com.example.lensai.model;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

/**
 * Một bản ghi trong Realtime Database: scan_history/{user_id}/{scan_id}
 * Tên trường trong Java giữ nguyên để adapter / màn hình khác không phải sửa.
 */
@IgnoreExtraProperties
public class ScanHistory {

    /** scan_id: chính là khóa của node, không lưu thành trường riêng khi đọc. */
    @Exclude
    public String id;

    // ===== Các trường theo bảng scan_history =====
    @PropertyName("user_id")
    public String userId;

    @PropertyName("image_path")
    public String imagePath;

    @PropertyName("created_at")
    public long timestamp;

    // ===== Thông tin đồ vật =====
    @PropertyName("label_en")
    public String labelEn;

    @PropertyName("label_vi")
    public String labelVi;

    public String description;

    @PropertyName("description_en")
    public String descriptionEn;

    @PropertyName("description_vi")
    public String descriptionVi;

    public String category;
    public String type;
    public float confidence;

    @PropertyName("object_count")
    public int objectCount;

    @PropertyName("objects_list_en")
    public String objectsListEn;

    @PropertyName("objects_list_vi")
    public String objectsListVi;

    public String source;

    @PropertyName("image_base64")
    public String imageBase64;

    public ScanHistory() {
        // bắt buộc cho Firebase
    }
}