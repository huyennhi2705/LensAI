package com.example.lensai.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class TextToSpeechHelper {
    private TextToSpeech tts;
    private boolean ready = false;

    public TextToSpeechHelper(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.US);   // American English
                ready = r != TextToSpeech.LANG_MISSING_DATA
                        && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
            }
        });
    }

    public void speak(String text) {
        if (!ready || text == null || text.isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lensai_utt");
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}