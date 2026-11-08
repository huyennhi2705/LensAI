package com.example.lensai.repository;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lensai.model.ScanHistory;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lưu lịch sử quét vào Firebase Realtime Database:
 *
 *   scan_history / {user_id} / {scan_id} / { user_id, image_path, created_at, ...thông tin đồ vật }
 */
public class HistoryRepository {

    private static final String TAG = "HistoryRepo";

    /** Giữ tên COLLECTION để các chỗ khác đang tham chiếu không bị lỗi biên dịch. */
    public static final String COLLECTION = "scan_history";

    // URL cố định để không phụ thuộc vào firebase_url trong google-services.json
    private static final String DB_URL = "https://lensai-97dc7-default-rtdb.firebaseio.com";

    private static final int MAX_IMAGE_SIZE = 480;
    private static final int MAX_ITEMS = 100;

    private final DatabaseReference root =
            FirebaseDatabase.getInstance(DB_URL).getReference(COLLECTION);
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface UidCallback {
        void onUid(@NonNull String uid);
    }

    /**
     * Lấy user_id. Nếu chưa đăng nhập thì tự đăng nhập ẩn danh
     * (cần bật Anonymous trong Firebase Authentication).
     */
    public void withUid(@NonNull UidCallback onUid, @Nullable OnFailureListener onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            onUid.onUid(user.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser u = result.getUser();
                    if (u != null) {
                        onUid.onUid(u.getUid());
                    } else if (onFailure != null) {
                        onFailure.onFailure(new Exception("Sign-in returned no user"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous sign-in failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    // ---------------------------------------------------------------------
    // Lưu
    // ---------------------------------------------------------------------
    public void saveScan(@NonNull ScanHistory history,
                         @Nullable Bitmap image,
                         @Nullable OnSuccessListener<Void> onSuccess,
                         @Nullable OnFailureListener onFailure) {

        history.timestamp = System.currentTimeMillis();

        // Mã hóa ảnh ngay bây giờ, tránh việc bitmap bị recycle trong lúc chờ đăng nhập
        final String imageBase64 = (image != null && !image.isRecycled())
                ? bitmapToBase64(image) : "";

        withUid(uid -> {
            DatabaseReference ref = root.child(uid).push();
            String scanId = ref.getKey();
            if (scanId == null) {
                if (onFailure != null) onFailure.onFailure(new Exception("Cannot create scan id"));
                return;
            }

            Map<String, Object> data = new HashMap<>();
            // --- Các trường theo bảng scan_history ---
            data.put("scan_id", scanId);
            data.put("user_id", uid);
            data.put("image_path", COLLECTION + "/" + uid + "/" + scanId + "/image_base64");
            data.put("created_at", history.timestamp);

            // --- Thông tin đồ vật ---
            data.put("label_en", nz(history.labelEn));
            data.put("label_vi", nz(history.labelVi));
            data.put("description", nz(history.description));
            data.put("description_en", nz(history.descriptionEn));
            data.put("description_vi", nz(history.descriptionVi));
            data.put("category", history.category != null ? history.category : "other");
            data.put("type", nz(history.type));
            data.put("confidence", history.confidence);
            data.put("object_count", history.objectCount);
            data.put("objects_list_en", nz(history.objectsListEn));
            data.put("objects_list_vi", nz(history.objectsListVi));
            data.put("source", history.source != null ? history.source : "gemini");
            data.put("image_base64", imageBase64);

            ref.setValue(data)
                    .addOnSuccessListener(unused -> {
                        history.id = scanId;
                        history.userId = uid;
                        Log.d(TAG, "Saved scan: " + scanId
                                + " | count=" + history.objectCount
                                + " | objects=" + history.objectsListEn);
                        if (onSuccess != null) onSuccess.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Save failed", e);
                        if (onFailure != null) onFailure.onFailure(e);
                    });
        }, onFailure);
    }

    // ---------------------------------------------------------------------
    // Đọc danh sách (HistoryController dùng)
    // ---------------------------------------------------------------------

    /** Truy vấn theo created_at, tối đa MAX_ITEMS bản ghi mới nhất (kết quả tăng dần). */
    public Query historyQuery(@NonNull String uid) {
        return root.child(uid).orderByChild("created_at").limitToLast(MAX_ITEMS);
    }

    // ---------------------------------------------------------------------
    // Xóa
    // ---------------------------------------------------------------------
    public void deleteScan(@NonNull String id,
                           @Nullable OnSuccessListener<Void> onSuccess,
                           @Nullable OnFailureListener onFailure) {
        withUid(uid -> root.child(uid).child(id).removeValue()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Deleted: " + id);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Delete failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                }), onFailure);
    }

    // ---------------------------------------------------------------------
    // Đọc một bản ghi
    // ---------------------------------------------------------------------
    public void getScan(@NonNull String id,
                        @NonNull OnSuccessListener<ScanHistory> onSuccess,
                        @Nullable OnFailureListener onFailure) {
        withUid(uid -> root.child(uid).child(id)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ScanHistory item = null;
                        try {
                            item = snapshot.getValue(ScanHistory.class);
                        } catch (Exception e) {
                            Log.e(TAG, "Parse failed", e);
                        }
                        if (item == null) {
                            if (onFailure != null) {
                                onFailure.onFailure(new Exception("Record not found"));
                            }
                            return;
                        }
                        item.id = snapshot.getKey();
                        onSuccess.onSuccess(item);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Load failed", error.toException());
                        if (onFailure != null) onFailure.onFailure(error.toException());
                    }
                }), onFailure);
    }

    // ---------- Helpers ----------

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private String bitmapToBase64(Bitmap source) {
        Bitmap scaled = source;
        int w = source.getWidth();
        int h = source.getHeight();
        float ratio = Math.min((float) MAX_IMAGE_SIZE / w, (float) MAX_IMAGE_SIZE / h);
        if (ratio < 1f) {
            scaled = Bitmap.createScaledBitmap(source,
                    Math.round(w * ratio), Math.round(h * ratio), true);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);

        if (scaled != source) {
            scaled.recycle();
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }
}