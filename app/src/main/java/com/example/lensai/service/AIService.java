package com.example.lensai.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.lensai.BuildConfig;
import com.example.lensai.ml.GeminiRecognitionEngine;
import com.example.lensai.ml.YoloDetector;
import com.example.lensai.model.AIResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIService {

    private static final String TAG = "AIService";

    private YoloDetector yolo;
    private final GeminiRecognitionEngine gemini =
            new GeminiRecognitionEngine(BuildConfig.GEMINI_API_KEY);

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    public AIService(Context context) {
        try {
            yolo = new YoloDetector(context);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load model.tflite", e);
            yolo = null;
        }
    }

    /** Nhận diện vật thể bằng YOLO. */
    public void analyze(Bitmap bitmap,
                        OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {
        if (yolo == null) {
            onFailure.onFailure(new IllegalStateException("YOLO model chưa được tải"));
            return;
        }
        executor.execute(() -> {
            try {
                List<AIResult> results = new ArrayList<>();
                for (YoloDetector.Detection d : yolo.detect(bitmap)) {
                    results.add(new AIResult(d.label, d.confidence, "tflite"));
                }
                main.post(() -> onSuccess.onSuccess(results));
            } catch (Exception e) {
                Log.e(TAG, "YOLO error", e);
                main.post(() -> onFailure.onFailure(e));
            }
        });
    }

    /** Hỏi đáp với Gemini (chỉ dùng văn bản, không gửi ảnh). */
    public void chat(String prompt,
                     OnSuccessListener<String> onSuccess,
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
        executor.execute(() -> { if (yolo != null) yolo.close(); });
        executor.shutdown();
    }
}