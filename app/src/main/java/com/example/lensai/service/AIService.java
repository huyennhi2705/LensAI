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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AIService {

    private static final String TAG = "AIService";
    private static final long GEMINI_TIMEOUT_SEC = 60;

    private YoloDetector yolo;
    private final GeminiRecognitionEngine gemini = new GeminiRecognitionEngine();
    private List<String> labels = new ArrayList<>();

    // 1 luồng điều phối + 2 luồng chạy song song
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    public AIService(Context context) {
        try {
            yolo = new YoloDetector(context);
            labels = yolo.getLabels();
            Log.d(TAG, "YOLO model loaded, " + labels.size() + " lớp");
        } catch (Throwable e) {
            Log.e(TAG, "Không load được model.tflite", e);
            yolo = null;
        }
    }

    public void analyze(Bitmap bitmap,
                        OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {
        executor.execute(() -> {
            final YoloDetector y = yolo;

            Future<List<AIResult>> yoloFuture = executor.submit(new Callable<List<AIResult>>() {
                @Override public List<AIResult> call() throws Exception {
                    if (y == null) throw new IllegalStateException("YOLO chưa load");
                    List<AIResult> out = new ArrayList<>();
                    for (YoloDetector.Detection d : y.detect(bitmap)) {
                        out.add(new AIResult(d.label, d.confidence, "tflite"));
                    }
                    return out;
                }
            });

            Future<List<AIResult>> geminiFuture = executor.submit(new Callable<List<AIResult>>() {
                @Override public List<AIResult> call() throws Exception {
                    if (labels.isEmpty()) throw new IllegalStateException("Chưa có labels.txt");
                    return gemini.recognize(bitmap, labels);
                }
            });

            List<AIResult> yoloRes = null, geminiRes = null;
            Throwable yoloErr = null, geminiErr = null;

            try { yoloRes = yoloFuture.get(); }
            catch (Throwable e) { yoloErr = e; Log.e(TAG, "YOLO lỗi", e); }

            try { geminiRes = geminiFuture.get(GEMINI_TIMEOUT_SEC, TimeUnit.SECONDS); }
            catch (Throwable e) {
                geminiErr = e;
                Log.e(TAG, "Gemini lỗi: " + e.getMessage(), e);
                geminiFuture.cancel(true);
            }

            if (yoloRes == null && geminiRes == null) {
                Throwable cause = geminiErr != null ? geminiErr : yoloErr;
                main.post(() -> onFailure.onFailure(new Exception(
                        cause != null ? String.valueOf(cause.getMessage()) : "Nhận diện thất bại")));
                return;
            }

            List<AIResult> merged = merge(yoloRes, geminiRes);
            main.post(() -> onSuccess.onSuccess(merged));
        });
    }

    private List<AIResult> merge(List<AIResult> yoloRes, List<AIResult> geminiRes) {
        List<AIResult> merged = new ArrayList<>();
        if (geminiRes != null) merged.addAll(geminiRes);

        if (yoloRes != null) {
            for (AIResult y : yoloRes) {
                AIResult same = null;
                for (AIResult g : merged) {
                    if (g.label.equalsIgnoreCase(y.label)) { same = g; break; }
                }
                if (same != null) {
                    same.confidence = Math.max(same.confidence, y.confidence);
                    same.type = "hybrid";
                } else {
                    merged.add(y);
                }
            }
        }
        merged.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        return merged;
    }

    public void release() {
        // đóng trong executor để không đóng interpreter khi đang chạy dở
        executor.execute(() -> { if (yolo != null) yolo.close(); });
        executor.shutdown();
    }
}