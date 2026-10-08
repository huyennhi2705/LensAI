package com.example.lensai.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class TextToSpeechHelper {
    private TextToSpeech tts;
    private boolean ready = false;

    public TextToSpeechHelper(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f);
                ready = true;
            }
        });
    }

    public void speak(String text) {
        if (ready && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LensAI");
        }
    }

    public void setSpeechRate(float rate) {
        if (tts != null) tts.setSpeechRate(rate);
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}