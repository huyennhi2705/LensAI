package com.example.lensai.view.activity;
// AuthActivity.java
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lensai.MainActivity;
import com.example.lensai.R;
import com.example.lensai.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AuthActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword;
    private Button btnAction;
    private TextView tvTitle, tvToggle;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private boolean isLoginMode = true; // Trạng thái hiện tại: true là đăng nhập, false là đăng ký

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Khởi tạo Firebase Auth và Database
        mAuth = FirebaseAuth.getInstance();
        // Chỉ định URL cơ sở dữ liệu của bạn (thực tế SDK thường tự động nhận diện từ google-services.json, nhưng chỉ định rõ ràng sẽ an toàn hơn)
        mDatabase = FirebaseDatabase.getInstance("https://lensai-97dc7-default-rtdb.firebaseio.com/").getReference();

        // Ánh xạ các thành phần giao diện
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnAction = findViewById(R.id.btnAction);
        tvTitle = findViewById(R.id.tvTitle);
        tvToggle = findViewById(R.id.tvToggle);
        progressBar = findViewById(R.id.progressBar);

        // Thiết lập trạng thái ban đầu là đăng nhập
        updateUIForMode();

        // Nhấn nút chính (đăng nhập/đăng ký)
        btnAction.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String fullName = etFullName.getText().toString().trim();

            // Xác thực biểu mẫu đơn giản
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
                return;
            }

            showLoading(true);

            if (isLoginMode) {
                // Logic đăng nhập
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            showLoading(false);
                            if (task.isSuccessful()) {
                                Toast.makeText(AuthActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                                // Chuyển đến MainActivity và kết thúc Activity hiện tại
                                startActivity(new android.content.Intent(AuthActivity.this, MainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(AuthActivity.this, "Đăng nhập thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            } else {
                // Logic đăng ký
                if (TextUtils.isEmpty(fullName)) {
                    Toast.makeText(this, "Vui lòng nhập họ tên", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    return;
                }

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                // Đăng ký thành công, lấy người dùng hiện tại
                                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                if (firebaseUser != null) {
                                    // Ghi thông tin bổ sung vào Realtime Database
                                    User user = new User(firebaseUser.getUid(), email, fullName);

                                    // Ghi vào nút "users/{uid}"
                                    mDatabase.child("users").child(firebaseUser.getUid()).setValue(user)
                                            .addOnCompleteListener(dbTask -> {
                                                showLoading(false);
                                                if (dbTask.isSuccessful()) {
                                                    Toast.makeText(AuthActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                                                    startActivity(new android.content.Intent(AuthActivity.this, MainActivity.class));
                                                    finish();
                                                } else {
                                                    Toast.makeText(AuthActivity.this, "Đăng ký thành công nhưng lưu thông tin thất bại: " + dbTask.getException().getMessage(), Toast.LENGTH_LONG).show();
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

        // Nhấn văn bản chuyển đổi chế độ
        tvToggle.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUIForMode();
        });
    }

    /**
     * Cập nhật tiêu đề giao diện, văn bản nút, văn bản chuyển đổi và trường họ tên dựa trên chế độ hiện tại
     */
    private void updateUIForMode() {
        if (isLoginMode) {
            tvTitle.setText("Đăng nhập");
            btnAction.setText("Đăng nhập");
            tvToggle.setText("Chưa có tài khoản? Đăng ký");
            etFullName.setVisibility(View.GONE);
        } else {
            tvTitle.setText("Đăng ký");
            btnAction.setText("Đăng ký");
            tvToggle.setText("Đã có tài khoản? Đăng nhập");
            etFullName.setVisibility(View.VISIBLE);
        }
        // Xóa trường mật khẩu
        etPassword.setText("");
    }

    /**
     * Hiển thị hoặc ẩn thanh tiến trình, đồng thời vô hiệu hóa nút
     */
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