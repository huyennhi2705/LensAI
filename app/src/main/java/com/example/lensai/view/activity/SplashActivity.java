package com.example.lensai.view.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.MainActivity;
import com.example.lensai.R;
import com.google.android.material.button.MaterialButton;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 3000L; // 3 giây

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = this::goToMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        btnGetStarted.setOnClickListener(v -> {
            // Bấm sớm thì huỷ hẹn giờ và vào app ngay
            handler.removeCallbacks(navigateRunnable);
            goToMain();
        });

        // Tự động vào app sau 3 giây
        handler.postDelayed(navigateRunnable, SPLASH_DELAY_MS);
    }

    private void goToMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tránh leak / navigate khi Activity đã bị huỷ
        handler.removeCallbacks(navigateRunnable);
    }
}