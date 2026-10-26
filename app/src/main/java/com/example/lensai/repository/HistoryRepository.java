package com.example.lensai.repository;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lensai.model.ScanHistory;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class HistoryRepository {

    private static final String TAG = "HistoryRepo";
    public static final String COLLECTION = "scans";
    private static final int MAX_IMAGE_SIZE = 480;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void saveScan(@NonNull ScanHistory history,
                         @Nullable Bitmap image,
                         @Nullable OnSuccessListener<Void> onSuccess,
                         @Nullable OnFailureListener onFailure) {

        history.timestamp = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();

        // Thông tin cũ
        data.put("labelEn", nz(history.labelEn));
        data.put("labelVi", nz(history.labelVi));
        data.put("description", nz(history.description));
        data.put("descriptionEn", nz(history.descriptionEn));
        data.put("descriptionVi", nz(history.descriptionVi));
        data.put("category", history.category != null ? history.category : "other");
        data.put("type", nz(history.type));
        data.put("confidence", history.confidence);
        data.put("timestamp", history.timestamp);

        // ===== MỚI =====
        data.put("objectCount", history.objectCount);
        data.put("objectsListEn", nz(history.objectsListEn));
        data.put("objectsListVi", nz(history.objectsListVi));
        // ===============

        if (image != null && !image.isRecycled()) {
            data.put("imageBase64", bitmapToBase64(image));
        } else {
            data.put("imageBase64", "");
        }

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(docRef -> {
                    history.id = docRef.getId();
                    Log.d(TAG, "Saved scan: " + history.id
                            + " | count=" + history.objectCount
                            + " | objects=" + history.objectsListEn);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Save failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }
    /** Newest first. */
    public Query getHistoryQuery() {
        return db.collection(COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING);
    }

    public void deleteScan(@NonNull String id,
                           @Nullable OnSuccessListener<Void> onSuccess,
                           @Nullable OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(id)
                .delete()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Deleted: " + id);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Delete failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    /** Loads one record by id. */
    public void getScan(@NonNull String id,
                        @NonNull OnSuccessListener<ScanHistory> onSuccess,
                        @Nullable OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    ScanHistory item = doc.toObject(ScanHistory.class);
                    if (item == null) {
                        if (onFailure != null) {
                            onFailure.onFailure(new Exception("Record not found"));
                        }
                        return;
                    }
                    item.id = doc.getId();
                    onSuccess.onSuccess(item);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Load failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
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