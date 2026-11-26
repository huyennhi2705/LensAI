package com.example.lensai.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class SpeechRecognitionHelper {
    private static final String TAG = "SpeechRecognitionHelper";

    public interface Callback {
        void onReadyForSpeech();
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(int errorCode);
        void onEndOfSpeech();
    }

    private final Context ctx;
    private SpeechRecognizer recognizer;
    private Callback callback;
    private boolean listening = false;

    public SpeechRecognitionHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(ctx);
    }

    public boolean isListening() { return listening; }

    public void setCallback(Callback cb) { this.callback = cb; }

    public void startListening() {
        if (!isAvailable()) {
            Log.e(TAG, "SpeechRecognizer không khả dụng");
            if (callback != null) callback.onError(SpeechRecognizer.ERROR_CLIENT);
            return;
        }

        if (recognizer == null) {
            // Chỉ định rõ ràng sử dụng dịch vụ của Google nếu máy là OPPO
            ComponentName googleComponent = new ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx, googleComponent);
            } catch (Exception e) {
                Log.w(TAG, "Không tìm thấy Google engine, dùng mặc định");
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            }
            recognizer.setRecognitionListener(listener);
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // ÉP SỬ DỤNG OFFLINE (Cứu cánh cho các máy bị lỗi chặn quyền)
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        listening = true;
        recognizer.startListening(intent);
        Log.d(TAG, "startListening() lang=vi-VN, preferOffline=true");
    }

    public void stopListening() {
        if (recognizer != null && listening) {
            recognizer.stopListening();
            listening = false;
        }
    }

    public void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        listening = false;
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
            if (callback != null) callback.onReadyForSpeech();
        }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");
            listening = false;
            if (callback != null) callback.onEndOfSpeech();
        }
        @Override public void onError(int error) {
            Log.e(TAG, "onError: " + error + " (" + errorToString(error) + ")");
            listening = false;
            if (callback != null) callback.onError(error);
        }
        @Override public void onResults(Bundle results) {
            ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (list != null && !list.isEmpty()) ? list.get(0) : null;
            Log.d(TAG, "onResults: " + text);
            if (callback != null && text != null) callback.onFinalResult(text);
        }
        @Override public void onPartialResults(Bundle partialResults) {
            ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (list != null && !list.isEmpty()) ? list.get(0) : null;
            if (callback != null && text != null) callback.onPartialResult(text);
        }
        @Override public void onEvent(int eventType, Bundle params) { }
    };

    public static String errorToString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Lỗi ghi âm";
            case SpeechRecognizer.ERROR_CLIENT: return "Lỗi ứng dụng";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Chưa cấp quyền Micrô (Lỗi từ Google Services)";
            case SpeechRecognizer.ERROR_NETWORK: return "Lỗi mạng";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Hết thời gian kết nối mạng";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Không nhận diện được giọng nói";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Dịch vụ nhận dạng đang bận";
            case SpeechRecognizer.ERROR_SERVER: return "Lỗi máy chủ nhận dạng";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Không nghe thấy giọng nói";
            case 12: return "Tiếng Việt chưa được hỗ trợ";
            case 13: return "Chưa tải gói tiếng Việt cho nhận dạng";
            default: return "Lỗi không xác định (" + error + ")";
        }
    }
}