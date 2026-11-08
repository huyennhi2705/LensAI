package com.example.lensai.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
    private final GeminiRecognitionEngine gemini = new GeminiRecognitionEngine();
    private List<String> labels = new ArrayList<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    public AIService(Context context) {
        try {
            yolo = new YoloDetector(context);
            labels = yolo.getLabels();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load model.tflite", e);
            yolo = null;
        }
    }

    public void analyze(Bitmap bitmap,
                        OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {
        executor.execute(() -> {
            List<AIResult> geminiRes = null;
            List<AIResult> yoloRes = null;
            Exception geminiError = null;

            // 1. Chạy Gemini (Ưu tiên)
            try {
                geminiRes = gemini.recognize(bitmap, labels);
            } catch (Exception e) {
                geminiError = e;
                Log.e(TAG, "Gemini error: " + e.getMessage());
            }

            // 2. Nếu Gemini lỗi hoặc không tìm thấy gì, chạy YOLO dự phòng
            if ((geminiRes == null || geminiRes.isEmpty()) && yolo != null) {
                yoloRes = new ArrayList<>();
                for (YoloDetector.Detection d : yolo.detect(bitmap)) {
                    yoloRes.add(new AIResult(d.label, d.confidence, "tflite"));
                }
            }

            final List<AIResult> finalGeminiRes = geminiRes;
            final List<AIResult> finalYoloRes = yoloRes;
            final Exception finalGeminiError = geminiError;

            main.post(() -> {
                if (finalGeminiRes != null && !finalGeminiRes.isEmpty()) {
                    onSuccess.onSuccess(finalGeminiRes);
                } else if (finalYoloRes != null && !finalYoloRes.isEmpty()) {
                    onSuccess.onSuccess(finalYoloRes);
                } else {
                    if (finalGeminiError != null) {
                        onFailure.onFailure(finalGeminiError);
                    } else {
                        onSuccess.onSuccess(new ArrayList<>());
                    }
                }
            });
        });
    }

    // Hàm chat tính năng hỏi đáp (Đã thêm lại)
    public void chat(Bitmap bitmap, String prompt,
                     OnSuccessListener<String> onSuccess,
                     OnFailureListener onFailure) {
        executor.execute(() -> {
            try {
                String response = gemini.chat(bitmap, prompt);
                main.post(() -> onSuccess.onSuccess(response));
            } catch (Exception e) {
                main.post(() -> onFailure.onFailure(e));
            }
        });
    }

    public void release() {
        executor.execute(() -> { if (yolo != null) yolo.close(); });
        executor.shutdown();
    }
}