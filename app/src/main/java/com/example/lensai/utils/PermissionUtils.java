package com.example.lensai.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionUtils {
    public static final int REQ_CAMERA = 1001;

    public static boolean hasCamera(Activity a) {
        return ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCamera(Activity a) {
        ActivityCompat.requestPermissions(a,
                new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }
}