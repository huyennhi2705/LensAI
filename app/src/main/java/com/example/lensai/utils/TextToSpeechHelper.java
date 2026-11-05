package com.example.lensai.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class TextToSpeechHelper {

    private static final String TAG = "TTSHelper";
    private TextToSpeech tts;
    private boolean ready = false;
    private Locale currentLocale = Locale.US;

    public TextToSpeechHelper(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                setLanguage(false); // mặc định tiếng Anh
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
                ready = true;
            } else {
                Log.e(TAG, "TTS init failed");
            }
        });
    }
    public void setLanguage(boolean vietnamese) {
        if (tts == null) return;

        Locale locale = vietnamese ? new Locale("vi", "VN") : Locale.US;
        int result = tts.setLanguage(locale);

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not supported: " + locale + " → fallback to US");
            tts.setLanguage(Locale.US);
            currentLocale = Locale.US;
        } else {
            currentLocale = locale;
        }
    }

    public void speak(String text) {
        if (!ready || text == null || text.trim().isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lensai_utt");
    }

    public void speak(String text, boolean vietnamese) {
        if (tts == null) return;
        Locale locale = vietnamese ? new Locale("vi", "VN") : Locale.US;
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US); // fallback
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
    }


    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
        }
    }

    public boolean isReady() {
        return ready;
    }
}