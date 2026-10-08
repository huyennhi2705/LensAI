package com.example.lensai.service;

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

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class StorageService {

    private static final String TAG = "StorageService";
    private static final String COLLECTION = "scans";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Lưu ảnh 224x224 (Base64) + thông tin nhận diện lên Firestore.
     * Không cần đăng nhập / không dùng Firebase Storage.
     *
     * @param history   object chứa label, description, confidence...
     * @param bitmap224 ảnh đã cắt & resize 224x224 (có thể null)
     */
    public void saveScan(@NonNull ScanHistory history,
                         @Nullable Bitmap bitmap224,
                         @Nullable OnSuccessListener<DocumentReference> onSuccess,
                         @Nullable OnFailureListener onFailure) {

        history.timestamp = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("labelEn", history.labelEn != null ? history.labelEn : "");
        data.put("description", history.description != null ? history.description : "");
        data.put("type", history.type != null ? history.type : "");
        data.put("confidence", history.confidence);
        data.put("timestamp", history.timestamp);

        if (bitmap224 != null && !bitmap224.isRecycled()) {
            data.put("imageBase64", bitmapToBase64(bitmap224));
        } else {
            data.put("imageBase64", null);
        }

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(docRef -> {
                    history.id = docRef.getId();
                    Log.d(TAG, "Lưu thành công: " + history.id);
                    if (onSuccess != null) onSuccess.onSuccess(docRef);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi lưu Firestore", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    /**
     * Overload tiện dụng: chỉ cần bitmap + chuỗi kết quả.
     */
    public void saveScan(@Nullable Bitmap bitmap224,
                         @Nullable String result,
                         @Nullable OnSuccessListener<DocumentReference> onSuccess,
                         @Nullable OnFailureListener onFailure) {

        ScanHistory history = new ScanHistory();
        history.labelEn = result != null ? result : "";
        history.description = result != null ? ("This is a " + result.toLowerCase() + ".") : "";
        history.confidence = 0f;
        history.timestamp = System.currentTimeMillis();

        saveScan(history, bitmap224, onSuccess, onFailure);
    }

    // ---------- Helper ----------

    private String bitmapToBase64(@NonNull Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }
}