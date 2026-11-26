package com.example.lensai.utils;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Wrapper TTS có sẵn stop() / isSpeaking() để tránh chiếm micro khi dùng SpeechRecognizer.
 */
public class TextToSpeechHelper implements TextToSpeech.OnInitListener {

    private static final String TAG = "TextToSpeechHelper";
    public static final String UTTERANCE_ID = "lensai_tts";

    public interface OnTtsReadyListener { void onReady(); }
    public interface OnTtsDoneListener  { void onDone(); }

    private TextToSpeech tts;
    private boolean ready = false;
    private OnTtsReadyListener readyListener;
    private OnTtsDoneListener doneListener;

    public TextToSpeechHelper(Context ctx) {
        tts = new TextToSpeech(ctx.getApplicationContext(), this);
    }

    public void setOnTtsReadyListener(OnTtsReadyListener l) { this.readyListener = l; }
    public void setOnTtsDoneListener(OnTtsDoneListener l)   { this.doneListener = l; }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("vi", "VN"));
            if (r == TextToSpeech.LANG_MISSING_DATA
                    || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Tiếng Việt không hỗ trợ, fallback EN");
                tts.setLanguage(Locale.US);
            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) {
                    if (doneListener != null) doneListener.onDone();
                }
                @Override public void onError(String utteranceId) {
                    if (doneListener != null) doneListener.onDone();
                }
            });
            ready = true;
            if (readyListener != null) readyListener.onReady();
            Log.d(TAG, "TTS ready");
        } else {
            Log.e(TAG, "TTS init failed: " + status);
        }
    }

    public boolean isReady() { return ready; }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void speak(String text) {
        if (!ready || tts == null) {
            Log.w(TAG, "TTS chưa ready, bỏ qua: " + text);
            return;
        }
        // QUEUE_FLUSH: cắt câu cũ, luôn nói câu mới
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, (Bundle) null, UTTERANCE_ID);
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
        }
    }
}