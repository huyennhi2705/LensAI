package com.example.lensai.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.lensai.BuildConfig;
import com.example.lensai.ml.GeminiRecognitionEngine;
import com.example.lensai.ml.MaterialClassifier;
import com.example.lensai.ml.YoloDetector;
import com.example.lensai.model.AIResult;
import com.example.lensai.utils.ObjectDescriptionHelper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIService {
    private static final String TAG = "AIService";
    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";
    private static final float CONF_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final long SPEAK_COOLDOWN_MS = 2000; // 2 giây

    private YoloDetector yolo;
    private MaterialClassifier materialClassifier;
    private final GeminiRecognitionEngine gemini = new GeminiRecognitionEngine(BuildConfig.GEMINI_API_KEY);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Cooldown: lưu thời điểm đọc cuối cùng của từng nhãn
    private final Map<String, Long> lastSpokenTime = new HashMap<>();

    public AIService(Context context) {
        try {
            yolo = new YoloDetector(context, MODEL_PATH, LABEL_PATH, CONF_THRESHOLD, IOU_THRESHOLD);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load model.tflite", e);
            yolo = null;
        }
        try {
            materialClassifier = new MaterialClassifier(context);
            ObjectDescriptionHelper.initMaterialClassifier(materialClassifier);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load MaterialClassifier", e);
            materialClassifier = null;
        }
    }

    public void analyze(Bitmap bitmap, OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {
        if (yolo == null) {
            onFailure.onFailure(new IllegalStateException("YOLO model chưa được tải"));
            return;
        }

        executor.execute(() -> {
            try {
                List<AIResult> results = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (YoloDetector.Detection d : yolo.detect(bitmap)) {
                    String labelEn = d.label;
                    String labelVi = TranslationService.toVietnamese(labelEn);
                    AIResult r = new AIResult(labelEn, d.confidence, "tflite");
                    r.labelVi = labelVi; // thêm trường (xem ghi chú bên dưới)
                    results.add(r);

                    // Cooldown: chỉ đánh dấu là "có thể đọc" nếu đã qua 2 giây
                    Long last = lastSpokenTime.get(labelEn);
                    if (last == null || now - last > SPEAK_COOLDOWN_MS) {
                        r.speakable = true;
                        lastSpokenTime.put(labelEn, now);
                    } else {
                        r.speakable = false;
                    }
                }
                main.post(() -> onSuccess.onSuccess(results));
            } catch (Throwable e) {
                Log.e(TAG, "YOLO error", e);
                Exception ex = (e instanceof Exception) ? (Exception) e : new Exception(e);
                main.post(() -> onFailure.onFailure(ex));
            }
        });
    }

    public void chat(String prompt, OnSuccessListener<String> onSuccess,
                     OnFailureListener onFailure) {
        String fullPrompt = prompt + "\nReply ONLY with JSON: {\"answer\":\"...\"}";
        gemini.generateJson(fullPrompt)
                .thenAccept(json -> {
                    String answer = json.optString("answer", "");
                    main.post(() -> onSuccess.onSuccess(answer));
                })
                .exceptionally(t -> {
                    Exception e = (t instanceof Exception) ? (Exception) t : new Exception(t);
                    main.post(() -> onFailure.onFailure(e));
                    return null;
                });
    }

    public void release() {
        executor.execute(() -> {
            if (yolo != null) { yolo.close(); yolo = null; }
            if (materialClassifier != null) { materialClassifier.close(); materialClassifier = null; }
        });
        executor.shutdown();
    }
}