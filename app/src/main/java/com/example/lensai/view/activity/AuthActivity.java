package com.example.lensai.view.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.MainActivity;
import com.example.lensai.R;
import com.example.lensai.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AuthActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword;
    private MaterialButton btnAction;
    private TextView tvTitle, tvToggle;
    private ProgressBar progressBar;
    private ImageView btnTogglePassword;
    private CheckBox cbRememberMe;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private boolean isLoginMode = true;

    // Khai báo SharedPreferences để lưu "Ghi nhớ đăng nhập"
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "LensAIPrefs";
    private static final String KEY_REMEMBER = "remember_me";
    private static final String KEY_EMAIL = "saved_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Khởi tạo Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance("https://lensai-97dc7-default-rtdb.firebaseio.com/").getReference();

        // Khởi tạo SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Ánh xạ UI
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnAction = findViewById(R.id.btnAction);
        tvTitle = findViewById(R.id.tvTitle);
        tvToggle = findViewById(R.id.tvToggle);
        progressBar = findViewById(R.id.progressBar);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);

        // 1. KHÔI PHỤC TRẠNG THÁI GHI NHỚ ĐĂNG NHẬP (Nếu có)
        boolean isRemembered = sharedPreferences.getBoolean(KEY_REMEMBER, false);
        if (isRemembered) {
            cbRememberMe.setChecked(true);
            etEmail.setText(sharedPreferences.getString(KEY_EMAIL, ""));
        }

        // 2. XỬ LÝ ẨN/HIỆN MẬT KHẨU
        btnTogglePassword.setOnClickListener(v -> {
            if (etPassword.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                // Đang ẩn -> Hiện mật khẩu
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_eye_show);
            } else {
                // Đang hiện -> Ẩn mật khẩu
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_eye_hide); // Thay bằng icon mắt nhắm của bạn
            }
            // Đưa con trỏ chuột về cuối dòng chữ
            etPassword.setSelection(etPassword.getText().length());
        });

        updateUIForMode();

        // 3. SỰ KIỆN NÚT ĐĂNG NHẬP / ĐĂNG KÝ
        btnAction.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String fullName = etFullName.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
                return;
            }

            // LƯU TRẠNG THÁI GHI NHỚ TRƯỚC KHI ĐĂNG NHẬP
            if (cbRememberMe.isChecked()) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(KEY_REMEMBER, true);
                editor.putString(KEY_EMAIL, email); // Chỉ lưu email, không lưu password vì lý do bảo mật
                editor.apply();
            } else {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(KEY_REMEMBER, false);
                editor.remove(KEY_EMAIL);
                editor.apply();
            }

            showLoading(true);

            if (isLoginMode) {
                // Đăng nhập
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            showLoading(false);
                            if (task.isSuccessful()) {
                                Toast.makeText(AuthActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(AuthActivity.this, MainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(AuthActivity.this, "Đăng nhập thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            } else {
                // Đăng ký
                if (TextUtils.isEmpty(fullName)) {
                    Toast.makeText(this, "Vui lòng nhập họ tên", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    return;
                }

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                if (firebaseUser != null) {
                                    User user = new User(firebaseUser.getUid(), email, fullName);

                                    mDatabase.child("users").child(firebaseUser.getUid()).setValue(user)
                                            .addOnCompleteListener(dbTask -> {
                                                showLoading(false);
                                                if (dbTask.isSuccessful()) {
                                                    Toast.makeText(AuthActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                                                    startActivity(new Intent(AuthActivity.this, MainActivity.class));
                                                    finish();
                                                } else {
                                                    Toast.makeText(AuthActivity.this, "Lưu thông tin thất bại: " + dbTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                }
                                            });
                                } else {
                                    showLoading(false);
                                }
                            } else {
                                showLoading(false);
                                Toast.makeText(AuthActivity.this, "Đăng ký thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        // Đổi chế độ Đăng nhập / Đăng ký
        tvToggle.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUIForMode();
        });
    }

    private void updateUIForMode() {
        if (isLoginMode) {
            tvTitle.setText("Đăng nhập");
            btnAction.setText("Đăng nhập");
            tvToggle.setText("Chưa có tài khoản? Đăng ký");
            etFullName.setVisibility(View.GONE);
            // Đảm bảo ô mật khẩu luôn ở trạng thái ẩn khi đổi mode
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            btnTogglePassword.setImageResource(R.drawable.ic_eye_hide);
        } else {
            tvTitle.setText("Đăng ký");
            btnAction.setText("Đăng ký");
            tvToggle.setText("Đã có tài khoản? Đăng nhập");
            etFullName.setVisibility(View.VISIBLE);
        }
        etPassword.setText("");
    }

    private void showLoading(boolean loading) {
        if (loading) {
            progressBar.setVisibility(View.VISIBLE);
            btnAction.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnAction.setEnabled(true);
        }
    }
}