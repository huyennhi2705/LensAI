package com.example.lensai.utils;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Đọc văn bản thành tiếng (Anh / Việt).
 * API: speak(text), speak(text, vietnamese), speak(text, vietnamese, onDone),
 *      stop(), isReady(), shutdown().
 *
 * onDone được gọi (trên main thread) khi đọc xong đoạn cuối, hoặc khi không đọc được
 * (lỗi engine, thiếu giọng) để logic phía sau (vd. lưu lịch sử) vẫn chạy.
 */
public class TextToSpeechHelper implements TextToSpeech.OnInitListener {

    private static final String TAG = "TTSHelper";
    private static final String GOOGLE_TTS = "com.google.android.tts";
    private static final int MAX_CHUNK = 3500; // giới hạn của Android là 4000 ký tự/lần

    private static final Locale LOCALE_VI = new Locale("vi", "VN");

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private boolean triedGoogleEngine = false;

    // Yêu cầu đến sớm khi engine chưa khởi tạo xong -> đọc ngay khi sẵn sàng
    private String pendingText;
    private boolean pendingVi;
    private Runnable pendingDone;

    // Callback khi đọc xong đoạn cuối của lần speak hiện tại
    private volatile Runnable doneCallback;
    private volatile String lastUtteranceId;

    private boolean warnedViMissing = false;
    private boolean warnedEnMissing = false;

    public TextToSpeechHelper(Context context) {
        appContext = context.getApplicationContext();
        tts = new TextToSpeech(appContext, this); // engine mặc định của máy
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            Log.e(TAG, "TextToSpeech init failed, status=" + status);

            // Engine mặc định lỗi -> thử chỉ định thẳng Google TTS một lần
            if (!triedGoogleEngine) {
                triedGoogleEngine = true;
                try {
                    if (tts != null) tts.shutdown();
                } catch (Exception ignored) {
                    // đã đóng
                }
                tts = new TextToSpeech(appContext, this, GOOGLE_TTS);
                return;
            }

            failed = true;
            toast("Máy chưa có bộ đọc giọng nói. Hãy cài \"Dịch vụ giọng nói của Google\", "
                    + "sau đó mở lại app.");
            openGoogleTtsInPlayStore();
            if (pendingDone != null) {
                main.post(pendingDone);
                pendingDone = null;
            }
            pendingText = null;
            return;
        }

        try {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
        } catch (Exception e) {
            Log.w(TAG, "setAudioAttributes failed", e);
        }

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onDone(String utteranceId) {
                finishIfLast(utteranceId);
            }

            @Override @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
                finishIfLast(utteranceId);
            }
        });

        ready = true;
        Log.d(TAG, "TextToSpeech ready, engine=" + tts.getDefaultEngine());

        if (pendingText != null) {
            String t = pendingText;
            boolean vi = pendingVi;
            Runnable cb = pendingDone;
            pendingText = null;
            pendingDone = null;
            speak(t, vi, cb);
        }
    }

    /** Đọc tiếng Anh. */
    public void speak(String text) {
        speak(text, false, null);
    }

    /** vietnamese = true -> đọc tiếng Việt, false -> tiếng Anh. */
    public void speak(String text, boolean vietnamese) {
        speak(text, vietnamese, null);
    }

    /**
     * Đọc theo ngôn ngữ chọn, gọi onDone khi đọc xong (hoặc khi không thể đọc).
     * Gọi speak mới sẽ ngắt lần đọc đang chạy.
     */
    public void speak(String text, boolean vietnamese, Runnable onDone) {
        if (text == null || text.trim().isEmpty()) {
            runNow(onDone);
            return;
        }

        if (failed) {
            toast("Thiết bị chưa có bộ đọc giọng nói.");
            runNow(onDone);
            return;
        }
        if (!ready || tts == null) {
            // Engine chưa sẵn sàng: nhớ lại và đọc sau khi onInit chạy
            pendingText = text;
            pendingVi = vietnamese;
            pendingDone = onDone;
            return;
        }

        if (!applyLanguage(vietnamese)) {
            runNow(onDone);
            return;
        }

        List<String> chunks = split(text.trim());
        String base = "lensai_" + System.nanoTime() + "_";
        lastUtteranceId = base + (chunks.size() - 1);
        doneCallback = onDone;

        for (int i = 0; i < chunks.size(); i++) {
            int mode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            String id = base + i;
            int r = tts.speak(chunks.get(i), mode, null, id);
            if (r != TextToSpeech.SUCCESS) {
                Log.e(TAG, "speak() returned " + r);
                finishIfLast(id);
            }
        }
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public boolean isReady() {
        return ready;
    }

    public void shutdown() {
        pendingText = null;
        pendingDone = null;
        doneCallback = null;
        ready = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
                // đã đóng
            }
            tts = null;
        }
    }

    // ---------------------------------------------------------------------

    private void finishIfLast(String utteranceId) {
        if (utteranceId != null && utteranceId.equals(lastUtteranceId)) {
            Runnable cb = doneCallback;
            doneCallback = null;
            runNow(cb);
        }
    }

    private void runNow(Runnable r) {
        if (r != null) main.post(r);
    }

    /** Đặt ngôn ngữ; nếu thiếu giọng đọc thì báo và mở trang tải dữ liệu giọng. */
    private boolean applyLanguage(boolean vietnamese) {
        Locale target = vietnamese ? LOCALE_VI : Locale.US;
        int r = tts.setLanguage(target);

        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not available: " + target + " (code " + r + ")");

            if (vietnamese && !warnedViMissing) {
                warnedViMissing = true;
                toast("Máy chưa có giọng tiếng Việt. Hãy cài dữ liệu giọng nói tiếng Việt.");
                openInstallVoiceData();
            } else if (!vietnamese && !warnedEnMissing) {
                warnedEnMissing = true;
                toast("Máy chưa có giọng tiếng Anh. Hãy cài dữ liệu giọng nói tiếng Anh.");
                openInstallVoiceData();
            }
            return false;
        }
        return true;
    }

    /** Mở trang cài "Speech Recognition & Synthesis from Google" trên CH Play. */
    private void openGoogleTtsInPlayStore() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=" + GOOGLE_TTS));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(i);
        } catch (Exception e) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://play.google.com/store/apps/details?id=" + GOOGLE_TTS));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(web);
            } catch (Exception e2) {
                Log.w(TAG, "Cannot open Play Store", e2);
            }
        }
    }

    private void openInstallVoiceData() {
        try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "Cannot open install-voice-data screen", e);
        }
    }


    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        String rest = text;
        while (rest.length() > MAX_CHUNK) {
            int cut = rest.lastIndexOf(". ", MAX_CHUNK);
            if (cut < MAX_CHUNK / 2) cut = rest.lastIndexOf(' ', MAX_CHUNK);
            if (cut <= 0) cut = MAX_CHUNK;
            out.add(rest.substring(0, cut + 1).trim());
            rest = rest.substring(cut + 1).trim();
        }
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    private void toast(String msg) {
        main.post(() -> Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show());
    }
}