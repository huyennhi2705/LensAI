package com.example.lensai.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Quản lý quyền runtime: CAMERA + RECORD_AUDIO.
 */
public final class PermissionUtils {

    public static final int REQ_CAMERA = 1001;
    public static final int REQ_AUDIO  = 1002;
    public static final int REQ_BOTH   = 1003;

    private PermissionUtils() {}

    // ===== CAMERA =====
    public static boolean hasCameraPermission(Activity a) {
        return ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCameraPermission(Activity a) {
        ActivityCompat.requestPermissions(a,
                new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    // ===== AUDIO =====
    public static boolean hasAudioPermission(Activity a) {
        return ContextCompat.checkSelfPermission(a, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestAudioPermission(Activity a) {
        ActivityCompat.requestPermissions(a,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
    }

    // ===== CẢ HAI =====
    public static boolean hasAllPermissions(Activity a) {
        return hasCameraPermission(a) && hasAudioPermission(a);
    }

    public static void requestAllPermissions(Activity a) {
        ActivityCompat.requestPermissions(a,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                }, REQ_BOTH);
    }
}