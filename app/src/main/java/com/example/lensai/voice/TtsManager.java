package com.example.lensai.voice;
import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

public class TtsManager {

    private static final String TAG = "TtsManager";
    private static TtsManager instance;

    public interface OnInitCallback { void onReady(boolean ok, String message); }
    public interface OnSpeakCallback { void onDone(); void onError(String reason); }

    private TextToSpeech tts;
    private boolean ready = false;
    private boolean initFailed = false;
    private final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
    private OnInitCallback initCallback;
    private OnSpeakCallback speakCallback;

    private TtsManager() {}

    public static synchronized TtsManager get() {
        if (instance == null) instance = new TtsManager();
        return instance;
    }

    /** Gọi 1 lần trong Application.onCreate() hoặc MainActivity.onCreate(). */
    public void init(Context appContext, OnInitCallback cb) {
        this.initCallback = cb;
        if (tts != null) {
            if (ready) { if (cb != null) cb.onReady(true, "already ready"); }
            return;
        }
        tts = new TextToSpeech(appContext.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                initFailed = true;
                Log.e(TAG, "TTS init FAILED, status=" + status);
                if (cb != null) cb.onReady(false, "init failed status=" + status);
                return;
            }
            // Chọn ngôn ngữ theo thứ tự ưu tiên
            int r = tts.setLanguage(new Locale("vi", "VN"));
            Log.d(TAG, "setLanguage(vi-VN) result=" + r);

            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("vi"));
                Log.d(TAG, "setLanguage(vi) result=" + r);
            }
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.US);
                Log.w(TAG, "Fallback sang en-US, result=" + r);
            }

            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                initFailed = true;
                Log.e(TAG, "Không có ngôn ngữ nào khả dụng trên máy!");
                if (cb != null) cb.onReady(false, "no language available – cài Google TTS + gói tiếng Việt");
                return;
            }

            tts.setSpeechRate(1.0f);
            tts.setPitch(1.0f);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { Log.d(TAG, "onStart " + id); }
                @Override public void onDone(String id) {
                    Log.d(TAG, "onDone " + id);
                    if (speakCallback != null) speakCallback.onDone();
                }
                @Override public void onError(String id) {
                    Log.e(TAG, "onError " + id);
                    if (speakCallback != null) speakCallback.onError("utterance error");
                }
            });

            ready = true;
            Log.i(TAG, "TTS ready, engine=" + tts.getDefaultEngine()
                    + ", voice=" + tts.getVoice());
            // chạy các lệnh chờ
            while (!pending.isEmpty()) pending.poll().run();
            if (cb != null) cb.onReady(true, "ready");
        });
    }

    /** Đọc văn bản. Nếu chưa ready, lệnh sẽ chờ init xong. */
    public void speak(String text, OnSpeakCallback cb) {
        if (text == null || text.trim().isEmpty()) return;
        this.speakCallback = cb;

        if (initFailed) {
            Log.e(TAG, "speak() nhưng initFailed=true");
            if (cb != null) cb.onError("tts init failed");
            return;
        }
        if (!ready) {
            Log.d(TAG, "speak() chờ init: " + text);
            pending.add(() -> speak(text, cb));
            return;
        }
        String id = UUID.randomUUID().toString();
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        int res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id);
        Log.d(TAG, "speak QUEUE_FLUSH id=" + id + " result=" + res + " text=" + text);
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public boolean isReady() { return ready; }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
        instance = null;
    }
}