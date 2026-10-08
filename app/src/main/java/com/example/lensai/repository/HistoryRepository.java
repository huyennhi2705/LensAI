package com.example.lensai.repository;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lensai.model.ScanHistory;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class HistoryRepository {

    private static final String TAG = "HistoryRepo";
    private static final String COLLECTION = "scans";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lưu kết quả scan + ảnh 224x224 (Base64) lên Firestore.
     * Không cần đăng nhập.
     */
    public void saveScan(@NonNull ScanHistory history,
                         @Nullable Bitmap image224,
                         @Nullable OnSuccessListener<Void> onSuccess,
                         @Nullable OnFailureListener onFailure) {

        history.timestamp = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("labelEn", history.labelEn != null ? history.labelEn : "");
        data.put("description", history.description != null ? history.description : "");
        data.put("type", history.type != null ? history.type : "");
        data.put("confidence", history.confidence);
        data.put("timestamp", history.timestamp);

        // Ảnh 224x224 → Base64 (nếu có)
        if (image224 != null && !image224.isRecycled()) {
            String base64 = bitmapToBase64(image224);
            data.put("imageBase64", base64);
        } else {
            data.put("imageBase64", null);
        }

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(docRef -> {
                    history.id = docRef.getId();
                    Log.d(TAG, "Saved scan: " + history.id);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Save failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    /**
     * Lấy danh sách lịch sử (mới nhất trước).
     */
    public Query getHistoryQuery() {
        return db.collection(COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING);
    }

    /**
     * Xóa 1 bản ghi theo id.
     */
    public void deleteScan(@NonNull String id,
                           @Nullable OnSuccessListener<Void> onSuccess,
                           @Nullable OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Deleted: " + id);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Delete failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    // ---------- Helper ----------

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 224x224 nên chất lượng 75 là đủ
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }
}