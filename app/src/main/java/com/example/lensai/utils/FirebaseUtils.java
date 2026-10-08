package com.example.lensai.utils;

import com.example.lensai.model.ScanHistoryItem;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class FirebaseUtils {

    private static final String DB_URL = "https://lensai-97dc7-default-rtdb.firebaseio.com/";
    private static final String GUEST_ID = "guest_user";

    public static DatabaseReference db() {
        return FirebaseDatabase.getInstance(DB_URL).getReference();
    }

    public static StorageReference storage() {
        return FirebaseStorage.getInstance().getReference();
    }

    public static String uid() {
        return GUEST_ID;
    }

    // ==================== LƯU LỊCH SỬ ====================
    public static void saveScanHistory(String label, String description, String imageUrl, float accuracy,
                                       OnCompleteListener<Void> listener) {

        ScanHistoryItem item = new ScanHistoryItem(label, description, imageUrl, accuracy);

        DatabaseReference ref = db()
                .child("scan_history")
                .child(uid())
                .push();          // tạo key tự động

        item.id = ref.getKey();   // gán id vào object

        ref.setValue(item)
                .addOnCompleteListener(listener);
    }

    // Phiên bản đơn giản (không cần listener)
    public static void saveScanHistory(String label, String description, String imageUrl, float accuracy) {
        saveScanHistory(label, description, imageUrl, accuracy, task -> {
            if (task.isSuccessful()) {
                // Lưu thành công
            } else {
                // Lỗi
            }
        });
    }
}