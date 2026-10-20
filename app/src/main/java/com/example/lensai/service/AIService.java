package com.example.lensai.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.lensai.ml.ImageRecognitionEngine;
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

    private YoloDetector detector;
    private ImageRecognitionEngine mlkitEngine; // dự phòng
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AIService(Context context) {
        try {
            detector = new YoloDetector(context, "model.tflite", "labels.txt");
            Log.d(TAG, "YOLO model loaded successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Load YOLO lỗi, chuyển sang ML Kit", e);
            detector = null;
            mlkitEngine = new ImageRecognitionEngine();
        }
    }

    public void analyze(Bitmap bitmap,
                        OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {

        if (detector == null) {
            // Fallback ML Kit
            mlkitEngine.recognize(bitmap, onSuccess, onFailure);
            return;
        }

        executor.execute(() -> {
            try {
                List<YoloDetector.Detection> detections = detector.detect(bitmap);
                List<AIResult> results = new ArrayList<>();

                for (YoloDetector.Detection d : detections) {
                    results.add(new AIResult(d.label, d.confidence, "yolo"));
                }

                mainHandler.post(() -> onSuccess.onSuccess(results));
            } catch (Throwable t) {
                Log.e(TAG, "Nhận dạng lỗi", t);
                Exception ex = (t instanceof Exception) ? (Exception) t : new RuntimeException(t);
                mainHandler.post(() -> onFailure.onFailure(ex));
            }
        });
    }

    public void release() {
        executor.execute(() -> {
            if (detector != null) {
                detector.close();
            }
        });
        executor.shutdown();
        if (mlkitEngine != null) {
            mlkitEngine.close();
        }
    }
}