package com.example.lensai.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.lensai.ml.ImageRecognitionEngine;
import com.example.lensai.ml.TFLiteRecognitionEngine;
import com.example.lensai.model.AIResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

public class AIService {

    private TFLiteRecognitionEngine tfliteEngine;
    private ImageRecognitionEngine mlkitEngine; // giữ lại để fallback

    public AIService(Context context) {
        try {
            tfliteEngine = new TFLiteRecognitionEngine(context);
            Log.d("AIService", "TFLite model loaded successfully");
        } catch (Exception e) {
            Log.e("AIService", "Failed to load TFLite model, fallback to ML Kit", e);
            tfliteEngine = null;
            mlkitEngine = new ImageRecognitionEngine();
        }
    }

    public void analyze(Bitmap bitmap,
                        OnSuccessListener<List<AIResult>> onSuccess,
                        OnFailureListener onFailure) {
        if (tfliteEngine != null) {
            tfliteEngine.recognize(bitmap, onSuccess, onFailure);
        } else {
            mlkitEngine.recognize(bitmap, onSuccess, onFailure);
        }
    }

    public void release() {
        if (tfliteEngine != null) tfliteEngine.close();
        if (mlkitEngine != null) mlkitEngine.close();
    }
}