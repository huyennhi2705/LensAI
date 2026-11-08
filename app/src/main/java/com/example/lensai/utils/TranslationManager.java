package com.example.lensai.utils;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class TranslationManager {

    private Translator translator;
    private volatile boolean ready = false;
    private volatile boolean isClosed = false;

    public TranslationManager() {
        translator = createTranslator();
    }

    private static Translator createTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build();
        return Translation.getClient(options);
    }

    private synchronized Translator current() {
        if (translator == null) {
            translator = createTranslator();
        }
        return translator;
    }

    /** Tạo lại translator khi cái hiện tại đã bị đóng ngoài ý muốn. */
    private synchronized void recreate() {
        try {
            if (translator != null) translator.close();
        } catch (Exception ignored) {
            // đã đóng rồi
        }
        translator = createTranslator();
        ready = false;
    }

    public void ensureModel(Runnable onReady, Runnable onError) {
        if (isClosed) return;
        if (ready) {
            onReady.run();
            return;
        }
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        try {
            current().downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(v -> {
                        ready = true;
                        if (!isClosed) onReady.run();
                    })
                    .addOnFailureListener(e -> {
                        if (!isClosed) onError.run();
                    });
        } catch (IllegalStateException e) {
            if (!isClosed) onError.run();
        }
    }

    public void translate(String text,
                          OnSuccessListener<String> onSuccess,
                          OnFailureListener onFailure) {
        translateInternal(text, onSuccess, onFailure, true);
    }

    private void translateInternal(String text,
                                   OnSuccessListener<String> onSuccess,
                                   OnFailureListener onFailure,
                                   boolean allowRetry) {
        if (isClosed) return;
        if (text == null || text.isEmpty()) {
            onSuccess.onSuccess("");
            return;
        }
        ensureModel(
                () -> {
                    if (isClosed) return;
                    try {
                        current().translate(text)
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure);
                    } catch (IllegalStateException e) {
                        // Translator bị đóng ngoài ý muốn: tạo lại và thử đúng 1 lần
                        if (allowRetry && !isClosed) {
                            recreate();
                            translateInternal(text, onSuccess, onFailure, false);
                        } else if (!isClosed) {
                            onFailure.onFailure(e);
                        }
                    }
                },
                () -> {
                    if (!isClosed) {
                        onFailure.onFailure(new Exception("Translation model download failed"));
                    }
                }
        );
    }

    public synchronized void close() {
        isClosed = true;
        try {
            if (translator != null) translator.close();
        } catch (Exception ignored) {
            // đã đóng rồi
        }
    }
}