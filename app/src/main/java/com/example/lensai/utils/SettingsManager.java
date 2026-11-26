package com.example.lensai.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "LensAISettings";
    public static final String KEY_AUTO_SPEAK = "auto_speak";
    public static final String KEY_SPEECH_RATE = "speech_rate";
    public static final String KEY_AUTO_SAVE = "auto_save";
    public static final String KEY_HIGH_ACCURACY = "high_accuracy";
    public static final String KEY_SYNC_FIREBASE = "sync_firebase";

    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoSpeak() { return prefs.getBoolean(KEY_AUTO_SPEAK, true); }
    public void setAutoSpeak(boolean value) { prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply(); }

    public float getSpeechRate() { return prefs.getFloat(KEY_SPEECH_RATE, 1.0f); }
    public void setSpeechRate(float value) { prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply(); }

    public boolean isAutoSave() { return prefs.getBoolean(KEY_AUTO_SAVE, true); }
    public void setAutoSave(boolean value) { prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply(); }

    public boolean isHighAccuracy() { return prefs.getBoolean(KEY_HIGH_ACCURACY, false); }
    public void setHighAccuracy(boolean value) { prefs.edit().putBoolean(KEY_HIGH_ACCURACY, value).apply(); }

    public boolean isSyncFirebase() { return prefs.getBoolean(KEY_SYNC_FIREBASE, true); }
    public void setSyncFirebase(boolean value) { prefs.edit().putBoolean(KEY_SYNC_FIREBASE, value).apply(); }
}