package com.example.lensai.repository;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lensai.model.ChatMessage;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryRepository {

    private static final String TAG = "HistoryRepo";
    public static final String COLLECTION = "scan_history";
    private static final String DB_URL = "https://lensai-97dc7-default-rtdb.firebaseio.com";
    private static final int MAX_IMAGE_SIZE = 480;
    private static final int MAX_ITEMS = 100;
    private final DatabaseReference root = FirebaseDatabase.getInstance(DB_URL).getReference(COLLECTION);
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    public interface UidCallback {
        void onUid(@NonNull String uid);
    }

    public void withUid(@NonNull UidCallback onUid, @Nullable OnFailureListener onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            onUid.onUid(user.getUid());
        } else {
            if (onFailure != null) {
                onFailure.onFailure(new Exception("Người dùng chưa đăng nhập. Vui lòng đăng nhập để xem lịch sử."));
            }
        }
    }
    public void saveScan(@NonNull ScanHistory history,
                         @Nullable Bitmap image,
                         @Nullable OnSuccessListener<Void> onSuccess,
                         @Nullable OnFailureListener onFailure) {
        history.timestamp = System.currentTimeMillis();
        final String imageBase64 = (image != null && !image.isRecycled()) ? bitmapToBase64(image) : "";
        withUid(uid -> {
            DatabaseReference ref = root.child(uid).push();
            String scanId = ref.getKey();
            if (scanId == null) {
                if (onFailure != null) onFailure.onFailure(new Exception("Cannot create scan id"));
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("scan_id", scanId);
            data.put("user_id", uid);
            data.put("image_path", COLLECTION + "/" + uid + "/" + scanId + "/image_base64");
            data.put("created_at", history.timestamp);
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
    public Query historyQuery(@NonNull String uid) {
        return root.child(uid).orderByChild("created_at").limitToLast(MAX_ITEMS);
    }
    public void deleteScan(@NonNull String id,
                           @Nullable OnSuccessListener<Void> onSuccess,
                           @Nullable OnFailureListener onFailure) {
        withUid(uid -> root.child(uid).child(id).removeValue().addOnSuccessListener(unused -> {
                    Log.d(TAG, "Deleted: " + id);
                    if (onSuccess != null) onSuccess.onSuccess(null);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Delete failed", e);
                    if (onFailure != null) onFailure.onFailure(e);
                }), onFailure);
    }
    public void getScan(@NonNull String id,
                        @NonNull OnSuccessListener<ScanHistory> onSuccess,
                        @Nullable OnFailureListener onFailure) {
        withUid(uid -> root.child(uid).child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            if (onFailure != null) onFailure.onFailure(new Exception("Record not found"));
                            return;
                        }
                        ScanHistory item = parseSnapshot(snapshot);
                        if (item != null) {
                            onSuccess.onSuccess(item);
                        } else {
                            if (onFailure != null) onFailure.onFailure(new Exception("Parse failed"));
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Load failed", error.toException());
                        if (onFailure != null) onFailure.onFailure(error.toException());
                    }
                }), onFailure);
    }

    public static  ScanHistory parseSnapshot(DataSnapshot snapshot) {
        try {
            ScanHistory item = new ScanHistory();
            item.id = snapshot.getKey();
            item.labelEn = str(snapshot, "label_en", str(snapshot, "labelEn", str(snapshot, "label", null)));
            item.labelVi = str(snapshot, "label_vi", str(snapshot, "labelVi", null));
            item.description = str(snapshot, "description", null);
            item.descriptionEn = str(snapshot, "description_en", str(snapshot, "descriptionEn", null));
            item.descriptionVi = str(snapshot, "description_vi", str(snapshot, "descriptionVi", null));
            item.category = str(snapshot, "category", "other");
            item.imageBase64 = str(snapshot, "image_base64", str(snapshot, "imageBase64", null));
            item.imagePath = str(snapshot, "image_path", str(snapshot, "imagePath", str(snapshot, "imageUrl", null)));
            item.objectsListEn = str(snapshot, "objects_list_en", str(snapshot, "objectsListEn", null));
            item.objectsListVi = str(snapshot, "objects_list_vi", str(snapshot, "objectsListVi", null));
            Long t = snapshot.child("created_at").getValue(Long.class);
            if (t == null) t = snapshot.child("timestamp").getValue(Long.class);
            item.timestamp = t != null ? t : 0L;
            Long cnt = snapshot.child("object_count").getValue(Long.class);
            if (cnt == null) cnt = snapshot.child("objectCount").getValue(Long.class);
            item.objectCount = cnt != null ? cnt.intValue() : 0;
            Double conf = snapshot.child("confidence").getValue(Double.class);
            item.confidence = conf != null ? conf.floatValue() : 0f;
            List<ChatMessage> chats = new ArrayList<>();
            for (DataSnapshot c : snapshot.child("chatHistory").getChildren()) {
                ChatMessage m = new ChatMessage();
                m.question = str(c, "question", "");
                m.answerEn = str(c, "answerEn", "");
                m.answerVi = str(c, "answerVi", "");
                Long ts = c.child("timestamp").getValue(Long.class);
                m.timestamp = ts != null ? ts : 0L;
                chats.add(m);
            }
            chats.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
            item.chatHistory = chats;
            item.inputType = str(snapshot, "input_type", null);
            Long dm = snapshot.child("detect_ms").getValue(Long.class);
            item.detectMs = dm != null ? dm : 0L;
            Long sp = snapshot.child("speak_count").getValue(Long.class);
            item.speakCount = sp != null ? sp : 0L;
            Long cc = snapshot.child("chat_count").getValue(Long.class);
            item.chatCount = cc != null ? cc : 0L;
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Parse failed", e);
            return null;
        }
    }
    private static String str(DataSnapshot s, String key, String def) {
        Object v = s.child(key).getValue();
        return v != null ? String.valueOf(v) : def;
    }
    private static String nz(String s) { return s != null ? s : "";}
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