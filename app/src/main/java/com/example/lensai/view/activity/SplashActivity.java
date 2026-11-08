package com.example.lensai.view.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.MainActivity;
import com.example.lensai.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Chờ 1.5 giây để người dùng kịp nhìn thấy màn hình Splash
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // =========================================================
            // BẮT BUỘC ĐĂNG XUẤT MỖI LẦN MỞ APP
            // =========================================================
            FirebaseAuth.getInstance().signOut();

            // Chuyển thẳng ra màn hình Đăng nhập (AuthActivity)
            Intent intent = new Intent(SplashActivity.this, AuthActivity.class);

            // Dùng cờ này để xóa lịch sử màn hình, không cho phép ấn Back quay lại Splash
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            // Đóng SplashActivity
            finish();

        }, 3000);
    }
}